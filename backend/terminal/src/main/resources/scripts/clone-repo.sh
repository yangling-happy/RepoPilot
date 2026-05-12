#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PROJECT_ID=""
BRANCH=""
USERNAME=""
REPO_URL=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-workspace/root/repos}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-id)
      PROJECT_ID="${2:-}"
      shift 2
      ;;
    --branch)
      BRANCH="${2:-}"
      shift 2
      ;;
    --username)
      USERNAME="${2:-}"
      shift 2
      ;;
    --repo-url)
      REPO_URL="${2:-}"
      shift 2
      ;;
    --workspace-root)
      WORKSPACE_ROOT="${2:-}"
      shift 2
      ;;
    *)
      fail "unsupported argument: $1"
      ;;
  esac
done

require_value "projectId" "$PROJECT_ID"
require_value "branch" "$BRANCH"
require_value "username" "$USERNAME"
require_value "repoUrl" "$REPO_URL"
if [[ "$REPO_URL" =~ ://[^/]+@ ]]; then
  fail "repoUrl must not contain credentials"
fi

TARGET_DIR="$WORKSPACE_ROOT/$USERNAME/$PROJECT_ID"

info "clone request accepted, projectId=$PROJECT_ID, branch=$BRANCH, username=$USERNAME"
if [[ -e "$TARGET_DIR" ]]; then
  fail "local directory already exists: $TARGET_DIR"
fi

mkdir -p "$(dirname "$TARGET_DIR")"
export GIT_TERMINAL_PROMPT=0

info "start cloning repository into $TARGET_DIR"
git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$TARGET_DIR"
HEAD_COMMIT="$(git -C "$TARGET_DIR" rev-parse HEAD)"
info "clone completed, HEAD=$HEAD_COMMIT"
