#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/common.sh"

PROJECT_ID=""
BRANCH=""
USERNAME=""
REPO_URL=""
TARGET_DIR=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-workspace/root/repos}"

while [ $# -gt 0 ]; do
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
    --target-dir)
      TARGET_DIR="${2:-}"
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
if echo "$REPO_URL" | grep -qE '://[^/]+@'; then
  fail "repoUrl must not contain credentials"
fi

if [[ -z "$TARGET_DIR" ]]; then
  TARGET_DIR="$WORKSPACE_ROOT/project-$PROJECT_ID"
fi

info "clone request accepted, projectId=$PROJECT_ID, branch=$BRANCH, username=$USERNAME"
if [[ -e "$TARGET_DIR" ]]; then
  # 如果目录存在但不是 git 仓库，或工作区没有实际文件（只剩 .git），删掉重新克隆
  if [[ ! -d "$TARGET_DIR/.git" && ! -f "$TARGET_DIR/.git" ]]; then
    warn "repository directory exists but is not a valid git repo, removing and re-cloning"
    rm -rf "$TARGET_DIR"
  else
    FILE_COUNT="$(find "$TARGET_DIR" -maxdepth 1 -not -name '.git' -not -name '.' | wc -l)"
    if [[ "$FILE_COUNT" -eq 0 ]]; then
      warn "repository directory exists but worktree is empty, removing and re-cloning"
      rm -rf "$TARGET_DIR"
    fi
  fi
fi

if [[ -e "$TARGET_DIR" ]]; then
  ensure_git_repo "$TARGET_DIR"
  OLD_HEAD="$(git -C "$TARGET_DIR" rev-parse HEAD)"
  CURRENT_ORIGIN="$(git -C "$TARGET_DIR" remote get-url origin 2>/dev/null || true)"
  if [[ -n "$CURRENT_ORIGIN" && "$CURRENT_ORIGIN" != "$REPO_URL" ]]; then
    fail "local repository origin does not match requested repoUrl"
  fi
  setup_git_auth
  info "local repository exists, synchronizing $TARGET_DIR"
  git -C "$TARGET_DIR" fetch origin "$BRANCH"
  if git -C "$TARGET_DIR" show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git -C "$TARGET_DIR" checkout "$BRANCH"
  else
    git -C "$TARGET_DIR" checkout -b "$BRANCH" "origin/$BRANCH"
  fi
  git -C "$TARGET_DIR" pull --ff-only origin "$BRANCH"
  NEW_HEAD="$(git -C "$TARGET_DIR" rev-parse HEAD)"
  info "clone sync completed, HEAD=$NEW_HEAD"
  emit_result "OLD_HEAD" "$OLD_HEAD"
  emit_result "NEW_HEAD" "$NEW_HEAD"
  emit_result "HEAD" "$NEW_HEAD"
  emit_result "LOCAL_PATH" "$TARGET_DIR"
  exit 0
fi

mkdir -p "$(dirname "$TARGET_DIR")"
setup_git_auth

info "start cloning repository into $TARGET_DIR"
git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$TARGET_DIR"
HEAD_COMMIT="$(git -C "$TARGET_DIR" rev-parse HEAD)"
info "clone completed, HEAD=$HEAD_COMMIT"
emit_result "HEAD" "$HEAD_COMMIT"
emit_result "NEW_HEAD" "$HEAD_COMMIT"
emit_result "LOCAL_PATH" "$TARGET_DIR"
