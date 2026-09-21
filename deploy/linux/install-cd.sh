#!/usr/bin/env bash
# One-time installation, run by an administrator from a reviewed repository copy.
set -euo pipefail
umask 077
base=/opt/enterprise-exam-test
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
[[ $EUID -eq 0 && -f "$base/.exam-test-owned" ]] || { echo 'Run as root on the existing test server.' >&2; exit 1; }
[[ $# -eq 1 && -f "$1" ]] || { echo 'Usage: install-cd.sh /absolute/path/to/ci-key.pub' >&2; exit 1; }
for command in python3 useradd install visudo ssh-keygen timeout rsync; do command -v "$command" >/dev/null; done
timeout 10 ssh-keygen -lf "$1" >/dev/null
# Reject multiline, authorized_keys options and private keys.
python3 - "$1" <<'PY'
import pathlib, re, sys
key = pathlib.Path(sys.argv[1]).read_text().strip()
if not re.fullmatch(r'ssh-ed25519 [A-Za-z0-9+/=]+(?: [^\r\n]+)?', key):
    raise SystemExit('Expected one plain Ed25519 public key')
PY
[[ -f "$base/backup.py" && -f "$base/scripts/ops/ops_common.py" && -f "$base/scripts/ops/backup.py" ]] || {
  echo 'Install the existing Linux backup tools first; see README.md.' >&2; exit 1;
}
if ! id exam-deploy >/dev/null 2>&1; then
  timeout 15 useradd --system --create-home --home-dir /var/lib/enterprise-exam-deploy --shell /bin/bash exam-deploy
fi
[[ $(getent passwd exam-deploy | cut -d: -f6) == /var/lib/enterprise-exam-deploy ]] || {
  echo 'Existing exam-deploy account has an unexpected home; stop and review.' >&2; exit 1;
}
install -d -o root -g root -m 0755 /var/lib/enterprise-exam-deploy
install -d -o exam-deploy -g exam-deploy -m 0700 /var/lib/enterprise-exam-deploy/incoming
install -d -o root -g root -m 0755 /var/lib/enterprise-exam-deploy/.ssh
key_tmp=$(mktemp)
sudo_tmp=$(mktemp)
trap 'rm -f -- "$key_tmp" "$sudo_tmp"' EXIT
printf 'restrict %s\n' "$(cat -- "$1")" > "$key_tmp"
install -o root -g root -m 0644 "$key_tmp" /var/lib/enterprise-exam-deploy/.ssh/authorized_keys
install -o root -g root -m 0755 "$script_dir/deploy_test.py" /usr/local/sbin/enterprise-exam-test-deploy
printf '%s\n' 'exam-deploy ALL=(root) NOPASSWD: /usr/local/sbin/enterprise-exam-test-deploy *' > "$sudo_tmp"
timeout 10 visudo -cf "$sudo_tmp"
install -o root -g root -m 0440 "$sudo_tmp" /etc/sudoers.d/enterprise-exam-test-deploy
echo 'CD helper and dedicated SSH account installed. Application was not restarted.'
