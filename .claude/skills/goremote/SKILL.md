---
name: goremote
description: Commit, push, sync DB, deploy JAR to EC2, restart, and verify
---

# Go Remote — Full Deploy Pipeline

Commit, push, sync local DB to RDS, deploy JAR to EC2, restart, and verify.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
JAR="target/ai-healthcare-1.0-SNAPSHOT.jar"
AWS_YML="application/src/main/resources/application-aws.yml"
REMOTE_DIR="/opt/aihealthcare"
LOCAL_DB_USER="admin"
LOCAL_DB_PASS="1454"
LOCAL_DB_NAME="aihealthcaredb"
DUMP_FILE="/tmp/aihealthcare_sync.sql"
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

### 3. Run tests

```bash
mvn test 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

If `BUILD FAILURE`, **STOP** and report failing tests. Do not proceed.

### 4. Commit changes

Generate a commit message from the diff summary. Include only the delta — what changed, not what exists.

```bash
git add -A
git status
```

Review staged files. **Do NOT stage** any file that could contain secrets (`.env`, credentials, keys).
If suspicious files are staged, unstage them with `git reset HEAD <file>` and alert the user.

Then commit:

```bash
git commit -m "<descriptive message of what changed>"
```

End the message with:
```
Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

### 5. Push to remote

```bash
git push origin HEAD
```

If the push fails (e.g. rejected), **STOP** and report. Do not force-push.

### 6. Build JAR

```bash
set -a && source .env && set +a && mvn package -DskipTests -q 2>&1 | tail -5
```

### 7. Sync local DB to remote RDS

Dump the local PostgreSQL database (schema + data), upload to EC2, and restore into RDS.
This makes the remote database a mirror of local.

**7a. Dump local DB:**

```bash
PGPASSWORD="1454" pg_dump -h localhost -U admin -d aihealthcaredb --clean --if-exists > /tmp/aihealthcare_sync.sql 2>&1
echo "Dump size: $(wc -c < /tmp/aihealthcare_sync.sql) bytes"
```

If `pg_dump` fails or the dump is empty, **STOP** and report. Do not proceed with an empty dump.

**7b. Upload dump to EC2:**

Use `timeout 120000` — dump can be large.

```bash
scp -i "$KEY" /tmp/aihealthcare_sync.sql "ec2-user@${IP}:/tmp/"
```

**7c. Restore on remote RDS:**

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -f /tmp/aihealthcare_sync.sql 2>&1 | tail -5 && rm /tmp/aihealthcare_sync.sql'
```

Errors like `NOTICE: table "X" does not exist, skipping` from `--clean` are expected and harmless.

### 8. Upload JAR + config to EC2

Use `timeout 600000` — JAR is ~100MB.

```bash
scp -i "$KEY" "$JAR" "ec2-user@${IP}:${REMOTE_DIR}/app.jar"
scp -i "$KEY" "$AWS_YML" "ec2-user@${IP}:${REMOTE_DIR}/"
scp -i "$KEY" deploy/static/index.html "ec2-user@${IP}:/opt/bigskylabs/static/"
```

### 9. Restart service on EC2

```bash
ssh -i "$KEY" "ec2-user@${IP}" "nohup sudo systemctl restart aihealthcare &>/dev/null &"
```

### 10. Verify — wait for startup, then health check

EC2 startup takes ~2-3 minutes (topic summary LLM calls on boot).

```bash
sleep 120 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

Should return `200`. If not, wait 60 more seconds and retry once:

```bash
sleep 60 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

If still failing, check logs:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare --no-pager -n 30'
```

Report the last 30 log lines to the user.

### 11. Clean up local dump

```bash
rm -f /tmp/aihealthcare_sync.sql
```

### 12. Report results

Report a summary to the user:

**Always include:**
- Test count (e.g., "1406 tests passed")
- Commit hash + message
- Push status
- DB sync status (dump size, restore result)
- Deploy status (JAR uploaded)
- App status (running at `https://app.bigskylabs.ai`)
- Login URL: `https://app.bigskylabs.ai/login`
- Corporate homepage: `https://bigskylabs.ai/`

**If front-end files were in the commit, include a "Changed Pages" section** using the same URL mapping table as the golocal skill, but prefixed with `https://app.bigskylabs.ai` instead of `http://localhost:8080`.

## Known issues

- **FierceHealthcare 403** — EC2 IPs are blocked by FierceHealthcare. Harvester logs error and continues.
- **SAXParseException DOCTYPE** — some RSS feeds return HTML error pages. Caught and skipped.
- **Startup takes ~2-3 minutes** — topic summary generation calls the LLM on boot.
- **File lock on mvn clean** — IntelliJ holds locks. Use `mvn package` without `clean`.
- **pg_dump NOTICE messages** — `--clean --if-exists` produces harmless "does not exist, skipping" notices.
- **Large JAR upload** — ~100MB, may take 1-2 minutes on slower connections.
