#!/usr/bin/env sh

MAVEN_VERSION="${MAVEN_VERSION:-3.9.6}"
MAVEN_HOME="/tmp/apache-maven-${MAVEN_VERSION}"

ensure_maven() {
  if command -v mvn >/dev/null 2>&1; then
    return 0
  fi

  if [ -x "$MAVEN_HOME/bin/mvn" ]; then
    PATH="$MAVEN_HOME/bin:$PATH"
    export PATH
    return 0
  fi

  log_line "INFO" "Maven not found, installing ${MAVEN_VERSION} to $MAVEN_HOME"
  MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  MAVEN_TMP="/tmp/maven-install-$$.tar.gz"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$MAVEN_TMP" "$MAVEN_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$MAVEN_TMP" "$MAVEN_URL"
  else
    log_line "ERROR" "neither curl nor wget available, cannot install Maven"
    return 1
  fi
  tar -xzf "$MAVEN_TMP" -C /tmp
  rm -f "$MAVEN_TMP"
  PATH="$MAVEN_HOME/bin:$PATH"
  export PATH
  log_line "INFO" "Maven ${MAVEN_VERSION} installed successfully"
}

timestamp() {
  date "+%Y-%m-%d %H:%M:%S"
}

log_line() {
  level="$1"
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
  name="$1"
  value="${2:-}"
  if [ -z "$value" ]; then
    fail "$name is required"
  fi
}

resolve_repo_dir() {
  username="$1"
  project="$2"
  repo_dir="${3:-}"
  workspace_root="${4:-workspace/root/repos}"

  if [ -n "$repo_dir" ]; then
    printf '%s\n' "$repo_dir"
    return
  fi

  printf '%s/%s/%s\n' "$workspace_root" "$username" "$project"
}

ensure_git_repo() {
  repo_dir="$1"
  if [ ! -d "$repo_dir/.git" ] && [ ! -f "$repo_dir/.git" ]; then
    fail "local repository not found: $repo_dir"
  fi
}
