"""Create a private Linux test backup: DB, uploads and independent runtime secrets.

Install beside compose.yaml and scripts/ops/{backup,ops_common}.py. Run as root.
Do not publish the resulting directory: runtime/ contains plaintext test secrets.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
from types import SimpleNamespace

BASE = Path('/opt/enterprise-exam-test')
sys.path.insert(0, str(BASE / 'scripts/ops'))
from backup import backup


def main():
    if os.geteuid() != 0 or not (BASE / '.exam-test-owned').is_file():
        raise RuntimeError('Run as root on the designated test deployment only')
    os.umask(0o077)
    destination = backup(SimpleNamespace(container='enterprise-exam-test-mysql-1',
        database='enterprise_exam_test', uploads_dir=str(BASE / 'uploads'),
        output=str(BASE / 'backups/ops')))
    metadata = destination / 'runtime-manifest.json'
    report = {'state': 'incomplete', 'files': {}, 'secrets': 'Independent Linux test keys; protect this entire backup'}
    try:
        names = ['secrets/app.env', 'secrets/mysql.env', 'secrets/redis.conf',
                 'compose.yaml', 'enterprise-exam-test.service', 'logback-spring.xml', 'deployment.json']
        for name in names:
            source = BASE / name
            if not source.is_file() or source.is_symlink():
                raise RuntimeError('Required regular configuration file missing: ' + name)
            target = destination / 'runtime' / name
            target.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
            shutil.copyfile(source, target)
            target.chmod(0o600)
            report['files']['runtime/' + name] = hashlib.sha256(target.read_bytes()).hexdigest()
        report['state'] = 'complete'
    finally:
        metadata.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Linux runtime configuration and independent keys included privately. No retention deletion performed.')


if __name__ == '__main__':
    main()
