"""Run local end-to-end acceptance with fresh fixtures and unconditional cleanup."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import shutil
import sys
import time
import environment as e

sys.path.insert(0, str(e.ROOT / 'scripts/ops'))
from ops_common import run


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-load', action='store_true', help='Skip the 100-user workload')
    options = parser.parse_args()
    if e.STATE.exists() or (e.ROOT / '.local/qa-fixtures.json').exists():
        raise RuntimeError('Existing QA environment/fixture; inspect and clean it before a fresh run')
    if not (e.ROOT / 'yf-bev2-api/target/release-build/yf-bev2-api.jar').exists():
        raise RuntimeError('Build the current frontend and release JAR first; see scripts/qa/README.md')
    if os.name != 'nt':
        raise RuntimeError('This local environment launcher requires Windows')
    stamp = time.strftime('%Y%m%d-%H%M%S')
    summary = {'started': stamp, 'steps': [], 'cleanup': False}
    summary_path = e.LOGS / ('qa-run-' + stamp + '.json')
    e.LOGS.mkdir(parents=True, exist_ok=True)
    previous = [e.LOGS / 'qa-api-results.json', e.LOGS / 'qa-load-results.json',
                e.ROOT / 'work/qa-browser/results.json']
    archive = e.ROOT / 'work/qa-archive' / stamp
    for report in previous:
        if report.exists():
            archive.mkdir(parents=True, exist_ok=True)
            shutil.copy2(report, archive / report.name)
            report.unlink()

    def step(name, args, timeout):
        print('RUN ' + name, flush=True)
        begun = time.monotonic()
        result = {'name': name, 'status': 'FAIL'}
        try:
            # Keep the outer runner informed while the inner watchdog monitors actual log progress.
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                job = pool.submit(run, args, timeout=timeout, record_output=True)
                while True:
                    try:
                        output = job.result(timeout=10)
                        break
                    except concurrent.futures.TimeoutError:
                        if job.done():
                            raise
                        print(f'RUN {name}: {time.monotonic()-begun:.0f}s; child watchdog active, output in recorded log', flush=True)
            print(output.decode('utf-8', 'replace')[-18000:], flush=True)
            result['status'] = 'PASS'
        finally:
            result['seconds'] = round(time.monotonic() - begun, 2)
            summary['steps'].append(result)
            summary_path.write_text(json.dumps(summary, indent=2), encoding='utf-8')

    python = sys.executable
    try:
        step('browser dependency', ['node', '-e', "require('playwright');console.log('Playwright available')"], 15)
        step('prepare isolated environment', [python, 'scripts/qa/environment.py', 'prepare'], 120)
        step('start isolated backend', [python, 'scripts/qa/environment.py', 'start'], 120)
        for action in ['prepare', 'api', 'grading', 'expiry']:
            step(action, [python, 'scripts/qa/acceptance.py', action], 120)
        step('imports', [python, 'scripts/qa/extended.py', 'imports'], 120)
        step('stop backend for recovery check', [python, 'scripts/qa/environment.py', 'stop'], 30)
        step('restart backend with original QA keys', [python, 'scripts/qa/environment.py', 'start'], 120)
        for action in ['archive_recovery', 'immediate'] + ([] if options.skip_load else ['load']) + ['reports', 'ui_prepare']:
            step(action, [python, 'scripts/qa/extended.py', action], 120)
        step('answer save ordering and recovery', ['node', 'scripts/qa/answer-queue.cjs'], 30)
        step('real browser flows', ['node', 'scripts/qa/browser.cjs', 'all'], 120)
        summary['status'] = 'PASS'
    finally:
        if e.STATE.exists():
            try:
                step('cleanup and source data verification', [python, 'scripts/qa/environment.py', 'cleanup'], 120)
                summary['cleanup'] = True
            except Exception:
                summary['status'] = 'FAIL'
                summary_path.write_text(json.dumps(summary, indent=2), encoding='utf-8')
                raise
        summary.setdefault('status', 'FAIL')
        summary_path.write_text(json.dumps(summary, indent=2), encoding='utf-8')
        print('Run summary: ' + str(summary_path), flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
