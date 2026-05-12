#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PROJECT_ID=""
BRANCH=""
USERNAME=""
REPO_URL=""
PROJECT_PATH=""
WORKSPACE_ROOT="${REPOPILOT_WORKSPACE_ROOT:-${REPOPILOT_WORKSPACE_BASE:-.}}"

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
    --project-path)
      PROJECT_PATH="${2:-}"
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
require_value "GITLAB_TOKEN" "${GITLAB_TOKEN:-}"
if [[ "$REPO_URL" =~ ://[^/]+@ ]]; then
  fail "repoUrl must not contain credentials"
fi

WORKSPACE_ROOT="$(shell_path "$WORKSPACE_ROOT")"
TARGET_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT_ID" "" "$WORKSPACE_ROOT")"
WORKSPACE_PATH="$WORKSPACE_ROOT/workspace/$(safe_username "$USERNAME")"
if [[ -z "$PROJECT_PATH" ]]; then
  PROJECT_PATH="project-$PROJECT_ID"
fi

info "clone request accepted, projectId=$PROJECT_ID, branch=$BRANCH, username=$USERNAME"
if [[ -e "$TARGET_DIR" ]]; then
  fail "local directory already exists: $TARGET_DIR"
fi

mkdir -p "$(dirname "$TARGET_DIR")"
export GIT_TERMINAL_PROMPT=0
ASKPASS_FILE="$(mktemp)"
cleanup() {
  rm -f "$ASKPASS_FILE"
}
trap cleanup EXIT
cat > "$ASKPASS_FILE" <<'ASKPASS'
#!/usr/bin/env sh
case "$1" in
  *Username*) printf '%s\n' "${GIT_USERNAME:-oauth2}" ;;
  *Password*) printf '%s\n' "${GITLAB_TOKEN:-}" ;;
  *) printf '\n' ;;
esac
ASKPASS
chmod +x "$ASKPASS_FILE"
export GIT_ASKPASS="$ASKPASS_FILE"

info "start cloning repository into $TARGET_DIR"
git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$TARGET_DIR"
HEAD_COMMIT="$(git -C "$TARGET_DIR" rev-parse HEAD)"
info "clone completed, HEAD=$HEAD_COMMIT"

CLONE_DATA="$(
  printf '{"projectId":%s,"gitlabUsername":"%s","projectPath":"%s","branch":"%s","cloneUrl":"%s","workspacePath":"%s","localPath":"%s","commitId":"%s"}' \
    "$PROJECT_ID" \
    "$(json_escape "$USERNAME")" \
    "$(json_escape "$PROJECT_PATH")" \
    "$(json_escape "$BRANCH")" \
    "$(json_escape "$REPO_URL")" \
    "$(json_escape "$WORKSPACE_PATH")" \
    "$(json_escape "$TARGET_DIR")" \
    "$(json_escape "$HEAD_COMMIT")"
)"
write_task_result "SUCCESS" "Clone completed" "$CLONE_DATA"
