from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

import ops_common


class CommandRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.logs = Path(self.temp.name)
        self.patch = patch.object(ops_common, "LOGS", self.logs)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def test_captured_stdout_is_not_left_in_log_files(self):
        # Avoid putting the fixture value in argv, which is intentionally logged.
        with tempfile.TemporaryFile() as source:
            source.write(b"private-output-fixture")
            source.seek(0)
            result = ops_common.run([sys.executable, "-c", "import sys; sys.stdout.buffer.write(sys.stdin.buffer.read())"],
                                    stdin=source, timeout=10)
        self.assertEqual(b"private-output-fixture", result)
        self.assertFalse(list(self.logs.glob("*.output")))
        for file in self.logs.iterdir():
            self.assertNotIn(b"private-output-fixture", file.read_bytes())

    def test_nonzero_exit_is_reported(self):
        with self.assertRaisesRegex(RuntimeError, "exit 7"):
            ops_common.run([sys.executable, "-c", "raise SystemExit(7)"], timeout=10)
        self.assertFalse(list(self.logs.glob("*.output")))

    def test_timeout_terminates_process_and_is_logged(self):
        with self.assertRaisesRegex(RuntimeError, "timed out"):
            ops_common.run([sys.executable, "-c", "import time; time.sleep(20)"], timeout=.3)
        self.assertTrue(any(b"TIMEOUT" in file.read_bytes() for file in self.logs.glob("*.log")))


if __name__ == "__main__":
    unittest.main()
