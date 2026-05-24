#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/common.sh"

HOST=""
PORT="22"
USER=""

while [ $# -gt 0 ]; do
  case "$1" in
    --host)  HOST="${2:-}"; shift 2 ;;
    --port)  PORT="${2:-22}"; shift 2 ;;
    --user)  USER="${2:-}"; shift 2 ;;
    *)       shift ;;
  esac
done

if [ -z "$HOST" ]; then echo "ERROR: host is required"; exit 1; fi
if [ -z "$USER" ]; then echo "ERROR: user is required"; exit 1; fi
if [ -z "${SSH_PASSWORD:-}" ]; then echo "ERROR: SSH_PASSWORD is required"; exit 1; fi
if ! command -v sshpass >/dev/null 2>&1; then
  echo "ERROR: sshpass is not installed. Run: sudo apt install sshpass"
  exit 1
fi

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
  mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh"
  if ! ssh-keygen -t ed25519 -N "" -f "$HOME/.ssh/id_ed25519" -q; then
    echo "ERROR: Failed to generate SSH key"; exit 1
  fi
  PRIV_KEY_FILE="$HOME/.ssh/id_ed25519"
  PUB_KEY_FILE="$HOME/.ssh/id_ed25519.pub"
fi

PUB_KEY=$(cat "$PUB_KEY_FILE")
export SSHPASS="$SSH_PASSWORD"
SSH_OPTS="-p $PORT -o StrictHostKeyChecking=no -o ConnectTimeout=10"

SSH_OUTPUT=$(sshpass -e ssh $SSH_OPTS "${USER}@${HOST}" "
  mkdir -p ~/.ssh && chmod 700 ~/.ssh
  touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
  if grep -qF '${PUB_KEY}' ~/.ssh/authorized_keys 2>/dev/null; then
    echo 'KEY_ALREADY_EXISTS'
  else
    echo '${PUB_KEY}' >> ~/.ssh/authorized_keys
    echo 'KEY_ADDED'
  fi
" 2>&1)
SSH_EXIT=$?
unset SSHPASS
unset SSH_PASSWORD

if [ $SSH_EXIT -ne 0 ]; then
  echo "ERROR: SSH failed (exit=$SSH_EXIT): ${SSH_OUTPUT}"
  exit 1
fi

REMOTE_CHECK=$(ssh -i "$PRIV_KEY_FILE" $SSH_OPTS -o BatchMode=yes "${USER}@${HOST}" "echo SSH_OK" 2>/dev/null || true)
if [ "$REMOTE_CHECK" = "SSH_OK" ]; then
  echo "[INFO] SSH key setup successful"
else
  echo "[INFO] Key deployed (verification may need agent forwarding)"
fi
emit_result "STATUS" "SUCCESS"
