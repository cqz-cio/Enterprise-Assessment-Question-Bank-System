"""Bounded, non-interactive process execution for maintenance commands (Python 3.10+)."""
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
LOGS = ROOT / "work" / "codex-logs"


def run(args, *, cwd=ROOT, timeout=120, stdin=None, stdout=None, env=None, record_output=False):
    LOGS.mkdir(parents=True, exist_ok=True)
    log = LOGS / (time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8] + "-ops.log")
    started = time.monotonic()
    flags = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {"start_new_session": True}
    # stdout may contain a database or configuration: never copy it into console/logs.
    output = log.with_suffix(".output") if record_output and stdout is None else stdout
    captured = b""
    with log.open("wb") as errors, (tempfile.TemporaryFile() if output is None else output.open("wb")) as out:
        errors.write(f"command={args!r}\ncwd={cwd}\n".encode("utf-8"))
        errors.flush()
        process = subprocess.Popen(args, cwd=cwd, env=env, stdin=stdin or subprocess.DEVNULL,
                                   stdout=out, stderr=errors, **flags)
        if record_output:
            print(f"PID={process.pid}; stdout={output}; stderr={log}", flush=True)
        previous = -1
        progress = started
        while process.poll() is None:
            time.sleep(.25)
            size = log.stat().st_size + os.fstat(out.fileno()).st_size
            now = time.monotonic()
            if size != previous:
                previous, progress = size, now
            if now - started > timeout or now - progress > 60:
                tree_stopped = True
                if os.name == "nt":
                    try:
                        killer = subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                                capture_output=True, timeout=10, creationflags=subprocess.CREATE_NO_WINDOW)
                        tree_stopped = killer.returncode == 0
                        errors.write(killer.stdout + killer.stderr)
                    except subprocess.TimeoutExpired:
                        tree_stopped = False
                    # Retain our own process handle as a fallback if taskkill is denied.
                    if process.poll() is None:
                        process.kill()
                else:
                    os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
                errors.write(f"\nTIMEOUT pid={process.pid} cwd={cwd} elapsed={now-started:.1f}s\n".encode())
                cleanup = "process tree stopped" if tree_stopped else "parent stopped; child-tree cleanup could not be confirmed"
                raise RuntimeError(f"Command timed out; {cleanup}. Log: {log}")
        errors.write(f"\nexit={process.returncode} cwd={cwd} elapsed={time.monotonic()-started:.1f}s\n".encode())
        if output is None:
            out.seek(0)
            captured = out.read()
    if process.returncode:
        raise RuntimeError(f"Command failed (exit {process.returncode}). Log: {log}")
    return output.read_bytes() if record_output and stdout is None else captured
