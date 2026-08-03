---
name: run
description: Run AIHealthcare locally
---

# Run AIHealthcare locally

Kill any running instance, rebuild, and start the app locally for testing.

## Steps — execute in order

### 1. Kill existing Java processes

```bash
taskkill //F //FI "IMAGENAME eq java.exe" 2>/dev/null; echo "done"
```

### 2. Build and start (skip tests for speed)

Source `.env` as OS environment variables first — Spring AI's auto-config resolves the
Anthropic API key at bean-creation time, before the `.env` property source is available.
Without this, all AI calls fail with HTTP 401.

Run in background with `timeout 120000`:

```bash
set -a && source .env && set +a && mvn spring-boot:run -DskipTests
```

### 3. Wait for startup and verify

```bash
sleep 45 && curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login
```

Should return `200`. If connection refused, wait 20 more seconds and retry — startup harvest generates topic summaries via LLM.

If still failing after 90 seconds, check the background task output for errors.

## Notes

- App runs on `http://localhost:8080`
- Uses PostgreSQL on AWS RDS (not H2) via `.env` config
- Login: `wgblackmonall@gmail.com` or any seeded user from `data.sql`
- Ctrl+C or kill Java to stop
