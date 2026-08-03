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

### 2. Run tests

```bash
mvn test 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)' | tail -3
```

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

### 5. Push

```bash
git push AIHealthcare_Origin master
```

Note: the remote is named `AIHealthcare_Origin`, not `origin`.
