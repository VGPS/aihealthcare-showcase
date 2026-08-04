---
name: test-run
description: Run Tests
---

# Test Run — Selective or Full Test Execution

Run tests for AIHealthcare. By default, runs only tests relevant to recently changed files.
Use `full` argument to run the entire suite.

## Modes

### Selective (default) — run only relevant tests

1. **Find changed files:**

```bash
git diff --name-only HEAD -- '*.java' 2>/dev/null
git diff --name-only --cached -- '*.java' 2>/dev/null
git ls-files --others --exclude-standard -- '*.java' 2>/dev/null
```

2. **Map each changed production file to its test counterpart:**
   - `src/main/java/**/FooService.java` → `FooServiceTest.java`
   - `src/main/java/**/FooController.java` → `FooControllerTest.java`
   - `src/main/java/**/FooAdapter.java` → `FooAdapterTest.java`
   - Domain records/enums: find test classes that import them

3. **Run only those test classes:**

```bash
mvn test -Dtest="TestClassA,TestClassB,TestClassC" 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

4. **If no test counterparts found**, report "No test counterparts for changed files — skipping tests."

5. **Report**: which tests ran, pass/fail count.

### Full — run all tests

Triggered when the user says `full`, `all`, or explicitly asks for the full suite.

```bash
mvn test 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

Report total test count and pass/fail status.

## Notes

- Never run full suite by default — it takes 30+ minutes
- Template, CSS, or YAML-only changes do not require tests unless a controller also changed
- If a test fails, report the failing class, method, and a brief explanation
