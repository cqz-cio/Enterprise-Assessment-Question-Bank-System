"""Exercise launch selection with an isolated secret/process stub; never start Java."""
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest

from ops_common import ROOT, run


@unittest.skipUnless(os.name == "nt", "Windows launcher")
class LauncherSelectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / ".local").mkdir()
        (self.root / "scripts/ops").mkdir(parents=True)
        (self.root / "yf-bev2-api/target").mkdir(parents=True)
        shutil.copy2(ROOT / "start-backend.ps1", self.root)
        self.default = self.root / "yf-bev2-api/target/yf-bev2-api.jar"
        self.production = self.root / "yf-bev2-api/target/release.jar"
        self.default.touch()
        self.production.touch()
        self.config = self.root / ".local/custom.properties"
        self.config.write_text("EXAM_DB_USER=fixture\n", encoding="utf-8")
        self.record = {"profile": "prod", "jar": str(self.production), "configFile": str(self.config)}
        (self.root / "scripts/ops/Backend.Common.ps1").write_text('''
$ErrorActionPreference = 'Stop'
$script:BackendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:BackendLocal = Join-Path $script:BackendRoot '.local'
$script:BackendRecord = Join-Path $script:BackendLocal 'backend-process.json'
function Read-BackendSecrets {
    if ($Profile -ne $env:EXPECTED_PROFILE -or $jar -ne $env:EXPECTED_JAR) { throw 'Wrong launch selection' }
    return @{}
}
function Get-ProjectBackend { return @{ProcessId=123} }
function Stop-ProjectBackend { throw 'Test must never stop a backend' }
''', encoding="utf-8")

    def invoke(self, profile="prod", parameters=None):
        env = os.environ.copy()
        env.update(EXPECTED_PROFILE=profile, EXPECTED_JAR=str(self.production if profile == "prod" else self.default))
        return run(["powershell.exe", "-NoProfile", "-NonInteractive", "-File",
                    str(self.root / "start-backend.ps1"), *(parameters or [])], env=env, timeout=15)

    def save(self, filename="backend-launch.json"):
        (self.root / ".local" / filename).write_text(json.dumps(self.record), encoding="utf-8")

    def test_remembers_production_without_process_record(self):
        self.save()
        self.assertIn(b"already running", self.invoke())

    def test_legacy_process_record_is_respected(self):
        self.save("backend-process.json")
        self.assertIn(b"already running", self.invoke())

    def test_explicit_dev_overrides_saved_production(self):
        self.save()
        self.assertIn(b"already running", self.invoke("dev", ["-Profile", "dev"]))

    def test_missing_production_configuration_refuses_start(self):
        self.save()
        self.config.unlink()
        with self.assertRaisesRegex(RuntimeError, "Command failed"):
            self.invoke()


if __name__ == "__main__":
    unittest.main()
