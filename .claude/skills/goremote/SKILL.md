---
name: goremote
description: Commit, push, deploy JAR to EC2, restart, and verify
---

# Go Remote — Lean Deploy Pipeline

Commit, push, deploy JAR to EC2, restart, and verify. No DB sync — EC2 crons populate
their own data. DB sync is a separate manual step if schema changes require it.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
JAR="target/ai-healthcare-1.0-SNAPSHOT.jar"
AWS_YML="application/src/main/resources/application-aws.yml"
REMOTE_DIR="/opt/aihealthcare"
```

## Steps — execute in order, stop on any failure

### 1. Kill local Java

```bash
taskkill //F //FI "IMAGENAME eq java.exe" 2>/dev/null; echo "done"
```

### 2. Compile check

```bash
mvn compile -q 2>&1
```

If compilation errors appear, **STOP** and report them. Do not proceed.

### 3. Run selective tests

Only run tests for changed files — NOT the full suite.

Find changed Java files with `git diff --name-only HEAD -- '*.java'`, map each to its test
counterpart (e.g. `FooService.java` → `FooServiceTest.java`), then run:

```bash
mvn test -Dtest="TestClassA,TestClassB" 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

If no test counterparts exist, skip testing.

If `BUILD FAILURE`, **STOP** and report failing tests. Do not proceed.

### 4. Commit changes

Generate a commit message from the diff summary. Include only the delta — what changed, not what exists.

Stage only project-relevant files (`.java`, `.html`, `.yml`, `.css`, `.md` under project dirs).
**Never stage** `.env`, credentials, `NotebookLMDirectory/`, `BOOT-INF/`, `.idea/`, `node_modules/`,
dump files, temp files, or `org/` (decompiled Spring classes). Use explicit file paths:

```bash
git add <file1> <file2> ...
git status
```

Review staged files before committing.

### 4a. Update README before committing

Before running `git commit`, update `README.md` to reflect this deploy's changes:

- **New pages / endpoints** — add any new Thymeleaf pages or REST endpoints to the relevant table
- **Test count** — update the test count line (e.g. "1,533+ tests") to match the current suite
- **Feature list** — add a bullet or section entry for any significant new feature shipped in this commit
- **Architecture notes** — update module/dependency notes only if the architecture genuinely changed

Keep edits minimal and accurate — only document what this commit actually added. Do not rewrite
existing sections or add speculative content.

After editing README.md, stage it with the rest of the commit:

```bash
git add README.md
```

Then commit everything together:

```bash
git commit -m "<descriptive message of what changed>"
```

End the message with:
```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

### 5. Push to private repo

```bash
git push AIHealthcare_Origin master
```

If the push fails (e.g. rejected), **STOP** and report. Do not force-push.

### 5a. Push to showcase (public) repo — ALWAYS

The showcase at `C:/workspaces/SpringAIClaude/aihealthcare-showcase` is kept in sync after
every private push. Do this step before building the JAR.

**Resume sync** — always copy if `docs/William_Blackmon_Resume.docx` is present:
```bash
cp C:/workspaces/SpringAIClaude/AIHealthcare/docs/William_Blackmon_Resume.docx \
   C:/workspaces/SpringAIClaude/aihealthcare-showcase/William_Blackmon_Resume.docx
```

Commit and push in the showcase repo:
```bash
cd C:/workspaces/SpringAIClaude/aihealthcare-showcase
git add William_Blackmon_Resume.docx
git diff --cached --quiet || git commit -m "<same short summary as private commit>"
git push origin master
cd C:/workspaces/SpringAIClaude/AIHealthcare
```

(`git diff --cached --quiet || git commit` skips the commit if nothing changed, so you
never get an empty commit even when the resume hasn't changed.)

### 6. Build JAR

```bash
set -a && source .env && set +a && mvn package -DskipTests -q 2>&1 | tail -5
```

### 7. Upload JAR + config to EC2

Use `timeout 600000` — JAR is ~100MB.

```bash
scp -i "$KEY" "$JAR" "ec2-user@${IP}:${REMOTE_DIR}/app.jar"
scp -i "$KEY" "$AWS_YML" "ec2-user@${IP}:${REMOTE_DIR}/"
scp -i "$KEY" deploy/static/index.html "ec2-user@${IP}:/opt/bigskylabs/static/"
```

### 8. Restart service on EC2

```bash
ssh -i "$KEY" "ec2-user@${IP}" "nohup sudo systemctl restart aihealthcare &>/dev/null &"
```

### 9. Verify — wait for startup, then health check

EC2 startup is fast now (startup harvest is disabled on AWS profile).

```bash
sleep 30 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

Should return `200`. If not, wait 30 more seconds and retry once:

```bash
sleep 30 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

If still failing, check logs:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare --no-pager -n 30'
```

Report the last 30 log lines to the user.

### 10. Report results

Report a summary to the user:

**Always include:**
- Test count (e.g., "18 selective tests passed")
- Commit hash + message
- Push status
- Deploy status (JAR uploaded)
- App status (running at `https://app.bigskylabs.ai`)
- Login URL: `https://app.bigskylabs.ai/login`
- Corporate homepage: `https://bigskylabs.ai/`

**If front-end files were in the commit, include a "Changed Pages" section** using the same URL mapping table as the golocal skill, but prefixed with `https://app.bigskylabs.ai` instead of `http://localhost:8080`.

## DB Sync (manual, only when needed)

Only sync the DB when schema changes (JPA entity changes) or seed data (`data.sql`) changed.
To sync manually:

```bash
PGPASSWORD="1454" pg_dump -h localhost -U admin -d aihealthcaredb --clean --if-exists > /tmp/aihealthcare_sync.sql
scp -i "$KEY" /tmp/aihealthcare_sync.sql "ec2-user@${IP}:/tmp/"
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -f /tmp/aihealthcare_sync.sql 2>&1 | tail -5 && rm /tmp/aihealthcare_sync.sql'
rm -f /tmp/aihealthcare_sync.sql
```

## Known issues

- **FierceHealthcare 403** — EC2 IPs are blocked by FierceHealthcare. Harvester logs error and continues.
- **SAXParseException DOCTYPE** — some RSS feeds return HTML error pages. Caught and skipped.
- **File lock on mvn clean** — IntelliJ holds locks. Use `mvn package` without `clean`.
- **Large JAR upload** — ~100MB, may take 1-2 minutes on slower connections.
