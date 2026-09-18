#!/usr/bin/env bash
# Usage: ./scripts/agent-quality-gate.sh [gradle-module ...]
# Example: ./scripts/agent-quality-gate.sh app:shared server:order server:common
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODULES=("$@")
if [[ ${#MODULES[@]} -eq 0 ]]; then
  echo "Running full ktlintCheck..."
  ./gradlew ktlintCheck
  echo "OK: full ktlintCheck"
  exit 0
fi

TASKS=()
for m in "${MODULES[@]}"; do
  # accept app:shared or :app:shared
  m="${m#:}"
  TASKS+=(":${m}:ktlintCheck")
done

echo "Running: ./gradlew ${TASKS[*]}"
./gradlew "${TASKS[@]}"
echo "OK: ${TASKS[*]}"
