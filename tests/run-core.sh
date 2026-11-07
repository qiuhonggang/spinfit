#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_OUT="$(mktemp -d "${TMPDIR:-/tmp}/spinfit-core.XXXXXX")"
trap 'rm -rf "$TEST_OUT"' EXIT
javac -encoding UTF-8 -d "$TEST_OUT" app/src/com/spinfit/bike/core/*.java tests/CoreChecks.java
java -cp "$TEST_OUT" CoreChecks
