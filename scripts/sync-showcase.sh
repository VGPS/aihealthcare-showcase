#!/usr/bin/env bash
# sync-showcase.sh — the ONLY sanctioned way to update the public showcase repo.
#
# Mirrors this private repo's master onto the public aihealthcare-showcase
# repo, stripping sale/outreach-strategy files BEFORE the public push, in one
# atomic sequence. A PreToolUse hook in .claude/settings.json blocks any other
# git command that touches "showcase" + push/--force, specifically so this
# script is the only path that can reach the public remote.
#
# Built 2026-11-16 after docs/Acquire_notes.md (asking-price history,
# negotiation strategy) was found live in the public repo -- the third time
# the old "remember to strip before every sync" pattern failed
# (2026-10-18, 2026-10-28, 2026-11-16).
set -euo pipefail

SHOWCASE_DIR="C:/workspaces/SpringAIClaude/aihealthcare-showcase"

# Files that must never be public. See feedback_showcase_mirror memory for
# the incident history behind each entry.
EXCLUDE_PATHS=(
  "docs/outreach/acquisition-outreach-targets.md"
  "docs/outreach/acquisition-outreach-targets-raw-research.md"
  "data_input/seed_acquisition_outreach.sql"
  "data_input/update_acquisition_contacts_tier1.sql"
  "data_input/update_acquisition_contacts_tier2.sql"
  "data_input/update_acquisition_contacts_tier3.sql"
  "data_input/update_brian_eastwood_linkedin.sql"
  "docs/outreach/AIHealthcare-Sale-Listing-Draft.md"
  "docs/outreach/acquire-notes.txt"
  "docs/outreach/FlippaNotes.txt"
  "docs/outreach/AIHealthcare-Marketing-Plan-v2.md"
  "marketing-plan.md"
  "docs/Acquire_notes.md"
  "acquire"
  "linkedin-posts/misc/MessageToAcquireInterests.txt"
)

cd "$SHOWCASE_DIR"

echo "[sync-showcase] fetching private..."
git fetch private

echo "[sync-showcase] resetting to private/master..."
git reset --hard private/master

echo "[sync-showcase] stripping excluded files..."
TO_STRIP=()
for f in "${EXCLUDE_PATHS[@]}"; do
  [ -e "$f" ] && TO_STRIP+=("$f")
done

if [ "${#TO_STRIP[@]}" -gt 0 ]; then
  git rm -r --cached --ignore-unmatch -- "${TO_STRIP[@]}" >/dev/null
  rm -rf -- "${TO_STRIP[@]}"
  git commit -m "chore: strip sale-sensitive files before public mirror sync" >/dev/null
  echo "[sync-showcase] stripped: ${TO_STRIP[*]}"
else
  echo "[sync-showcase] nothing to strip -- clean."
fi

echo "[sync-showcase] pushing to public origin..."
git push origin master --force

echo "[sync-showcase] done."
