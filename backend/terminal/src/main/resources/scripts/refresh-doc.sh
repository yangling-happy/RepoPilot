#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PROJECT=""
BRANCH=""
USERNAME=""
REPO_DIR=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-${REPOPILOT_WORKSPACE_BASE:-.}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project)
      PROJECT="${2:-}"
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
    --repo-dir)
      REPO_DIR="${2:-}"
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

require_value "project" "$PROJECT"
require_value "branch" "$BRANCH"
require_value "username" "$USERNAME"

WORKSPACE_ROOT="$(shell_path "$WORKSPACE_ROOT")"
REPO_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT" "$REPO_DIR" "$WORKSPACE_ROOT")"
ensure_git_repo "$REPO_DIR"

info "refresh doc request accepted, project=$PROJECT, branch=$BRANCH, username=$USERNAME"
OLD_HEAD="$(git -C "$REPO_DIR" rev-parse HEAD)"
info "current HEAD=$OLD_HEAD"

export GIT_TERMINAL_PROMPT=0
git -C "$REPO_DIR" fetch origin "$BRANCH"
git -C "$REPO_DIR" checkout "$BRANCH"
git -C "$REPO_DIR" pull --ff-only origin "$BRANCH"

NEW_HEAD="$(git -C "$REPO_DIR" rev-parse HEAD)"
if [[ "$OLD_HEAD" == "$NEW_HEAD" ]]; then
  info "refresh completed, no new commits"
else
  info "refresh completed, new HEAD=$NEW_HEAD"
fi
