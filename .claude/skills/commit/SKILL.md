---
name: commit
description: Commit and Push AIHealthcare changes
---

# Commit and Push AIHealthcare changes

Stage changes, commit with a descriptive message, and push to GitHub.

## Steps — execute in order

### 1. Review what changed

```bash
git status -s -- '*.java' '*.html' '*.yml' '*.sql' '*.css' '*.txt' '*.md' | grep -v '^?? node_modules' | grep -v '^?? NotebookLM' | grep -v '^?? BOOT-INF' | grep -v '^?? org/'
```

```bash
git diff --stat
```

### 2. Run selective tests

Only run tests for changed files — NOT the full suite.

Find changed Java files with `git diff --name-only HEAD -- '*.java'`, map each to its test
counterpart (e.g. `FooService.java` → `FooServiceTest.java`), then run:

```bash
mvn test -Dtest="TestClassA,TestClassB" 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

If no test counterparts exist, skip testing.

**STOP if tests fail.** Report the failure and do not commit.

### 3. Stage relevant files

Stage modified and new files that are part of the feature. **Never stage:**
- `.env`, credentials, API keys
- `node_modules/`, `BOOT-INF/`, `.idea/`
- `NotebookLMDirectory/`, dump files, temp files
- `org/` (decompiled Spring classes)

### 4. Commit

Write a commit message that describes the delta since the last commit. Follow this project's style:
- First line: short summary + test count (e.g., "Feature name — 1380 tests")
- Body: bullet points of what changed
- End with: `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`

Use HEREDOC format for the message.

### 5. Push to private repo

```bash
git push AIHealthcare_Origin master
```

Note: the remote is named `AIHealthcare_Origin`, not `origin`.

### 6. Push to showcase (public) repo — ALWAYS

The showcase repo at `C:/workspaces/SpringAIClaude/aihealthcare-showcase` is always kept
in sync. After every push to the private repo, also push to the public showcase.

**Sync the resume** if `docs/William_Blackmon_Resume.docx` changed in this commit:
```bash
cp C:/workspaces/SpringAIClaude/AIHealthcare/docs/William_Blackmon_Resume.docx \
   C:/workspaces/SpringAIClaude/aihealthcare-showcase/William_Blackmon_Resume.docx
```

Then commit and push in the showcase repo with a matching summary message:
```bash
cd C:/workspaces/SpringAIClaude/aihealthcare-showcase
git add William_Blackmon_Resume.docx   # and any other files you synced
git commit -m "<same summary as private repo commit>"
git push origin master
cd C:/workspaces/SpringAIClaude/AIHealthcare
```

If no showcase-relevant files changed (resume, README, domain layer), still note in the
report that the showcase is current (no sync needed this commit).
