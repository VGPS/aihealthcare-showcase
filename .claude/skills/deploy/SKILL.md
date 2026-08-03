---
name: deploy
description: Deploy AIHealthcare to AWS EC2
---

# Deploy AIHealthcare to AWS EC2

Build, upload, restart, and verify the app on EC2. Stop on any failure.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
JAR="target/ai-healthcare-1.0-SNAPSHOT.jar"
AWS_YML="application/src/main/resources/application-aws.yml"
REMOTE_DIR="/opt/aihealthcare"
```

## Steps — execute in order

### 1. Kill local Java (so build works)

```bash
taskkill //F //FI "IMAGENAME eq java.exe" 2>/dev/null; echo "done"
```

### 2. Build JAR

Source `.env` so API keys are available during build (Spring AI resolves them at compile-time config):

```bash
set -a && source .env && set +a && mvn package -DskipTests -q 2>&1 | tail -5
```

If `mvn clean` fails due to file lock, use `mvn package -DskipTests` (without clean).

### 3. Upload JAR + config + static page

Run with `timeout 600000` — the JAR is large.

```bash
scp -i "$KEY" "$JAR" "ec2-user@${IP}:${REMOTE_DIR}/app.jar"
scp -i "$KEY" "$AWS_YML" "ec2-user@${IP}:${REMOTE_DIR}/"
scp -i "$KEY" deploy/static/index.html "ec2-user@${IP}:/opt/bigskylabs/static/"
```

### 4. Restart service

The restart command may hang (systemd waits for old process). Use background approach:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "nohup sudo systemctl restart aihealthcare &>/dev/null &"
```

### 5. Verify — wait ~2 minutes for startup harvest, then check

```bash
sleep 120 && curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

Should return `200`. If `502`, wait 60 more seconds and retry — the startup harvest calls LLMs for topic summaries.

### 6. Tail logs (optional, if user asks or if health check fails)

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare --no-pager -n 30'
```

## Known issues

- **FierceHealthcare 403** — expected, EC2 IPs are blocked. Harvester logs error and continues.
- **SAXParseException DOCTYPE** — some RSS feeds return HTML error pages. Caught and skipped.
- **Startup takes ~2-3 minutes** — topic summary generation calls the LLM for each topic on first boot.
- **File lock on mvn clean** — IntelliJ holds locks on `target/classes`. Skip clean or close IntelliJ.

## URLs

- Corporate homepage: `https://bigskylabs.ai/`
- App login: `https://app.bigskylabs.ai/login`
