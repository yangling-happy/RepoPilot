#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PROJECT=""
BRANCH=""
USERNAME=""
ENVIRONMENT=""
ARTIFACT_PATH=""
REPO_DIR=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-workspace/root/repos}"

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
    --environment)
      ENVIRONMENT="${2:-}"
      shift 2
      ;;
    --artifact-path)
      ARTIFACT_PATH="${2:-}"
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
require_value "environment" "$ENVIRONMENT"

if [[ -z "$ARTIFACT_PATH" ]]; then
  REPO_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT" "$REPO_DIR" "$WORKSPACE_ROOT")"
  ensure_git_repo "$REPO_DIR"
  ARTIFACT_PATH="$(find "$REPO_DIR" -path "$REPO_DIR/.git" -prune -o -type f \( -name "*.jar" -o -name "*.war" \) -print | head -n 1)"
fi

require_value "artifactPath" "$ARTIFACT_PATH"
if [[ ! -f "$ARTIFACT_PATH" ]]; then
  fail "artifact not found: $ARTIFACT_PATH"
fi

info "deploy request accepted, project=$PROJECT, branch=$BRANCH, username=$USERNAME, environment=$ENVIRONMENT"
if [[ -z "${DEPLOY_TARGET_DIR:-}" ]]; then
  fail "DEPLOY_TARGET_DIR is not configured"
fi

TARGET_DIR="$DEPLOY_TARGET_DIR/$ENVIRONMENT/$PROJECT"
mkdir -p "$TARGET_DIR"
cp "$ARTIFACT_PATH" "$TARGET_DIR/"
info "deploy completed, artifact copied to $TARGET_DIR"
