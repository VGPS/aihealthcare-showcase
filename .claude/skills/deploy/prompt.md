# Deploy AIHealthcare to AWS EC2

Deploy the Spring Boot JAR to the EC2 instance at `100.61.13.237`, restart the service, and tail logs.

## Prerequisites (one-time setup — already done)
- EC2 provisioned via `deploy/setup-ec2.sh` (Java 17, Nginx, systemd service)
- RDS PostgreSQL at `aihealthcare-db.ci7yogem2p0o.us-east-1.rds.amazonaws.com`
- `/opt/aihealthcare/.env` on EC2 contains `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, and all API keys
- SSH key: `C:/workspaces/SpringAIClaude/N_VaKeyPair.pem`

## Deploy steps

Execute the following steps in order using Bash tool calls. Default values shown — accept overrides from user if provided.

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
JAR="target/ai-healthcare-1.0-SNAPSHOT.jar"
AWS_YML="application/src/main/resources/application-aws.yml"
REMOTE_DIR="/opt/aihealthcare"
```

### Step 1 — Build JAR (skip if user says files are already in target)

```bash
cd "C:\workspaces\SpringAIClaude\AIHealthcare" && mvn package -DskipTests -q && echo "BUILD_OK"
```

### Step 2 — Upload JAR

```bash
scp -i "$KEY" -o StrictHostKeyChecking=no "$JAR" "ec2-user@${IP}:${REMOTE_DIR}/app.jar"
```

### Step 3 — Upload AWS config

```bash
scp -i "$KEY" -o StrictHostKeyChecking=no "$AWS_YML" "ec2-user@${IP}:${REMOTE_DIR}/"
```

### Step 4 — Restart service

```bash
ssh -i "$KEY" -o StrictHostKeyChecking=no "ec2-user@${IP}" 'sudo systemctl restart aihealthcare && sleep 5 && sudo systemctl status aihealthcare --no-pager'
```

### Step 5 — Tail logs (wait ~35s for Spring Boot startup, then show last 50 lines)

```bash
sleep 35 && ssh -i "$KEY" -o StrictHostKeyChecking=no "ec2-user@${IP}" 'sudo journalctl -u aihealthcare -n 50 --no-pager'
```

## Known issues and fixes applied

### data.sql ALTER TABLE removed
`data.sql` previously contained `ALTER TABLE wiki_source_refs ALTER COLUMN ... TYPE TEXT` migration statements. These failed on AWS because all tables are owned by `postgresadmin` (the RDS master user), not `admin` (the app user). Those lines were removed — the columns are already TEXT from the dump restore.

### PgVectorStore schema init disabled
`application-aws.yml` sets `spring.ai.vectorstore.pgvector.initialize-schema: false` to prevent Spring AI from attempting `CREATE INDEX` on the `vector_store` table at startup. The table and HNSW index already exist from the dump restore; the app user (`admin`) doesn't own them and cannot ALTER them.

### FierceHealthcare 403
Expected — FierceHealthcare blocks EC2 server IPs. The harvester logs an error and continues; this does not affect startup or other feeds.

## Verify success

- `Active: active (running)` in systemd status
- Spring Boot banner and `Starting AiHealthcareApplication` visible in logs
- No `FAILURE` or `exited` lines after the restart timestamp
- Schedulers firing (feed harvest logs visible)

## Live log stream (optional)

```bash
ssh -i "$KEY" -o StrictHostKeyChecking=no "ec2-user@${IP}" 'sudo journalctl -u aihealthcare -f --no-pager'
```
(Ctrl+C to stop)
