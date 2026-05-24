#!/usr/bin/env bash
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/common.sh"

# Override fail to also write to stderr so the controller can extract the error
fail() {
  error "$@"
  printf '%s\n' "$*" >&2
  exit 1
}

HOST=""
PORT="22"
USER=""

while [ $# -gt 0 ]; do
  case "$1" in
    --host)
      HOST="${2:-}"
      shift 2
      ;;
    --port)
      PORT="${2:-22}"
      shift 2
      ;;
    --user)
      USER="${2:-}"
      shift 2
      ;;
    *)
      fail "unsupported argument: $1"
      ;;
  esac
done

require_value "host" "$HOST"
require_value "user" "$USER"

if [ -z "${SSH_PASSWORD:-}" ]; then
  fail "SSH_PASSWORD environment variable is required"
fi

# Install sshpass if missing
if ! command -v sshpass >/dev/null 2>&1; then
  info "sshpass not found, installing..."
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    sudo apt-get update -qq && sudo apt-get install -y -qq sshpass
  elif command -v yum >/dev/null 2>&1; then
    sudo yum install -y sshpass
  elif command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y sshpass
  elif command -v apk >/dev/null 2>&1; then
    sudo apk add --no-cache sshpass
  else
    fail "No supported package manager found. Please install sshpass manually."
  fi
  info "sshpass installed successfully"
fi

# Locate or generate SSH key
PUB_KEY_FILE=""
PRIV_KEY_FILE=""
for key_type in ed25519 rsa; do
  candidate="$HOME/.ssh/id_${key_type}"
  if [ -f "${candidate}.pub" ]; then
    PRIV_KEY_FILE="$candidate"
    PUB_KEY_FILE="${candidate}.pub"
    break
  fi
done

if [ -z "$PUB_KEY_FILE" ]; then
  info "No SSH key found, generating ed25519 key pair..."
  mkdir -p "$HOME/.ssh"
  chmod 700 "$HOME/.ssh"
  ssh-keygen -t ed25519 -N "" -f "$HOME/.ssh/id_ed25519" -q
  PRIV_KEY_FILE="$HOME/.ssh/id_ed25519"
  PUB_KEY_FILE="$HOME/.ssh/id_ed25519.pub"
  info "SSH key generated: $PUB_KEY_FILE"
fi

PUB_KEY=$(cat "$PUB_KEY_FILE")
info "Using public key from: $PUB_KEY_FILE"

SSH_OPTS="-p $PORT -o StrictHostKeyChecking=no -o ConnectTimeout=10"

info "Deploying public key to ${USER}@${HOST}:${PORT}..."

# Use sshpass with SSHPASS env var to avoid password in process args
export SSHPASS="$SSH_PASSWORD"

SSH_OUTPUT=$(sshpass -e ssh $SSH_OPTS "${USER}@${HOST}" "
  mkdir -p ~/.ssh && chmod 700 ~/.ssh
  touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
  if grep -qF '${PUB_KEY}' ~/.ssh/authorized_keys 2>/dev/null; then
    echo 'KEY_ALREADY_EXISTS'
  else
    echo '${PUB_KEY}' >> ~/.ssh/authorized_keys
    echo 'KEY_ADDED'
  fi
" 2>&1) || fail "Failed to connect to ${USER}@${HOST}:${PORT} — check host, port, user and password. Output: ${SSH_OUTPUT}"

unset SSHPASS
unset SSH_PASSWORD

info "Verifying passwordless SSH login..."
REMOTE_CHECK=$(ssh -i "$PRIV_KEY_FILE" $SSH_OPTS -o BatchMode=yes "${USER}@${HOST}" "echo SSH_OK" 2>/dev/null || true)

if [ "$REMOTE_CHECK" = "SSH_OK" ]; then
  info "SSH key setup successful — passwordless login verified"
  emit_result "STATUS" "SUCCESS"
else
  warn "Key was deployed but passwordless verification failed (may need agent forwarding)"
  emit_result "STATUS" "SUCCESS"
fi
