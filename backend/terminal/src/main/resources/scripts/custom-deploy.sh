#!/usr/bin/env sh
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/common.sh"

REPO_DIR=""

while [ $# -gt 0 ]; do
  case "$1" in
    --repo-dir)
      REPO_DIR="${2:-}"
      shift 2
      ;;
    *)
      fail "unsupported argument: $1"
      ;;
  esac
done

require_value "repoDir" "$REPO_DIR"

if [ ! -d "$REPO_DIR" ]; then
  fail "repository directory not found: $REPO_DIR"
fi

DEPLOY_SCRIPT="$REPO_DIR/deploy.sh"

if [ ! -f "$DEPLOY_SCRIPT" ]; then
  fail "deploy.sh not found in repository: $REPO_DIR"
fi

if [ ! -x "$DEPLOY_SCRIPT" ]; then
  info "deploy.sh is not executable, adding execute permission"
  chmod +x "$DEPLOY_SCRIPT"
fi

info "running custom deploy script: $DEPLOY_SCRIPT"

cd "$REPO_DIR"
"$DEPLOY_SCRIPT"
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  warn "deploy.sh exited with code $EXIT_CODE (treating as success)"
else
  info "custom deploy completed successfully"
fi

exit 0
