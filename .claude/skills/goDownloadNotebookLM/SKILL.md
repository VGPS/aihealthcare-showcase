---
name: goDownloadNotebookLM
description: Sync NotebookLMDirectory from EC2 to local machine
---

# Sync NotebookLMDirectory from EC2 → Local

EC2 runs `ResearchHarvestScheduler` daily (06:00 & 12:00 UTC) and writes article files
and daily summaries to `/opt/aihealthcare/NotebookLMDirectory`. This skill pulls those
files down to the local `NotebookLMDirectory` so the corpus stays current for manual
Google NotebookLM uploads.

The local app only populates `NotebookLMDirectory` while a local JVM is running. When the
local app is dark (dev machine off/sleeping), EC2 keeps harvesting — run this skill to
close the gap.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
LOCAL_DIR="C:/workspaces/SpringAIClaude/AIHealthcare/NotebookLMDirectory"
REMOTE_DIR="/opt/aihealthcare/NotebookLMDirectory"
```

## Steps — execute in order

### 1. Report current local state

```bash
echo "Local article files: $(ls "$LOCAL_DIR"/*.txt 2>/dev/null | wc -l)"
echo "Local summaries: $(ls "$LOCAL_DIR"/summaries/ 2>/dev/null | wc -l) files, newest: $(ls -t "$LOCAL_DIR"/summaries/*.txt 2>/dev/null | head -1 | xargs basename 2>/dev/null || echo none)"
```

### 2. Report EC2 state

```bash
ssh -i "$KEY" "ec2-user@${IP}" \
  'echo "EC2 article files: $(ls /opt/aihealthcare/NotebookLMDirectory/*.txt 2>/dev/null | wc -l)"; echo "EC2 summaries newest: $(ls -t /opt/aihealthcare/NotebookLMDirectory/summaries/*.txt 2>/dev/null | head -1 | xargs basename 2>/dev/null || echo none)"'
```

Report both counts to the user before proceeding.

### 3. Sync summaries directory

Individual scp of the summaries dir — small number of files, fast.

```bash
scp -i "$KEY" "ec2-user@${IP}:${REMOTE_DIR}/summaries/*" "$LOCAL_DIR/summaries/"
echo "Summaries synced."
```

### 4. Tar article files on EC2

Tarballing on EC2 first avoids the scp-3000-individual-files timeout problem.

```bash
ssh -i "$KEY" "ec2-user@${IP}" \
  'cd /opt/aihealthcare/NotebookLMDirectory && tar -czf /tmp/notebooklm_articles.tar.gz *.txt && ls -lh /tmp/notebooklm_articles.tar.gz'
```

### 5. Download tar

```bash
scp -i "$KEY" "ec2-user@${IP}:/tmp/notebooklm_articles.tar.gz" "/tmp/notebooklm_articles.tar.gz"
echo "Download complete."
```

### 6. Extract — no-clobber (never overwrite files already present locally)

```bash
cd "$LOCAL_DIR" && tar -xzf /tmp/notebooklm_articles.tar.gz --keep-old-files 2>/dev/null; echo "Extract done."
```

### 7. Clean up tmp files

```bash
rm /tmp/notebooklm_articles.tar.gz
ssh -i "$KEY" "ec2-user@${IP}" 'rm /tmp/notebooklm_articles.tar.gz && echo "EC2 tmp cleaned"'
```

### 8. Report results

```bash
echo "Local article files after sync: $(ls "$LOCAL_DIR"/*.txt 2>/dev/null | wc -l)"
echo "Local summaries after sync: $(ls "$LOCAL_DIR"/summaries/ 2>/dev/null | wc -l) files"
echo "Newest summary: $(ls -t "$LOCAL_DIR"/summaries/*.txt 2>/dev/null | head -1 | xargs basename 2>/dev/null || echo none)"
```

Report: before count, after count, delta, newest summary date.

## Notes

- `--keep-old-files` means EC2 files never overwrite local ones — safe to run repeatedly.
- Summaries include both daily research summaries (`yyyy_MM_dd.{txt,html}`) and monthly
  market intelligence reports (`healthcare_ai_market_intelligence_*.html`).
- EC2 crons run at 06:00 & 12:00 UTC — run this skill after those times to get the latest.
- The `NotebookLMDirectory` path is relative to the app's working directory on both machines:
  - Local: `C:/workspaces/SpringAIClaude/AIHealthcare/NotebookLMDirectory`
  - EC2: `/opt/aihealthcare/NotebookLMDirectory`
