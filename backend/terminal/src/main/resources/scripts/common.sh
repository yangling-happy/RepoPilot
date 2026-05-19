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

emit_result() {
  local key="$1"
  local value="${2:-}"
  printf 'REPOPILOT_RESULT_%s=%s\n' "$key" "$value"
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

setup_git_auth() {
  export GIT_TERMINAL_PROMPT=0
  if [[ -z "${GITLAB_TOKEN:-}" ]]; then
    return
  fi

  export GIT_USERNAME="${GIT_USERNAME:-git}"
  REPOPILOT_GIT_ASKPASS_FILE="$(mktemp)"
  cat >"$REPOPILOT_GIT_ASKPASS_FILE" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  *Username*) printf '%s\n' "${GIT_USERNAME:-git}" ;;
  *Password*) printf '%s\n' "${GITLAB_TOKEN:-}" ;;
  *) printf '\n' ;;
esac
EOF
  chmod 700 "$REPOPILOT_GIT_ASKPASS_FILE"
  export GIT_ASKPASS="$REPOPILOT_GIT_ASKPASS_FILE"
  trap 'rm -f "${REPOPILOT_GIT_ASKPASS_FILE:-}"' EXIT
}
