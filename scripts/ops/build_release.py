"""Build current frontend and tested backend into a checksummed release folder."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import time
import uuid
import zipfile

from ops_common import ROOT, run


def build(args):
    dirty = bool(run(["git", "status", "--porcelain", "--untracked-files=normal"]))
    if dirty and not args.allow_dirty:
        raise ValueError("Working tree has uncommitted files; commit the intended release first, or use --allow-dirty for a local test build")
    commit = run(["git", "rev-parse", "HEAD"]).decode().strip()
    print("Building production frontend (maximum 300 seconds)...", flush=True)
    run(["node", "node_modules/vite/bin/vite.js", "build", "--mode", "pro"],
        cwd=ROOT / "yf-bev2-vue", timeout=300, record_output=True)
    print("Building and testing backend (maximum 300 seconds)...", flush=True)
    command = ["mvn", "-B", "-ntp", "-Prelease"]
    if args.offline:
        command.append("-o")
    if args.maven_settings:
        command += ["-s", str(Path(args.maven_settings).resolve(strict=True))]
    if args.maven_repository:
        command += ["-Dmaven.repo.local=" + str(Path(args.maven_repository).resolve(strict=True))]
    command += ["clean", "verify"]
    if os.name == "nt":
        # cmd is only used for the Maven .cmd executable, never for filesystem operations.
        command = ["cmd.exe", "/d", "/c", *command]
    run(command, cwd=ROOT / "yf-bev2-api", timeout=300, record_output=True)
    jar = ROOT / "yf-bev2-api/target/release-build/yf-bev2-api.jar"
    frontend = ROOT / "yf-bev2-vue/dist-pro"
    expected = {file.relative_to(frontend).as_posix(): file for file in frontend.rglob("*") if file.is_file()}
    with zipfile.ZipFile(jar) as archive:
        actual = {name.removeprefix("BOOT-INF/classes/static/") for name in archive.namelist()
                  if name.startswith("BOOT-INF/classes/static/") and not name.endswith("/")}
        if actual != set(expected) or "index.html" not in actual:
            raise ValueError("Packaged frontend file list differs from the fresh build")
        for name, source in expected.items():
            if hashlib.sha256(archive.read("BOOT-INF/classes/static/" + name)).digest() != hashlib.sha256(source.read_bytes()).digest():
                raise ValueError("Packaged frontend differs: " + name)
        if "BOOT-INF/classes/application-prod.yml" not in archive.namelist():
            raise ValueError("Production profile is missing from the JAR")
    destination = ROOT / "yf-bev2-api/target/releases" / (time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    destination.mkdir(parents=True)
    artifact = destination / jar.name
    shutil.copy2(jar, artifact)
    metadata = {"gitCommit": commit, "dirtyWorkingTree": dirty,
                "jarSha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                "frontendFilesVerified": len(expected), "backendTests": "mvn clean verify passed"}
    (destination / "release.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"Release built and checked: {destination}")
    if dirty:
        print("LOCAL TEST BUILD: includes uncommitted workspace changes; not an exact Git release.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-dirty", action="store_true")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--maven-settings")
    parser.add_argument("--maven-repository")
    try:
        build(parser.parse_args())
    except Exception as error:
        print(f"FAILED: {error}", file=sys.stderr)
        sys.exit(1)
