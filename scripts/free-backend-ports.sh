#!/usr/bin/env bash
set -euo pipefail

# Collect listener PIDs for business/terminal default ports.
PIDS=$(ss -ltnp 2>/dev/null \
  | awk '/:8080|:8081/ { if (match($0, /pid=[0-9]+/)) { print substr($0, RSTART + 4, RLENGTH - 4) } }' \
  | sort -u)

if [ -z "$PIDS" ]; then
  exit 0
fi

# shellcheck disable=SC2086
kill $PIDS || true