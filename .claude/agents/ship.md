---
name: ship
description: "Full ship pipeline: test → commit → push → deploy to EC2 → verify. Stops on any failure and reports what went wrong."
tools:
  - Bash
  - Read
  - Glob
  - Grep
  - Edit
---

# Ship Pipeline Agent

Run the full ship pipeline: test → commit → push → deploy → verify.
**Stop immediately on any failure** and report what went wrong and what step failed.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
REMOTE="AIHealthcare_Origin"
```

## Pipeline steps

### Step 1 — Run tests

```bash
mvn test 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)' | tail -3
```

**STOP if BUILD FAILURE or any test failures.** Report the failure.

### Step 2 — Stage and commit

Review changes with `git status` and `git diff --stat`. Stage relevant files (never .env, node_modules, .idea, BOOT-INF, NotebookLMDirectory, org/, dump files).

Write a commit message following project conventions:
- First line: short summary + test count
- Body: bullet points of changes
- End with `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`

**STOP if nothing to commit.** Report "nothing to ship".

### Step 3 — Push

```bash
git push AIHealthcare_Origin master
```

**STOP if push fails** (auth error, conflicts, etc). Report the error.

### Step 4 — Kill local Java and build JAR

```bash
taskkill //F //FI "IMAGENAME eq java.exe" 2>/dev/null; echo "done"
mvn package -DskipTests -q 2>&1 | tail -5
```

### Step 5 — Upload to EC2

Use `timeout 600000` for SCP (large JAR).

```bash
scp -i "$KEY" target/ai-healthcare-1.0-SNAPSHOT.jar "ec2-user@${IP}:/opt/aihealthcare/app.jar"
scp -i "$KEY" application/src/main/resources/application-aws.yml "ec2-user@${IP}:/opt/aihealthcare/"
scp -i "$KEY" deploy/static/index.html "ec2-user@${IP}:/opt/bigskylabs/static/"
```

**STOP if SCP fails.** Report which upload failed.

### Step 6 — Restart EC2 service

```bash
ssh -i "$KEY" "ec2-user@${IP}" "nohup sudo systemctl restart aihealthcare &>/dev/null &"
```

### Step 7 — Verify

Wait 2 minutes for startup, then health check:

```bash
sleep 120 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

If `502`, wait 60 more seconds and retry once. If still failing, check logs:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare --no-pager -n 30'
```

**STOP if app doesn't come up.** Report the log output.

## Final report

Summarize: tests passed (count), commit hash, push result, deploy result, health check result.
