import argparse
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import backup


class BackupSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.dump = self.folder / "database.sql"
        self.dump.write_text("CREATE TABLE `empty_table` (id int);\n"
                             "CREATE TABLE `records` (id int, data text);\n"
                             "INSERT INTO `records` VALUES (1,'text, with (punctuation)');\n"
                             "INSERT INTO `records` VALUES (2,'escaped\\nnewline');\n", encoding="utf-8")
        self.metadata = {"format": 1, "state": "complete", "database": "yf_boot_exam",
                         "mysqlImage": "sha256:" + "a" * 64,
                         "files": {"database.sql": backup.sha(self.dump)},
                         "rows": {"empty_table": 0, "records": 2}}
        self.save()

    def save(self):
        (self.folder / "manifest.json").write_text(json.dumps(self.metadata), encoding="utf-8")

    def test_counts_include_empty_tables_and_escaped_row_content(self):
        self.assertEqual(self.metadata, backup.validate_backup(self.folder))

    def test_corruption_is_rejected_before_docker(self):
        self.dump.write_text("truncated", encoding="utf-8")
        with patch.object(backup, "run") as run:
            with self.assertRaisesRegex(ValueError, "checksum"):
                backup.verify(argparse.Namespace(backup=self.folder))
            run.assert_not_called()

    def test_incomplete_and_path_traversal_manifests_are_rejected(self):
        self.metadata["state"] = "incomplete"
        self.save()
        with self.assertRaisesRegex(ValueError, "incomplete"):
            backup.validate_backup(self.folder)
        self.metadata["state"] = "complete"
        self.metadata["files"]["../anything"] = "a" * 64
        self.save()
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            backup.validate_backup(self.folder)

    def test_row_manifest_mismatch_is_rejected(self):
        self.metadata["rows"]["records"] = 3
        self.save()
        with self.assertRaisesRegex(ValueError, "row manifest"):
            backup.validate_backup(self.folder)

    def test_identifiers_cannot_inject_sql(self):
        for value in ["bad;DROP DATABASE", "mysql--", "`unsafe`", ""]:
            with self.assertRaises(ValueError):
                backup.identifier(value)

    def test_failed_restore_cleans_only_the_new_isolated_container(self):
        with patch.object(backup, "run", return_value=b"") as run, \
                patch.object(backup, "mysql", side_effect=RuntimeError("restore failed")):
            with self.assertRaisesRegex(RuntimeError, "restore failed"):
                backup.verify(argparse.Namespace(backup=self.folder))
        create = run.call_args_list[0].args[0]
        container = create[create.index("--name") + 1]
        self.assertRegex(container, r"^exam-restore-check-[a-f0-9]{16}$")
        self.assertIn("--network=none", create)
        self.assertNotIn("-p", create)
        self.assertNotIn("-v", create)
        self.assertEqual(["docker", "rm", "--force", "--volumes", container], run.call_args_list[-1].args[0])
        report = json.loads((self.folder / "restore-verification.json").read_text())
        self.assertFalse(report["passed"])
        self.assertTrue(report["temporaryContainerRemoved"])


if __name__ == "__main__":
    unittest.main()
