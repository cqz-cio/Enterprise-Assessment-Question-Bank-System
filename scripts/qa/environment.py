"""Disposable local MySQL/Redis/app environment for end-to-end acceptance.

Only schema, migration history and configuration are copied. No real accounts,
questions, assignments or answers are used. See scripts/qa/README.md.
"""
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / '.local/qa-environment.json'
LOGS = ROOT / 'work/codex-logs'
MYSQL = 'yf-exam-mysql'
SOURCE = 'yf_boot_exam'
PORT = 18090
CONFIG_TABLES = ['flyway_schema_history', 'el_cfg_base', 'el_cfg_switch', 'el_sys_depart',
                 'el_sys_dic', 'el_sys_dic_value', 'el_sys_menu',
                 'el_sys_role', 'el_sys_role_menu']


def command(args, data=None, timeout=25):
    flags = ({'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt'
             else {'start_new_session': True})
    p = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, **flags)
    try:
        out, err = p.communicate(data, timeout=timeout)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            subprocess.run(['taskkill', '/PID', str(p.pid), '/T', '/F'],
                           capture_output=True, timeout=10, creationflags=subprocess.CREATE_NO_WINDOW)
        else:
            os.killpg(p.pid, signal.SIGKILL)
        if p.poll() is None:
            p.kill()
        p.communicate(timeout=5)
        raise RuntimeError(f'{args[0]} timed out after {timeout}s; process tree terminated')
    if p.returncode:
        raise RuntimeError(f'{args[0]} failed: ' + err.decode('utf-8', 'replace')[:700])
    return out



def mysql(query, database=SOURCE):
    if database != SOURCE and not re.fullmatch(r'qa_exam_[a-f0-9]{12}', database):
        raise ValueError('Invalid disposable database')
    return command(['docker', 'exec', '-i', MYSQL, 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
                    '--default-character-set=utf8mb4 -N -D ' + database],
                   query.encode('utf-8')).decode('utf-8').strip()


def load():
    state = json.loads(STATE.read_text(encoding='utf-8'))
    if not re.fullmatch(r'qa_exam_[a-f0-9]{12}', state['database']):
        raise ValueError('Refuse non-test database')
    if state['redis'] != state['database'].replace('_', '-'):
        raise ValueError('Refuse non-test container')
    return state


def save(state):
    STATE.parent.mkdir(exist_ok=True)
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')


def sql(query):
    return mysql(query, load()['database'])


def redis(*args):
    return command(['docker', 'exec', load()['redis'], 'redis-cli', '-n', '0', '--raw',
                    *map(str, args)]).decode('utf-8').strip()


def fingerprint():
    tables = mysql('SHOW TABLES').splitlines()
    # Read-only server checksums plus counts for every source table.
    digest = mysql('CHECKSUM TABLE ' + ','.join('`'+t+'`' for t in tables))
    counts = mysql(' UNION ALL '.join("SELECT '"+t+"',COUNT(*) FROM `"+t+'`' for t in tables))
    return {'sha256': hashlib.sha256((digest+'\n'+counts).encode()).hexdigest(),
            'tables': len(tables), 'counts': counts}


def prepare():
    if STATE.exists():
        raise RuntimeError('Existing QA environment: run cleanup before creating another')
    with socket.socket() as probe:
        probe.settimeout(2)
        if probe.connect_ex(('127.0.0.1', PORT)) == 0:
            raise RuntimeError(f'Port {PORT} is in use; leave the existing service untouched')
    name = 'qa_exam_' + uuid.uuid4().hex[:12]
    state = {'database': name, 'redis': name.replace('_', '-'), 'port': PORT,
             'baseline': fingerprint(), 'created': time.strftime('%Y-%m-%d %H:%M:%S')}
    save(state)
    mysql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci')
    schema = command(['docker', 'exec', MYSQL, 'sh', '-c',
                      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
                      '--no-data --set-gtid-purged=OFF --no-tablespaces ' + SOURCE])
    existing = set(mysql('SHOW TABLES').splitlines())
    config = [t for t in CONFIG_TABLES if t in existing]
    data = command(['docker', 'exec', MYSQL, 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
                    '--no-create-info --skip-triggers --set-gtid-purged=OFF '
                    '--no-tablespaces ' + SOURCE + ' ' + ' '.join(config)])
    command(['docker', 'exec', '-i', MYSQL, 'sh', '-c',
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
             '--default-character-set=utf8mb4 ' + name], schema+b'\n'+data)
    image = command(['docker', 'inspect', 'yf-exam-redis', '--format', '{{.Config.Image}}']).decode().strip()
    command(['docker', 'run', '--pull=never', '-d', '--rm', '--name', state['redis'],
             '--label', 'enterprise-exam-qa=true', '-p', '127.0.0.1::6379', image,
             'redis-server', '--save', '', '--appendonly', 'no'])
    state['redisPort'] = int(command(['docker', 'port', state['redis'], '6379']).decode().strip().split(':')[-1])
    save(state)
    print(f'Prepared {name}: {len(existing)} table schemas, {len(config)} config tables; no business records copied.', flush=True)


def start():
    state = load()
    if state.get('pid'):
        raise RuntimeError('QA backend already recorded; stop it before restart')
    jar = ROOT / 'yf-bev2-api/target/release-build/yf-bev2-api.jar'
    if not jar.exists():
        raise RuntimeError('Build the release profile first')
    runtime_jar = ROOT / '.local' / (state['database']+'.jar')
    shutil.copy2(jar, runtime_jar)
    jar = runtime_jar
    env = os.environ.copy()
    # Persist only random QA keys in ignored state so process-restart checks use the same identity.
    state.setdefault('testSecrets', {'JWT_SECRET': secrets.token_urlsafe(48),
                                     'ASSIGNMENT_CODE_PEPPER': secrets.token_urlsafe(48)})
    env.update(state['testSecrets'])
    env['SPRING_DATASOURCE_PASSWORD'] = command(['docker', 'exec', MYSQL, 'printenv', 'MYSQL_ROOT_PASSWORD']).decode().strip()
    LOGS.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime('%Y%m%d-%H%M%S')
    log = LOGS / (stamp+'-qa-backend.log')
    error = LOGS / (stamp+'-qa-backend.stderr.log')
    args = [shutil.which('java'), '-Xmx768m', '-jar', str(jar), '--server.address=127.0.0.1',
            '--server.port='+str(PORT), '--spring.profiles.active=dev',
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/'+state['database']+
            '?useSSL=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true',
            '--spring.datasource.username=root', '--spring.data.redis.database=0',
            '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port='+str(state['redisPort']),
            '--logging.level.root=INFO', '--logging.level.com.yf=INFO']
    with log.open('wb') as out, error.open('wb') as err:
        p = subprocess.Popen(args, cwd=ROOT/'yf-bev2-api', env=env,
                             stdout=out, stderr=err, stdin=subprocess.DEVNULL,
                             creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    env.clear()
    state.update(pid=p.pid, log=str(log), stderr=str(error), jar=str(jar))
    save(state)
    print(f'QA backend PID={p.pid}; log={log}', flush=True)
    begun = time.monotonic()
    while time.monotonic()-begun < 90 and p.poll() is None:
        try:
            c = http.client.HTTPConnection('127.0.0.1', PORT, timeout=2)
            c.request('POST', '/api/sys/config/detail', '{}', {'Content-Type': 'application/json'})
            reply = json.loads(c.getresponse().read()); c.close()
            if reply.get('code') == 0:
                print('QA backend ready: http://127.0.0.1:'+str(PORT), flush=True)
                return
        except (OSError, ValueError, http.client.HTTPException):
            pass
        time.sleep(.5)
    stop()
    raise RuntimeError('QA backend failed to become ready in 90s; inspect recorded logs')


def stop():
    state = load()
    if state.get('pid'):
        if os.name != 'nt':
            raise RuntimeError('This local lifecycle currently supports Windows only')
        pid = int(state['pid'])
        # Verify exact command line before stopping the recorded PID (PID reuse safety).
        check = command(['powershell.exe', '-NoProfile', '-Command',
                         f"Get-CimInstance Win32_Process -Filter 'ProcessId = {pid}' -OperationTimeoutSec 5 | Select-Object CommandLine | ConvertTo-Json -Compress"])
        if check.strip():
            cmdline = json.loads(check)['CommandLine'] or ''
            if state['database'] not in cmdline or state['jar'] not in cmdline:
                raise RuntimeError('PID belongs to a different process; refusing to stop')
            command(['taskkill', '/PID', str(pid), '/T', '/F'], timeout=15)
        state.pop('pid'); save(state)


def cleanup():
    state = load()
    stop()
    command(['docker', 'rm', '-f', state['redis']])
    mysql('DROP DATABASE IF EXISTS `'+state['database']+'`')
    after = fingerprint()
    if after != state['baseline']:
        raise RuntimeError('Source checksum/counts changed; preserve state for investigation')
    summary = {'sourceUnchanged': True, 'sourceTables': after['tables'],
               'databaseRemoved': state['database'], 'backendStopped': True, 'redisRemoved': True}
    (LOGS/(time.strftime('%Y%m%d-%H%M%S')+'-qa-cleanup.json')).write_text(json.dumps(summary, indent=2))
    runtime_jar = ROOT / '.local' / (state['database']+'.jar')
    runtime_jar.unlink(missing_ok=True)
    (ROOT/'.local/qa-fixtures.json').unlink(missing_ok=True)
    STATE.unlink()
    print(json.dumps(summary), flush=True)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    actions = {'prepare': prepare, 'start': start, 'stop': stop, 'cleanup': cleanup}
    if len(sys.argv) != 2 or sys.argv[1] not in actions:
        raise SystemExit('Usage: environment.py prepare|start|stop|cleanup')
    actions[sys.argv[1]]()
