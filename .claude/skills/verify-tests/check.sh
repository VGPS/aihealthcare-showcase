#!/usr/bin/env bash
# verify-tests/check.sh — gather diff evidence + run the mapped tests.
# Usage: check.sh          -> uncommitted + staged changes (working tree vs HEAD)
#        check.sh last     -> most recent commit only (HEAD~1 vs HEAD)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

if [ "${1:-}" = "last" ]; then
  DIFF_CMD=(git diff HEAD~1 HEAD)
else
  DIFF_CMD=(git diff HEAD)
fi

CHANGED_JAVA=$("${DIFF_CMD[@]}" --name-only -- '*.java')
if [ -z "$CHANGED_JAVA" ]; then
  echo "No changed .java files in scope — nothing to verify."
  exit 0
fi

TEST_FILES=$(echo "$CHANGED_JAVA" | grep -E 'Test\.java$' || true)
PROD_FILES=$(echo "$CHANGED_JAVA" | grep -vE 'Test\.java$' || true)

echo "===== CHANGED PRODUCTION FILES ====="
echo "${PROD_FILES:-none}"
echo
echo "===== CHANGED TEST FILES ====="
echo "${TEST_FILES:-none}"
echo

if [ -n "$TEST_FILES" ]; then
  echo "===== TEST FILE DIFFS (inspect for weakening) ====="
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    echo "--- $f ---"
    "${DIFF_CMD[@]}" -- "$f"
    echo
  done <<< "$TEST_FILES"
fi

# Map every changed file (prod or test) to its test class, run selectively.
ALL_CLASSES=$(echo "$CHANGED_JAVA" | sed 's|.*/||; s|\.java$||; s|Test$||' | sort -u)
TEST_LIST=""
while IFS= read -r cls; do
  [ -z "$cls" ] && continue
  TFILE=$(find . -path '*/src/test*' -name "${cls}Test.java" -not -path '*/target/*' 2>/dev/null | head -1)
  [ -n "$TFILE" ] && TEST_LIST="${TEST_LIST}${cls}Test,"
done <<< "$ALL_CLASSES"
TEST_LIST="${TEST_LIST%,}"

if [ -z "$TEST_LIST" ]; then
  echo "No test counterparts found for changed files — no test run performed."
  exit 0
fi

echo "===== RUNNING: mvn test -Dtest=\"$TEST_LIST\" ====="
mvn test -Dtest="$TEST_LIST" --no-transfer-progress 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]'
