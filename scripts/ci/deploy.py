"""GitHub runner SSH client. Only the already-tested JAR is uploaded."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import sys
import tempfile
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from ops_common import LOGS, ROOT, run


def execute(command, seconds):
    before = set(LOGS.glob('*'))
    try:
        return run(command, timeout=seconds, record_output=True)
    except Exception:
        # These commands contain key FILE PATHS only. Never print env/key contents.
        # Remote helper output is limited to phases and private server log paths.
        for path in sorted(set(LOGS.glob('*')) - before):
            if path.suffix in ('.log', '.output'):
                print(path.read_text(encoding='utf-8', errors='replace')[-12000:], file=sys.stderr)
        raise


def stop_upload(ssh, remote):
    # Only this account's rsync processes for this run's private staging directory.
    # The application JAR lives elsewhere and is never an upload destination.
    cleanup = """
import os, signal, sys, time
from pathlib import Path
target = sys.argv[1].encode() + b'/'
def matches(pid):
    p = Path('/proc') / str(pid)
    try:
        if p.stat().st_uid != os.getuid(): return False
        args = (p / 'cmdline').read_bytes().split(bytes([0]))
        return (p / 'comm').read_text().strip() == 'rsync' and b'--server' in args and target in args
    except (FileNotFoundError, ProcessLookupError):
        return False
pids = [int(p.name) for p in Path('/proc').iterdir() if p.name.isdigit() and matches(int(p.name))]
for sig in (signal.SIGTERM, signal.SIGKILL):
    for pid in pids:
        if matches(pid):
            try: os.kill(pid, sig)
            except ProcessLookupError: pass
    time.sleep(0.5)
if any(matches(pid) for pid in pids):
    raise SystemExit('Previous upload has not stopped; refusing concurrent retry')
print('Previous upload stopped; private partial JAR retained.')
"""
    execute([*ssh, 'timeout --kill-after=2s 5s python3 -c ' + shlex.quote(cleanup) + ' ' + shlex.quote(remote)], 20)


def upload(command, ssh, remote):
    try:
        execute(command, 125)
    except RuntimeError:
        stop_upload(ssh, remote)
        print('Resuming remaining changed blocks once after closing the stalled transfer.', flush=True)
        # The first attempt updated only the prepared private copy. Reusing those
        # blocks keeps reconnects short; the root helper still checks the full SHA256.
        execute(command, 125)


def main():
    sha = os.environ['GITHUB_SHA']
    run_id, attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
    if not re.fullmatch(r'[a-f0-9]{40}', sha) or not all(re.fullmatch(r'[0-9]+', x) for x in (run_id, attempt)):
        raise ValueError('Invalid GitHub release identity')
    if os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise ValueError('Only main can deploy')
    repository = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository')
    request = urllib.request.Request(
        f'https://api.github.com/repos/{repository}/git/ref/heads/main',
        headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        if json.load(response)['object']['sha'] != sha:
            raise ValueError('This run is no longer main HEAD; deploy the newer run instead')
    folder = ROOT / 'work/ci-release'
    metadata = json.loads((folder / 'release.json').read_text())
    jar = folder / 'yf-bev2-api.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if metadata.get('gitCommit') != sha or metadata.get('dirtyWorkingTree') is not False or metadata.get('jarSha256') != digest:
        raise ValueError('Artifact identity/checksum mismatch or dirty build')
    host, port = os.environ['TEST_SSH_HOST'], os.environ['TEST_SSH_PORT']
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]*', host) or not port.isdigit() or not 1 <= int(port) <= 65535:
        raise ValueError('Invalid SSH host/port')
    key, hosts = os.environ.get('TEST_SSH_PRIVATE_KEY', ''), os.environ.get('TEST_SSH_KNOWN_HOSTS', '')
    if not key.strip() or not hosts.strip():
        raise ValueError('Set TEST_SSH_PRIVATE_KEY and TEST_SSH_KNOWN_HOSTS secrets first')
    release_id = f'{run_id}-{attempt}-{sha[:12]}'
    destination = 'exam-deploy@' + host
    remote = '/var/lib/enterprise-exam-deploy/incoming/' + release_id
    # Temporary key material is outside the artifact tree and removed even on failure.
    with tempfile.TemporaryDirectory(prefix='exam-cd-') as temporary:
        key_file, hosts_file = Path(temporary) / 'key', Path(temporary) / 'known_hosts'
        key_file.write_text(key.strip() + '\n')
        key_file.chmod(0o600)
        hosts_file.write_text(hosts.strip() + '\n')
        options = ['-i', str(key_file), '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
                   '-o', 'StrictHostKeyChecking=yes', '-o', 'UserKnownHostsFile=' + str(hosts_file),
                   '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=3']
        ssh = ['ssh', *options, '-p', port, destination]
        execute([*ssh, f'sudo -n /usr/local/sbin/enterprise-exam-test-deploy prepare {release_id}'], 30)
        # The private seeded copy may be partially updated; it is never activated
        # until full checksum verification. A backup basis preserves blocks shifted
        # within the JAR; each remote transfer has its own deadline.
        command = ['rsync', '--inplace', '--backup', '--info=progress2', '--stats', '--outbuf=L', '--timeout=60',
                   '--bwlimit=32', '--rsync-path=timeout --kill-after=5s 110s rsync', '--chmod=F600,D700',
                   '-e', shlex.join(['ssh', *options, '-p', port]), str(jar), str(folder / 'release.json'),
                   destination + ':' + remote + '/']
        upload(command, ssh, remote)
        # 660 seconds covers backup (180), service operations, readiness and recovery.
        result = execute([*ssh, f'sudo -n /usr/local/sbin/enterprise-exam-test-deploy {release_id} {sha} {digest}'], 660)
        print(result.decode('utf-8', errors='replace'))
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as stream:
            stream.write(f'Deployed `{sha}` to [test site](https://124.220.2.69:18443).\n\nRelease: `{release_id}`\n')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Deployment failed: {error}', file=sys.stderr)
        sys.exit(1)
