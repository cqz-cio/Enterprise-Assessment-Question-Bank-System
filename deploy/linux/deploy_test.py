#!/usr/bin/python3 -I
"""Root-owned deployment helper for the existing test instance only (Python 3.10+)."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import subprocess
import sys
import time
import urllib.request
import uuid
import zipfile

BASE = Path('/opt/enterprise-exam-test')
INCOMING = Path('/var/lib/enterprise-exam-deploy/incoming')
SERVICE = 'enterprise-exam-test'
HTTP = 'http://127.0.0.1:18082'


def progress(message):
    # Continue the transaction after SSH disconnects, even when its stdout pipe closes.
    try:
        print(message, flush=True)
    except BrokenPipeError:
        with open(os.devnull, 'w') as sink:
            os.dup2(sink.fileno(), sys.stdout.fileno())


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path, data):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def run(command, seconds=60):
    """Private logs, periodic progress and a bounded process group for each command."""
    logs = BASE / 'cd-logs'
    logs.mkdir(mode=0o700, exist_ok=True)
    log = logs / (time.strftime('%Y%m%d-%H%M%S-') + uuid.uuid4().hex[:8] + '.log')
    started = time.monotonic()
    with log.open('wb') as output:
        output.write(f'command={command!r}\ncwd={BASE}\n'.encode())
        output.flush()
        process = subprocess.Popen(command, cwd=BASE, stdin=subprocess.DEVNULL,
                                   stdout=output, stderr=output, start_new_session=True)
        last_progress, heartbeat, size = started, started, log.stat().st_size
        try:
            while process.poll() is None:
                time.sleep(.25)
                now, current_size = time.monotonic(), log.stat().st_size
                if current_size != size:
                    last_progress, size = now, current_size
                if now - heartbeat >= 15:
                    progress(f'Operation in progress ({int(now-started)}s); private log: {log}')
                    heartbeat = now
                if now - started > seconds or now - last_progress > 60:
                    output.write(b'\nTIMEOUT: terminating command process group\n')
                    output.flush()
                    raise TimeoutError(f'Command timed out; private log: {log}')
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
            output.write(f'\nexit={process.returncode}; elapsed={time.monotonic()-started:.1f}s\n'.encode())
        if process.returncode:
            raise RuntimeError(f'Command failed ({process.returncode}); private log: {log}')


def migrations(jar):
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        required = {'BOOT-INF/classes/static/index.html', 'BOOT-INF/classes/application-prod.yml'}
        if not required.issubset(names) or len(names) != len(set(names)):
            raise ValueError('Invalid release JAR or missing production frontend/profile')
        # Windows-built releases can contain CRLF while Linux checkout uses LF.
        # Normalize only line endings; all actual SQL content remains immutable.
        result = {name: hashlib.sha256(archive.read(name).replace(b'\r\n', b'\n')).hexdigest() for name in names
                  if name.startswith('BOOT-INF/classes/db/migration/') and not name.endswith('/')}
    if not result:
        raise ValueError('Release has no Flyway migrations')
    return result


def validate_upgrade(old, new):
    old_migrations, new_migrations = migrations(old), migrations(new)
    if any(new_migrations.get(name) != digest for name, digest in old_migrations.items()):
        raise ValueError('Release removes or edits an existing migration; deployment refused')
    return old_migrations == new_migrations


def copy_upload(source, target, directory_fd=None):
    # Open once without following links; copy into a root-owned snapshot before validation.
    descriptor = os.open(source, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory_fd)
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > 512 * 1024 * 1024:
            raise ValueError('Expected a regular upload no larger than 512 MiB')
        with target.open('xb') as output:
            shutil.copyfileobj(stream, output, length=1024 * 1024)


def stage(release_id, commit, digest):
    if not re.fullmatch(r'[0-9]+-[0-9]+-[a-f0-9]{12}', release_id):
        raise ValueError('Invalid release ID')
    if not re.fullmatch(r'[a-f0-9]{40}', commit) or not release_id.endswith('-' + commit[:12]):
        raise ValueError('Invalid commit')
    if not re.fullmatch(r'[a-f0-9]{64}', digest):
        raise ValueError('Invalid SHA256')
    source = INCOMING / release_id
    if source.is_symlink() or source.resolve(strict=True) != source:
        raise ValueError('Upload directory must not contain links')
    target = BASE / 'releases' / release_id
    target.mkdir(mode=0o750)
    source_fd = os.open(source, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for name in ('yf-bev2-api.jar', 'release.json'):
            copy_upload(name, target / name, source_fd)
    finally:
        os.close(source_fd)
    metadata = json.loads((target / 'release.json').read_text(encoding='utf-8'))
    jar = target / 'yf-bev2-api.jar'
    if (metadata.get('gitCommit') != commit or metadata.get('dirtyWorkingTree') is not False
            or metadata.get('jarSha256') != digest or sha256(jar) != digest):
        raise ValueError('Release provenance/checksum mismatch')
    # Deployment user cannot modify staged releases or read runtime secrets.
    import grp
    group = grp.getgrnam('exam-test').gr_gid
    os.chown(target, 0, group)
    target.chmod(0o750)
    os.chown(jar, 0, group)
    jar.chmod(0o640)
    return jar


def switch(jar):
    link = BASE / ('current.jar.' + uuid.uuid4().hex + '.tmp')
    link.symlink_to(jar)
    link.replace(BASE / 'current.jar')


def healthy(jar, seconds=120):
    with zipfile.ZipFile(jar) as archive:
        expected = hashlib.sha256(archive.read('BOOT-INF/classes/static/index.html')).digest()
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(HTTP + '/', timeout=5) as response:
                if response.status != 200 or hashlib.sha256(response.read()).digest() != expected:
                    raise ValueError('Frontend does not match the deployed artifact')
            with urllib.request.urlopen(HTTP + '/api/common/captcha/gen?key=' + str(uuid.uuid4()), timeout=5) as response:
                if response.status != 200 or response.read(8) != b'\x89PNG\r\n\x1a\n':
                    raise ValueError('Captcha/Redis readiness failed')
            run(['systemctl', 'is-active', '--quiet', SERVICE], 10)
            return
        except Exception:
            progress('Waiting for application, frontend and Redis readiness...')
            time.sleep(5)
    raise RuntimeError('Application readiness timed out')


def backup():
    folder = BASE / 'backups/ops'
    before = set(folder.iterdir()) if folder.exists() else set()
    run(['/usr/bin/python3', '-I', str(BASE / 'backup.py')], 180)
    created = set(folder.iterdir()) - before
    if len(created) != 1:
        raise ValueError('Expected one new backup directory')
    destination = created.pop()
    for name in ('manifest.json', 'runtime-manifest.json'):
        report = json.loads((destination / name).read_text(encoding='utf-8'))
        if report.get('state') != 'complete' or not report.get('files'):
            raise ValueError('Backup is incomplete')
        for relative, digest in report['files'].items():
            path = destination / relative
            if (path.is_symlink() or not path.resolve().is_relative_to(destination.resolve())
                    or sha256(path) != digest):
                raise ValueError('Backup checksum/path mismatch')
    return str(destination)


def deploy(jar, commit, release_id):
    current = BASE / 'current.jar'
    old = current.resolve(strict=True)
    if not current.is_symlink() or not old.is_relative_to((BASE / 'releases').resolve()):
        raise ValueError('current.jar must point to an existing release')
    same_migrations = validate_upgrade(old, jar)
    pending = BASE / '.cd-needs-recovery'
    if pending.exists():
        raise RuntimeError('Previous deployment requires recovery; inspect .cd-needs-recovery first')
    run(['systemctl', 'is-active', '--quiet', SERVICE], 10)
    report = {'release': release_id, 'gitCommit': commit, 'jarSha256': sha256(jar),
              'previousJar': str(old), 'jar': str(jar), 'sameMigrations': same_migrations,
              'state': 'preparing', 'backup': None}
    write_json(pending, report)
    switched = False
    try:
        progress('Stopping test application for a consistent database/uploads backup...')
        run(['systemctl', 'stop', SERVICE], 60)
        report['backup'] = backup()
        write_json(pending, report)
        progress('Backup verified; switching to tested release...')
        switch(jar)
        switched = True
        run(['systemctl', 'reset-failed', SERVICE], 15)
        run(['systemctl', 'start', SERVICE], 30)
        healthy(jar)
        report['state'] = 'deployed'
        write_json(BASE / 'cd-current.json', report)
    except Exception:
        if not switched or same_migrations:
            progress('Deployment failed; restoring previous application release...')
            run(['systemctl', 'stop', SERVICE], 60)
            if switched:
                switch(old)
            run(['systemctl', 'reset-failed', SERVICE], 15)
            run(['systemctl', 'start', SERVICE], 30)
            healthy(old)
            report['state'] = 'failed-old-release-restored'
            write_json(jar.parent / 'deployment-result.json', report)
            pending.unlink()
        else:
            run(['systemctl', 'stop', SERVICE], 60)
            report['state'] = 'failed-migration-review-required'
            write_json(pending, report)
            progress('New migrations may have run. Application stopped; review backup before recovery.')
        raise
    write_json(jar.parent / 'deployment-result.json', report)
    pending.unlink()
    progress(f'Deployed {commit}; backup retained at {report["backup"]}')


def main():
    if os.geteuid() != 0 or not (BASE / '.exam-test-owned').is_file():
        raise RuntimeError('Run as root on the designated test deployment only')
    if len(sys.argv) != 4:
        raise ValueError('Usage: enterprise-exam-test-deploy RELEASE_ID COMMIT SHA256')
    os.umask(0o077)
    # SSH disconnects must not interrupt the critical switch/recovery section.
    signal.signal(signal.SIGHUP, signal.SIG_IGN)
    import fcntl
    with (BASE / '.cd-deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if (BASE / '.cd-needs-recovery').exists():
            raise RuntimeError('Unfinished deployment: inspect .cd-needs-recovery before retrying')
        if shutil.disk_usage(BASE).free < 1024 * 1024 * 1024:
            raise RuntimeError('Less than 1 GiB free; make space before deploying')
        jar = stage(*sys.argv[1:])
        deploy(jar, sys.argv[2], sys.argv[1])


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Deployment failed: {error}', file=sys.stderr, flush=True)
        sys.exit(1)
