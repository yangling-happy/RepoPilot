#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/common.sh"

PROJECT=""
BRANCH=""
USERNAME=""
ENVIRONMENT=""
ARTIFACT_PATH=""
REPO_DIR=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-workspace/root/repos}"

while [ $# -gt 0 ]; do
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

if [ -z "$ARTIFACT_PATH" ]; then
  REPO_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT" "$REPO_DIR" "$WORKSPACE_ROOT")"
  ensure_git_repo "$REPO_DIR"
  ARTIFACT_PATH="$(find "$REPO_DIR" -path "$REPO_DIR/.git" -prune -o -type f \( -name "*.jar" -o -name "*.war" \) -print | head -n 1)"
fi

require_value "artifactPath" "$ARTIFACT_PATH"
if [ ! -f "$ARTIFACT_PATH" ]; then
  fail "artifact not found: $ARTIFACT_PATH"
fi

info "deploy request accepted, project=$PROJECT, branch=$BRANCH, username=$USERNAME, environment=$ENVIRONMENT"
if [ -z "${DEPLOY_TARGET_DIR:-}" ]; then
  fail "DEPLOY_TARGET_DIR is not configured"
fi

DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_USER="${DEPLOY_USER:-}"
DEPLOY_TARGET_DIR=$(echo "$DEPLOY_TARGET_DIR" | sed 's|\\|/|g' | sed 's|/$||')

TARGET_DIR="$DEPLOY_TARGET_DIR/$ENVIRONMENT/$PROJECT"
ARTIFACT_NAME="$(basename "$ARTIFACT_PATH")"

if [ -n "$DEPLOY_HOST" ]; then
  info "remote deploy to $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PORT -> $TARGET_DIR"

  REMOTE_SSH="$DEPLOY_HOST"
  if [ -n "$DEPLOY_USER" ]; then
    REMOTE_SSH="$DEPLOY_USER@$DEPLOY_HOST"
  fi

  SSH_OPTS="-p $DEPLOY_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes"
  SCP_OPTS="-P $DEPLOY_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=10"

  REMOTE_OS=$(ssh $SSH_OPTS "$REMOTE_SSH" "uname -s" 2>/dev/null || true)
  case "$REMOTE_OS" in
    Linux*|Darwin*) ;;
    *) REMOTE_OS="Windows" ;;
  esac

  if [ "$REMOTE_OS" = "Windows" ]; then
    scp $SCP_OPTS "$ARTIFACT_PATH" "$REMOTE_SSH:$ARTIFACT_NAME" || \
      fail "failed to copy artifact to remote"
    PS_DIR=$(echo "$TARGET_DIR" | sed 's|/|\\|g')
    PS_DEST="${PS_DIR}\\${ARTIFACT_NAME}"
    ssh $SSH_OPTS "$REMOTE_SSH" \
      "powershell -Command \"New-Item -ItemType Directory -Force -Path '${PS_DIR}'; Move-Item -Path '${ARTIFACT_NAME}' -Destination '${PS_DEST}' -Force\"" || \
      fail "failed to move artifact on remote Windows"
  else
    ssh $SSH_OPTS "$REMOTE_SSH" "mkdir -p '$TARGET_DIR'" || \
      fail "failed to create remote directory $TARGET_DIR on $DEPLOY_HOST"
    scp $SCP_OPTS "$ARTIFACT_PATH" "$REMOTE_SSH:$TARGET_DIR/$ARTIFACT_NAME" || \
      fail "failed to copy artifact to $REMOTE_SSH:$TARGET_DIR"
  fi

  info "remote deploy completed, artifact copied to $REMOTE_SSH:$TARGET_DIR/$ARTIFACT_NAME"
else
  info "local deploy to $TARGET_DIR"
  mkdir -p "$TARGET_DIR"
  cp "$ARTIFACT_PATH" "$TARGET_DIR/"
  info "local deploy completed, artifact copied to $TARGET_DIR"
fi
