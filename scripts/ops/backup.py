"""Backup the Docker MySQL database and rehearse restores in a disposable container.

No command restores into an existing server. No automatic deletion of backup history.
Run --help for usage. The local Docker image must already be available.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import sys
import tempfile
import time
import uuid
import zipfile

from ops_common import ROOT, run


def sha(path):
    result = hashlib.sha256()
    deadline = time.monotonic() + 120
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            if time.monotonic() > deadline:
                raise TimeoutError("File checksum exceeded 120 seconds")
            result.update(chunk)
    return result.hexdigest()


def identifier(value):
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", value):
        raise ValueError("Invalid database/table identifier")
    return value


def mysql(container, database, query=None, source=None):
    # Password is expanded inside the container, never in argv or host logs.
    args = ["docker", "exec", "-i", container, "sh", "-c",
            'export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-$(cat "${MYSQL_ROOT_PASSWORD_FILE:-/dev/null}")}"; '
            'exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4 "$@"',
            "mysql", "-D", identifier(database)]
    if source:
        with source.open("rb") as stream:
            return run(args, stdin=stream)
    with tempfile.TemporaryFile() as stream:
        stream.write(query.encode("utf-8"))
        stream.seek(0)
        return run(args, stdin=stream, timeout=30).decode("utf-8").strip()


def dump_rows(path):
    """Counts from the SAME dump snapshot, not live counts collected before/after it."""
    tables, counts = set(), Counter()
    with path.open("rb") as stream:
        for line in stream:
            create = re.match(rb"CREATE TABLE `([A-Za-z0-9_]+)`", line)
            insert = re.match(rb"INSERT INTO `([A-Za-z0-9_]+)`", line)
            if create:
                tables.add(create[1].decode())
            if insert:
                counts[insert[1].decode()] += 1
    if not tables or set(counts) - tables:
        raise ValueError("Unsupported/incomplete dump; expected single-row INSERT statements")
    return {table: counts[table] for table in sorted(tables)}


def private_directory(path):
    path.mkdir(parents=True, exist_ok=False, mode=0o700)
    if os.name == "nt":
        identity = run(["whoami"]).decode().strip()
        run(["icacls", str(path), "/inheritance:r", "/grant:r",
             identity + ":(OI)(CI)F", "*S-1-5-18:(OI)(CI)F"], timeout=15)


def backup(args):
    database = identifier(args.database)
    image = run(["docker", "inspect", "--format", "{{.Image}}", args.container], timeout=15).decode().strip()
    # single-transaction cannot guarantee a snapshot of non-transactional tables.
    unsupported = mysql(args.container, database,
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
        "AND table_type='BASE TABLE' AND engine <> 'InnoDB'")
    if unsupported != "0":
        raise ValueError("Non-InnoDB tables found: use a maintenance-window backup procedure")
    local_config = mysql(args.container, database,
        "SELECT HEX(config_data) FROM pl_plugin_data WHERE code='upload-local' AND in_use=1 LIMIT 1")
    uploads = None
    if local_config:
        # mysql batch escapes backslashes: request hex for JSON to avoid ambiguous decoding.
        configured = json.loads(bytes.fromhex(local_config).decode())["localDir"]
        uploads = Path(args.uploads_dir or configured)
        if not uploads.is_absolute():
            uploads = ROOT / "yf-bev2-api" / uploads
        uploads = uploads.resolve()
        if not uploads.is_dir():
            raise ValueError("Configured upload directory is missing; supply --uploads-dir with the real upload directory")
    elif args.uploads_dir:
        uploads = Path(args.uploads_dir).resolve(strict=True)
    destination = Path(args.output).resolve() / (datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    private_directory(destination)
    dump = destination / "database.sql"
    manifest = {"format": 1, "createdAt": datetime.now(timezone.utc).isoformat(), "database": database,
                "mysqlImage": image, "state": "incomplete", "files": {}, "keyStoreIncluded": False,
                "consistency": "InnoDB snapshot; no concurrent DDL allowed. Upload files copied online separately.",
                "secretRecovery": "DPAPI copy only: same Windows user/computer; NOT a portable key escrow."}
    metadata = destination / "manifest.json"
    try:
        run(["docker", "exec", args.container, "sh", "-c",
             'export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-$(cat "${MYSQL_ROOT_PASSWORD_FILE:-/dev/null}")}"; '
             'exec mysqldump -uroot --single-transaction --quick --skip-lock-tables '
             '--routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF '
             '--skip-extended-insert --complete-insert --default-character-set=utf8mb4 "$@"',
             "mysqldump", database], stdout=dump, timeout=120)
        with dump.open("rb") as stream:
            stream.seek(max(0, dump.stat().st_size - 2048))
            if b"-- Dump completed on" not in stream.read():
                raise ValueError("Dump completion marker missing")
        manifest["rows"] = dump_rows(dump)
        manifest["files"][dump.name] = sha(dump)
        if uploads:
            archive = destination / "uploads.zip"
            if destination == uploads or uploads in destination.parents:
                raise ValueError("Backup destination cannot be inside upload directory")
            with zipfile.ZipFile(archive, "x", zipfile.ZIP_DEFLATED) as out:
                deadline = time.monotonic() + 120
                for current, directories, files in os.walk(uploads, followlinks=False):
                    for name in directories + files:
                        item = Path(current) / name
                        if item.is_symlink() or getattr(item, "is_junction", lambda: False)():
                            raise ValueError("Upload tree contains links; resolve them before backup")
                    for name in files:
                        if time.monotonic() > deadline:
                            raise TimeoutError("Upload backup exceeded 120 seconds")
                        item = Path(current) / name
                        before = item.stat()
                        with item.open("rb") as source, out.open(item.relative_to(uploads).as_posix(), "w", force_zip64=True) as target:
                            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                                if time.monotonic() > deadline:
                                    raise TimeoutError("Upload backup exceeded 120 seconds")
                                target.write(chunk)
                        after = item.stat()
                        if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                            raise ValueError("Upload changed while copying; repeat during maintenance")
            manifest["files"][archive.name] = sha(archive)
        keys = ROOT / ".local/backend-secrets.clixml"
        if keys.exists():
            saved = destination / keys.name
            shutil.copyfile(keys, saved)
            manifest["files"][saved.name] = sha(saved)
            manifest["keyStoreIncluded"] = True
        manifest["state"] = "complete"
    finally:
        metadata.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Backup complete: {destination}", flush=True)
    print("Run verify on this folder. Keep an encrypted off-machine copy and escrow the original keys separately.")
    return destination


def validate_backup(folder):
    metadata = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    if metadata.get("format") != 1 or metadata.get("state") != "complete":
        raise ValueError("Backup incomplete or format unsupported")
    allowed = {"database.sql", "uploads.zip", "backend-secrets.clixml"}
    if "database.sql" not in metadata["files"] or set(metadata["files"]) - allowed:
        raise ValueError("Unexpected backup file manifest")
    for name, digest in metadata["files"].items():
        file = folder / name
        if file.is_symlink() or sha(file) != digest:
            raise ValueError("Backup checksum mismatch: " + name)
    if metadata["rows"] != dump_rows(folder / "database.sql"):
        raise ValueError("Dump row manifest mismatch")
    identifier(metadata["database"])
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", metadata["mysqlImage"]):
        raise ValueError("Expected an immutable local MySQL image ID")
    return metadata


def verify(args):
    folder = Path(args.backup).resolve(strict=True)
    metadata = validate_backup(folder)
    database = metadata["database"]
    name = "exam-restore-check-" + uuid.uuid4().hex[:16]
    env = os.environ.copy()
    env["MYSQL_ROOT_PASSWORD"] = secrets.token_urlsafe(32)
    created = False
    report = {"verifiedAt": datetime.now(timezone.utc).isoformat(), "passed": False,
              "manifestSha256": sha(folder / "manifest.json")}
    try:
        # New container only; no published ports, no host mounts, no external network.
        run(["docker", "create", "--pull=never", "--name", name, "--network=none",
             "--memory=1g", "--cpus=1", "--env", "MYSQL_ROOT_PASSWORD",
             "--env", "MYSQL_DATABASE=" + database,
             "--label", "exam.restore-check=true", metadata["mysqlImage"],
             "--event-scheduler=OFF"], env=env, timeout=30)
        created = True
        run(["docker", "start", name], timeout=15)
        print("Restore rehearsal: waiting for isolated MySQL (maximum 90 seconds)...", flush=True)
        deadline = time.monotonic() + 90
        while True:
            try:
                # TCP avoids the temporary socket-only initialization server.
                run(["docker", "exec", name, "sh", "-c",
                     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"'], timeout=5)
                break
            except RuntimeError:
                if time.monotonic() >= deadline:
                    raise RuntimeError("Isolated MySQL did not become ready within 90 seconds")
                time.sleep(2)
        mysql(name, database, source=folder / "database.sql")
        actual_tables = set(mysql(name, database,
            "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() "
            "AND table_type='BASE TABLE'").splitlines())
        if actual_tables != set(metadata["rows"]):
            raise ValueError("Restored table list mismatch")
        queries = [f"SELECT '{identifier(table)}',COUNT(*) FROM `{table}`" for table in sorted(actual_tables)]
        rows = dict(line.split("\t") for line in mysql(name, database, " UNION ALL ".join(queries)).splitlines())
        if {key: int(value) for key, value in rows.items()} != metadata["rows"]:
            raise ValueError("Restored row counts differ from dump snapshot")
        report["tables"] = len(rows)
        report["rows"] = sum(metadata["rows"].values())
        report["flywayVersion"] = mysql(name, database,
            "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")
        if "uploads.zip" in metadata["files"]:
            with zipfile.ZipFile(folder / "uploads.zip") as archive:
                deadline = time.monotonic() + 120
                for entry in archive.infolist():
                    with archive.open(entry) as stream:
                        while stream.read(1024 * 1024):
                            if time.monotonic() > deadline:
                                raise TimeoutError("Upload archive verification exceeded 120 seconds")
                report["uploadFiles"] = len(archive.infolist())
        report["passed"] = True
    finally:
        if created:
            # Only the random container created by this invocation is removed.
            run(["docker", "rm", "--force", "--volumes", name], timeout=30)
            report["temporaryContainerRemoved"] = True
        (folder / "restore-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {report['tables']} tables, {report['rows']} rows, Flyway {report['flywayVersion']}; temporary container removed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    save = sub.add_parser("create", help="Read-only online DB + local upload backup")
    save.add_argument("--container", default="yf-exam-mysql")
    save.add_argument("--database", default="yf_boot_exam")
    save.add_argument("--output", default=str(ROOT / "work/backups/ops"))
    save.add_argument("--uploads-dir", help="Actual host upload folder when Docker/OS paths differ")
    check = sub.add_parser("verify", help="Restore ONLY into a new disposable container")
    check.add_argument("backup")
    args = parser.parse_args()
    try:
        backup(args) if args.action == "create" else verify(args)
    except Exception as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
