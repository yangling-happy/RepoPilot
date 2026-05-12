#!/usr/bin/env bash

timestamp() {
  date "+%Y-%m-%d %H:%M:%S"
}

log_line() {
  local level="$1"
  shift
  printf '[%s] [%s] %s\n' "$(timestamp)" "$level" "$*"
}

info() {
  log_line "INFO" "$@"
}

warn() {
  log_line "WARN" "$@"
}

error() {
  log_line "ERROR" "$@"
}

fail() {
  error "$@"
  write_task_result "FAILED" "$*" ""
  exit 1
}

require_value() {
  local name="$1"
  local value="${2:-}"
  if [[ -z "$value" ]]; then
    fail "$name is required"
  fi
}

resolve_repo_dir() {
  local username="$1"
  local project="$2"
  local repo_dir="${3:-}"
  local workspace_root="${4:-${REPOPILOT_WORKSPACE_BASE:-.}}"

  if [[ -n "$repo_dir" ]]; then
    printf '%s\n' "$repo_dir"
    return
  fi

  printf '%s/workspace/%s/repos/project-%s\n' "$workspace_root" "$(safe_username "$username")" "$project"
}

ensure_git_repo() {
  local repo_dir="$1"
  if [[ ! -d "$repo_dir/.git" && ! -f "$repo_dir/.git" ]]; then
    fail "local repository not found: $repo_dir"
  fi
}

safe_username() {
  local username="$1"
  printf '%s' "$username" | sed 's/[^a-zA-Z0-9._-]/_/g'
}

shell_path() {
  local value="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$value"
    return
  fi
  printf '%s\n' "$value"
}

json_escape() {
  local value="${1:-}"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

write_task_result() {
  local status="$1"
  local message="${2:-}"
  local data="${3:-}"
  local result_file="${REPOPILOT_TASK_RESULT_FILE:-}"
  if [[ -z "$result_file" ]]; then
    return
  fi
  mkdir -p "$(dirname "$result_file")"
  if [[ -n "$data" ]]; then
    printf '{"status":"%s","message":"%s","data":%s}\n' \
      "$(json_escape "$status")" \
      "$(json_escape "$message")" \
      "$data" > "$result_file"
  else
    printf '{"status":"%s","message":"%s","data":null}\n' \
      "$(json_escape "$status")" \
      "$(json_escape "$message")" > "$result_file"
  fi
}
