#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PROJECT=""
BRANCH=""
USERNAME=""
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

REPO_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT" "$REPO_DIR" "$WORKSPACE_ROOT")"
ensure_git_repo "$REPO_DIR"

info "build request accepted, project=$PROJECT, branch=$BRANCH, username=$USERNAME"
if [[ -x "$REPO_DIR/mvnw" ]]; then
  info "running Maven wrapper build"
  (cd "$REPO_DIR" && ./mvnw -B -DskipTests package)
elif [[ -f "$REPO_DIR/pom.xml" ]]; then
  info "running Maven build"
  (cd "$REPO_DIR" && mvn -B -DskipTests package)
elif [[ -f "$REPO_DIR/package.json" && -f "$REPO_DIR/pnpm-lock.yaml" ]]; then
  info "running pnpm build"
  (cd "$REPO_DIR" && pnpm install --offline && pnpm build)
elif [[ -f "$REPO_DIR/package.json" ]]; then
  info "running npm build"
  (cd "$REPO_DIR" && npm ci --offline && npm run build)
else
  fail "no supported build file found in $REPO_DIR"
fi
info "build completed"
