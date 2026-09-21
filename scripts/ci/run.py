"""Run a CI command with finite timeout, idle detection and workspace-local logs."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from ops_common import run

if __name__ == '__main__':
    try:
        output = run(sys.argv[2:], timeout=int(sys.argv[1]), record_output=True)
        # Commands here are builds/tests only, never commands handling server secrets.
        print(output.decode('utf-8', errors='replace')[-12000:])
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
