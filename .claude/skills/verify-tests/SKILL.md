---
name: verify-tests
description: Verify that changed tests weren't weakened to force a pass, rather than the underlying bug actually being fixed. Use after any refactor, bug fix, or edit that touches test files — reads the diff of changed test files against the weakening patterns catalog, runs the mapped tests, and reports a PASS/FAIL verdict with evidence attached.
---

# Verify Tests — Diff-Weakening Check

Confirms a change didn't quietly loosen a test to make it pass instead of fixing
the underlying behavior. This is the one check `mvn test` alone can't do — green
tests don't prove a test still asserts what it used to.

This complements, not replaces, the project's existing gates: the `test-run`
skill and the repo's `Stop` hook already handle "did the mapped tests pass."
This skill's only job is the diff-judgment step neither of those can do
mechanically.

## Procedure

1. Run the check script to gather evidence:
   ```bash
   bash .claude/skills/verify-tests/check.sh        # uncommitted + staged changes
   bash .claude/skills/verify-tests/check.sh last   # most recent commit instead
   ```
   This prints: changed production files vs. changed test files, the full diff
   of every changed test file, and a selective `mvn test` run for the mapped
   test classes.

2. Read every test-file diff the script printed. Compare each change against
   the weakening patterns catalog in `reference.md` (loosened assertions,
   removed/widened exception checks, widened tolerances, disabled tests,
   tautological mocks, relaxed `verify()`, reduced parameterized cases, etc.).

3. For every changed test file, state one of:
   - **No weakening detected** — assertions are equal or stricter than before.
   - **Weakening detected** — quote the before/after diff lines and name which
     pattern from `reference.md` it matches.

4. Report one verdict for the whole change:
   - **PASS** — tests ran green AND no test file was weakened.
   - **FAIL** — either the test run didn't come back clean, or a test was
     weakened (even if it's green now).

   Always show the evidence backing the verdict — the test run output and the
   quoted diff lines. Never state PASS/FAIL without it.

## Notes

- If no test files changed, say so explicitly and just report whether the
  mapped tests for the changed production files still pass.
- A test being loosened because the production contract genuinely changed
  on purpose is not weakening — but say so explicitly and point to what in
  the change justifies it, rather than silently passing it through.
