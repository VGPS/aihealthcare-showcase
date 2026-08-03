---
name: status
description: Check AIHealthcare EC2 Status
---

# Check AIHealthcare EC2 Status

Check app health, API key status, and recent logs on the EC2 instance.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
```

## Steps — run all checks and report a summary

### 1. Health check

```bash
curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

Report: `200` = healthy, `502` = app starting or down, connection error = EC2 unreachable.

### 2. Service status

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo systemctl status aihealthcare --no-pager -l' 2>/dev/null | head -15
```

### 3. API key check

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'grep -E "API_KEY|STRIPE" /opt/aihealthcare/.env | sed "s/=.\{8\}/=*****/g"'
```

Report which keys are set vs still `REPLACE_ME`.

### 4. Recent logs (last 20 lines)

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'sudo journalctl -u aihealthcare --no-pager -n 20'
```

### 5. Disk and memory

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'df -h / && echo "---" && free -h'
```

## Report format

Summarize as a brief status table:
- App: up/down (HTTP status)
- Service: active/failed
- API keys: which are set, which are missing
- Disk/memory: any warnings if >80% used
- Errors: any ERROR lines in recent logs
