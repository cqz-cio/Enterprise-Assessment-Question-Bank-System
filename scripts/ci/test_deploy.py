"""Deployment failure-path tests. No real SSH, systemd, database or server writes."""
import importlib.util
import hashlib
import io
import json
import os
import shlex
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('deploy_test', ROOT / 'deploy/linux/deploy_test.py')
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)
client_spec = importlib.util.spec_from_file_location('deploy_client', ROOT / 'scripts/ci/deploy.py')
client = importlib.util.module_from_spec(client_spec)
client_spec.loader.exec_module(client)


def make_jar(path, migration='original', extra=False):
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('BOOT-INF/classes/static/index.html', '<html>test</html>')
        archive.writestr('BOOT-INF/classes/application-prod.yml', 'fixture')
        archive.writestr('BOOT-INF/classes/db/migration/V001__test.sql', migration)
        if extra:
            archive.writestr('BOOT-INF/classes/db/migration/V002__test.sql', 'new')


class MigrationGuardTest(unittest.TestCase):
    def test_windows_and_linux_line_endings_are_equivalent(self):
        with tempfile.TemporaryDirectory() as folder:
            old, new = Path(folder) / 'old.jar', Path(folder) / 'new.jar'
            make_jar(old, migration='SELECT 1;\r\nSELECT 2;\r\n')
            make_jar(new, migration='SELECT 1;\nSELECT 2;\n')
            self.assertTrue(deployment.validate_upgrade(old, new))

    def test_progress_survives_closed_ssh_output(self):
        with patch('builtins.print', side_effect=BrokenPipeError), patch.object(deployment.os, 'dup2') as redirect:
            deployment.progress('fixture')
        redirect.assert_called_once()

    def test_same_new_changed_and_removed_migrations(self):
        with tempfile.TemporaryDirectory() as folder:
            old, new = Path(folder) / 'old.jar', Path(folder) / 'new.jar'
            make_jar(old)
            make_jar(new)
            self.assertTrue(deployment.validate_upgrade(old, new))
            make_jar(new, extra=True)
            self.assertFalse(deployment.validate_upgrade(old, new))
            with self.assertRaisesRegex(ValueError, 'removes or edits'):
                deployment.validate_upgrade(new, old)
            make_jar(new, migration='changed')
            with self.assertRaisesRegex(ValueError, 'removes or edits'):
                deployment.validate_upgrade(old, new)

    def test_bad_release_identity_rejected_before_filesystem_access(self):
        for release, commit, digest in [('../bad', 'a'*40, 'b'*64),
                                         ('1-1-'+'a'*12, 'b'*40, 'c'*64),
                                         ('1-1-'+'a'*12, 'a'*40, 'not-a-hash')]:
            with self.assertRaises(ValueError):
                deployment.stage(release, commit, digest)


class DeploymentTransactionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        releases = self.base / 'releases'
        releases.mkdir()
        self.old, self.new = releases / 'old.jar', releases / 'new.jar'
        make_jar(self.old)
        make_jar(self.new)
        try:
            (self.base / 'current.jar').symlink_to(self.old)
        except OSError:
            self.skipTest('Symlink privilege required; Linux CI runs these tests')
        for name, value in [('BASE', self.base)]:
            patcher = patch.object(deployment, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.run = self.start_patch('run')
        self.backup = self.start_patch('backup', return_value='/private/backup')
        self.healthy = self.start_patch('healthy')

    def start_patch(self, name, **kwargs):
        patcher = patch.object(deployment, name, **kwargs)
        mock = patcher.start()
        self.addCleanup(patcher.stop)
        return mock

    def deploy(self):
        deployment.deploy(self.new, 'a'*40, '1-1-'+'a'*12)

    def test_success_preserves_old_jar_and_records_backup(self):
        self.deploy()
        self.assertEqual(self.new, (self.base / 'current.jar').resolve())
        self.assertTrue(self.old.exists())
        self.assertFalse((self.base / '.cd-needs-recovery').exists())
        self.assertEqual('/private/backup', json.loads((self.base / 'cd-current.json').read_text())['backup'])
        self.healthy.assert_called_once_with(self.new)

    def test_backup_failure_restarts_old_without_switch(self):
        self.backup.side_effect = RuntimeError('backup failed')
        with self.assertRaisesRegex(RuntimeError, 'backup failed'):
            self.deploy()
        self.assertEqual(self.old, (self.base / 'current.jar').resolve())
        self.healthy.assert_called_once_with(self.old)
        self.assertFalse((self.base / '.cd-needs-recovery').exists())

    def test_failed_same_schema_release_restores_old(self):
        self.healthy.side_effect = [RuntimeError('unhealthy'), None]
        with self.assertRaisesRegex(RuntimeError, 'unhealthy'):
            self.deploy()
        self.assertEqual(self.old, (self.base / 'current.jar').resolve())
        self.assertFalse((self.base / '.cd-needs-recovery').exists())

    def test_failed_new_migration_stops_and_blocks_next_deploy(self):
        make_jar(self.new, extra=True)
        self.healthy.side_effect = RuntimeError('unhealthy')
        with self.assertRaisesRegex(RuntimeError, 'unhealthy'):
            self.deploy()
        self.assertEqual(self.new, (self.base / 'current.jar').resolve())
        self.assertEqual(['systemctl', 'stop', deployment.SERVICE], self.run.call_args.args[0])
        with self.assertRaisesRegex(RuntimeError, 'requires recovery'):
            self.deploy()
        self.backup.assert_called_once()

    def test_recovery_failure_keeps_interruption_marker(self):
        self.healthy.side_effect = RuntimeError('still unhealthy')
        with self.assertRaisesRegex(RuntimeError, 'still unhealthy'):
            self.deploy()
        self.assertTrue((self.base / '.cd-needs-recovery').exists())

    def test_changed_migration_does_not_stop_service(self):
        make_jar(self.new, migration='tampered')
        with self.assertRaisesRegex(ValueError, 'removes or edits'):
            self.deploy()
        self.run.assert_not_called()
        self.backup.assert_not_called()

    @unittest.skipUnless(os.name == 'posix', 'Linux upload ownership')
    def test_prepare_seeds_only_jar_and_rejects_reuse(self):
        import pwd
        incoming = self.base / 'incoming'
        incoming.mkdir()
        (self.base / 'secrets').mkdir()
        (self.base / 'secrets/app.env').write_text('private-fixture')
        release_id = '123-1-' + 'a'*12
        with patch.object(deployment, 'INCOMING', incoming), patch.object(deployment.os, 'chown'), \
                patch.object(pwd, 'getpwnam', return_value=pwd.getpwuid(os.getuid())):
            deployment.prepare(release_id)
            copied = incoming / release_id
            self.assertEqual(['yf-bev2-api.jar'], [p.name for p in copied.iterdir()])
            self.assertEqual(self.old.read_bytes(), (copied / 'yf-bev2-api.jar').read_bytes())
            with self.assertRaisesRegex(ValueError, 'already exists'):
                deployment.prepare(release_id)
        self.assertEqual(self.old, (self.base / 'current.jar').resolve())
        self.run.assert_not_called()

    def test_prepare_rejects_path_traversal_before_copy(self):
        with self.assertRaisesRegex(ValueError, 'Invalid release ID'):
            deployment.prepare('../unsafe')


class BackupVerificationTest(unittest.TestCase):
    def test_incomplete_or_corrupt_backup_is_rejected(self):
        for incomplete in (True, False):
            with self.subTest(incomplete=incomplete), tempfile.TemporaryDirectory() as folder:
                base = Path(folder)
                backups = base / 'backups/ops'
                backups.mkdir(parents=True)

                def fake_backup(*args):
                    destination = backups / 'new'
                    destination.mkdir()
                    (destination / 'database.sql').write_text('fixture')
                    report = {'state': 'incomplete' if incomplete else 'complete',
                              'files': {'database.sql': '0'*64}}
                    (destination / 'manifest.json').write_text(json.dumps(report))

                with patch.object(deployment, 'BASE', base), patch.object(deployment, 'run', side_effect=fake_backup):
                    with self.assertRaises(ValueError):
                        deployment.backup()

    @unittest.skipUnless(os.name == 'posix', 'Linux no-follow file descriptors')
    def test_symlink_upload_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            (base / 'real').write_text('fixture')
            (base / 'link').symlink_to(base / 'real')
            with self.assertRaises(OSError):
                deployment.copy_upload(base / 'link', base / 'out')
            self.assertFalse((base / 'out').exists())


class ClientSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.folder = self.root / 'work/ci-release'
        self.folder.mkdir(parents=True)
        self.jar = self.folder / 'yf-bev2-api.jar'
        self.jar.write_bytes(b'jar-fixture')
        self.metadata = {'gitCommit': 'a'*40, 'dirtyWorkingTree': False,
                         'jarSha256': hashlib.sha256(self.jar.read_bytes()).hexdigest()}
        self.save_metadata()
        environment = {'GITHUB_SHA': 'a'*40, 'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1',
                       'GITHUB_REF': 'refs/heads/main', 'GITHUB_REPOSITORY': 'example/example',
                       'GH_TOKEN': 'test-token', 'TEST_SSH_HOST': 'example.invalid', 'TEST_SSH_PORT': '22',
                       'TEST_SSH_PRIVATE_KEY': 'fixture-private-key', 'TEST_SSH_KNOWN_HOSTS': 'fixture-host-key'}
        for patcher in (patch.dict(os.environ, environment, clear=True), patch.object(client, 'ROOT', self.root)):
            patcher.start()
            self.addCleanup(patcher.stop)
        remote = patch.object(client, 'execute', return_value=b'deployed')
        self.execute = remote.start()
        self.addCleanup(remote.stop)
        api = patch.object(client.urllib.request, 'urlopen',
                           return_value=io.BytesIO(json.dumps({'object': {'sha': 'a'*40}}).encode()))
        self.api = api.start()
        self.addCleanup(api.stop)

    def save_metadata(self):
        (self.folder / 'release.json').write_text(json.dumps(self.metadata))

    def test_success_uses_strict_host_check_and_removes_private_key(self):
        client.main()
        commands = [call.args[0] for call in self.execute.call_args_list]
        self.assertEqual(3, len(commands))
        for command in commands:
            connection = shlex.split(command[command.index('-e') + 1]) if command[0] == 'rsync' else command
            self.assertIn('StrictHostKeyChecking=yes', connection)
            self.assertIn('BatchMode=yes', connection)
            self.assertNotIn('fixture-private-key', ' '.join(command))
            self.assertFalse(Path(connection[connection.index('-i') + 1]).exists())
        self.assertIn(' prepare ', commands[0][-1])
        self.assertIn('--info=progress2', commands[1])
        self.assertIn('--timeout=60', commands[1])
        self.assertIn('--bwlimit=16', commands[1])
        self.assertIn('--rsync-path=timeout --kill-after=5s 290s rsync', commands[1])
        self.assertTrue(commands[-1][-1].startswith('sudo -n /usr/local/sbin/enterprise-exam-test-deploy '))

    def test_upload_timeout_never_activates_release_and_removes_key(self):
        self.execute.side_effect = [b'prepared', RuntimeError('upload timed out')]
        with self.assertRaisesRegex(RuntimeError, 'upload timed out'):
            client.main()
        commands = [call.args[0] for call in self.execute.call_args_list]
        self.assertEqual(2, len(commands))
        self.assertIn(' prepare ', commands[0][-1])
        self.assertEqual('rsync', commands[1][0])
        connection = shlex.split(commands[1][commands[1].index('-e') + 1])
        self.assertFalse(Path(connection[connection.index('-i') + 1]).exists())

    def test_corrupt_artifact_is_not_uploaded(self):
        self.jar.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'Artifact identity'):
            client.main()
        self.execute.assert_not_called()

    def test_dirty_build_is_not_uploaded(self):
        self.metadata['dirtyWorkingTree'] = True
        self.save_metadata()
        with self.assertRaisesRegex(ValueError, 'dirty build'):
            client.main()
        self.execute.assert_not_called()

    def test_non_main_ref_is_rejected_without_network(self):
        os.environ['GITHUB_REF'] = 'refs/heads/feature'
        with self.assertRaisesRegex(ValueError, 'Only main'):
            client.main()
        self.api.assert_not_called()
        self.execute.assert_not_called()

    def test_stale_run_is_rejected(self):
        self.api.return_value = io.BytesIO(json.dumps({'object': {'sha': 'b'*40}}).encode())
        with self.assertRaisesRegex(ValueError, 'no longer main HEAD'):
            client.main()
        self.execute.assert_not_called()

    def test_ssh_host_injection_is_rejected(self):
        os.environ['TEST_SSH_HOST'] = 'host;echo unsafe'
        with self.assertRaisesRegex(ValueError, 'Invalid SSH'):
            client.main()
        self.execute.assert_not_called()


if __name__ == '__main__':
    unittest.main()
