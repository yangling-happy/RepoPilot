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
require_value "REPOPILOT_TASK_RESULT_FILE" "${REPOPILOT_TASK_RESULT_FILE:-}"

WORKSPACE_ROOT="$(shell_path "$WORKSPACE_ROOT")"
REPO_DIR="$(resolve_repo_dir "$USERNAME" "$PROJECT" "$REPO_DIR" "$WORKSPACE_ROOT")"
ensure_git_repo "$REPO_DIR"

info "scan local doc request accepted, project=$PROJECT, branch=$BRANCH, username=$USERNAME"

find_business_cli_jar() {
  local backend_root="$1"
  local candidate=""
  if [[ -z "$backend_root" ]]; then
    return
  fi
  for candidate in "$backend_root"/business/target/business-*.jar; do
    if [[ -f "$candidate" && "$candidate" != *.original && "$candidate" != *-sources.jar && "$candidate" != *-javadoc.jar ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
}

build_business_cli_jar() {
  local backend_root="$1"
  if [[ -z "$backend_root" || ! -f "$backend_root/pom.xml" ]]; then
    fail "backend root not found; set REPOPILOT_BACKEND_ROOT"
  fi

  info "business CLI jar not found; packaging backend/business"
  if [[ -f "$backend_root/mvnw" ]]; then
    if (cd "$backend_root" && bash ./mvnw -pl business -am package -DskipTests); then
      return 0
    fi
    warn "mvnw package failed; falling back to mvn from PATH"
  fi

  if ! command -v mvn >/dev/null 2>&1; then
    fail "maven is not available; build backend/business or set REPOPILOT_BUSINESS_CLI_JAR"
  fi

  if ! (cd "$backend_root" && mvn -pl business -am package -DskipTests); then
    fail "business CLI jar build failed"
  fi
}

BUSINESS_CLI_JAR="${REPOPILOT_BUSINESS_CLI_JAR:-}"
BACKEND_ROOT="$(shell_path "${REPOPILOT_BACKEND_ROOT:-}")"
if [[ -z "$BUSINESS_CLI_JAR" ]]; then
  BUSINESS_CLI_JAR="$(find_business_cli_jar "$BACKEND_ROOT")"
fi
if [[ -z "$BUSINESS_CLI_JAR" ]]; then
  build_business_cli_jar "$BACKEND_ROOT"
  BUSINESS_CLI_JAR="$(find_business_cli_jar "$BACKEND_ROOT")"
fi
if [[ -z "$BUSINESS_CLI_JAR" ]]; then
  fail "business CLI jar not found after package; set REPOPILOT_BUSINESS_CLI_JAR"
fi
BUSINESS_CLI_JAR="$(shell_path "$BUSINESS_CLI_JAR")"

export USER_WORKSPACE_BASE="$WORKSPACE_ROOT"
info "starting business local scan CLI"
java -jar "$BUSINESS_CLI_JAR" \
  --spring.main.web-application-type=none \
  --repopilot.cli.mode=scan-local \
  --repopilot.cli.gitlab-username="$USERNAME" \
  --repopilot.cli.project="$PROJECT" \
  --repopilot.cli.branch="$BRANCH" \
  --repopilot.cli.terminal-session-id="${REPOPILOT_SESSION_ID:-}" \
  --repopilot.cli.result-file="$REPOPILOT_TASK_RESULT_FILE"
info "scan local doc CLI completed"
