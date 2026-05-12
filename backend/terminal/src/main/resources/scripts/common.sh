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
  local workspace_root="${4:-workspace/root/repos}"

  if [[ -n "$repo_dir" ]]; then
    printf '%s\n' "$repo_dir"
    return
  fi

  printf '%s/%s/%s\n' "$workspace_root" "$username" "$project"
}

ensure_git_repo() {
  local repo_dir="$1"
  if [[ ! -d "$repo_dir/.git" && ! -f "$repo_dir/.git" ]]; then
    fail "local repository not found: $repo_dir"
  fi
}
