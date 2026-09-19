---
name: goWeekendupdate
description: Generate the Saturday Weekly Intel Roundup — LLM narrative, LinkedIn/Substack/Facebook posts, deploy, and output to genTextDir
---

# Go Weekend Update — Weekly Intel Roundup Pipeline

Generates the Saturday weekly roundup post for LinkedIn, Facebook, and Substack.
Uses the `/dashboard/weekly-roundup` page to produce LLM-synthesized analytical
narrative from the past 7 days of COMPETITOR and LEGAL tier articles.

Run this every Saturday morning.

## Connection info

```
IP="100.61.13.237"
KEY="/c/Users/Administrator/.ollama/.ssh/aihealthcare-kp.pem"
SITE="https://app.bigskylabs.ai"
GEN_TEXT_DIR="C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/text"
GEN_IMAGE_DIR="C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots"
```

## Pre-flight check

Verify the app is running and the Anthropic API is accepting calls:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/dashboard/weekly-roundup"
```

Should return `200`. If not, check if the app is running:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "sudo systemctl status aihealthcare | head -10"
```

## Step 1 — Check LLM synthesis status

Verify the page is using real LLM narrative (not fallback):

```bash
ssh -i "$KEY" "ec2-user@${IP}" "curl -s http://localhost:8080/dashboard/weekly-roundup | grep -c 'Full analysis available at app.bigskylabs.ai'"
```

- Result `0` = LLM synthesis is working (good, proceed)
- Result `1` or more = fallback narrative active (API quota likely exhausted)

If fallback is active, check logs for the specific error:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "sudo journalctl -u aihealthcare --since '5 min ago' --no-pager | grep -i 'quota\|api usage\|fallback' | tail -5"
```

Report the error to the user and ask whether to proceed with fallback content or wait.

## Step 2 — Extract the generated content

The weekly roundup page at `/dashboard/weekly-roundup` generates three output blocks:
- **LinkedIn post body** — analytical narrative + hashtags (id="li-body")
- **LinkedIn first comment** — insights link + subscribe CTA (id="li-comment")
- **Substack article** — longer-form Markdown analysis (id="ss-body")

Visit `https://app.bigskylabs.ai/dashboard/weekly-roundup` in a browser or extract
content via curl:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "curl -s http://localhost:8080/dashboard/weekly-roundup" > /tmp/roundup.html
```

Parse the three content blocks from the HTML.

## Step 3 — Generate output files

Get today's date for filenames:

```bash
DATE=$(date +%Y-%m-%d)
```

### LinkedIn + Facebook post file

Write to `$GEN_TEXT_DIR/$DATE-weekly-intel-roundup-linkedin.txt`:

```
=== LINKEDIN POST BODY ===
(paste li-body content here)

=== LINKEDIN FIRST COMMENT (paste after posting) ===
(paste li-comment content here)

=== FACEBOOK POST ===
(same as LinkedIn body — Facebook has no character limit concern)

=== POSTING INSTRUCTIONS ===

LINKEDIN:
1. Copy the Post Body above
2. Go to linkedin.com → Start a post
3. Paste the body text
4. Add the infographic screenshot as the post image
5. Publish (do NOT add links in the body — kills reach)
6. Immediately paste the First Comment on your new post

FACEBOOK:
1. Copy the Facebook Post above
2. Post to your Facebook page/group
3. Add the infographic screenshot as the post image
4. The insights URL will generate an OG preview card automatically

SUBSTACK:
1. Copy the Substack Article from the separate file
2. Go to substack.com → New post
3. Title: "AI in Healthcare — Weekly Intel Roundup"
4. Subtitle: the date range
5. Paste into editor (renders Markdown automatically)
6. Add cover image (infographic screenshot)
7. Section: "Weekly Roundup"
8. Check "Send to email subscribers"
9. Publish or schedule for Saturday morning
```

### Substack article file

Write to `$GEN_TEXT_DIR/$DATE-weekly-intel-roundup-substack.txt`:

```
(paste ss-body content here — this is already Markdown formatted)
```

## Step 4 — Generate infographic screenshot

Tell the user to take a screenshot of the weekly roundup page at:
`https://app.bigskylabs.ai/dashboard/weekly-roundup`

The screenshot should capture the article preview column showing the 10 selected
articles with their LEGAL/COMPETITOR tier badges.

Save to: `$GEN_IMAGE_DIR/$DATE-weekly-intel-roundup.png`

Also copy to the static resources for the hosted insights page:
`application/src/main/resources/static/images/$DATE-weekly-intel-roundup.png`

## Step 5 — Create hosted insights page (optional)

If the user wants a hosted page with OG tags for clickable link preview cards on
social platforms, create:

`application/src/main/resources/static/insights/$DATE-weekly-intel-roundup.html`

Use the previous week's insights page as a template. Key elements:
- OG meta tags (og:title, og:description, og:image, og:url)
- Responsive layout (max-width: 1400px, width: 100%)
- Brand icon at `/images/brand-icon.png`
- The infographic image embedded
- CTA links to the live app

The insights page is already in SecurityConfig `permitAll()` via `/insights/**`.

## Step 6 — Deploy if new files were created

If any static files (insights page, images) were added, rebuild and deploy:

1. Build: `source .env && mvn -Pci verify`
2. Deploy JAR:
   ```bash
   scp -i "$KEY" target/ai-healthcare-1.1.0-SNAPSHOT.jar "ec2-user@${IP}:/opt/aihealthcare/app.jar"
   ```
3. Restart:
   ```bash
   ssh -i "$KEY" "ec2-user@${IP}" "sudo systemctl restart aihealthcare"
   ```
4. Wait and verify:
   ```bash
   sleep 40 && ssh -i "$KEY" "ec2-user@${IP}" "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/dashboard/weekly-roundup"
   ```

## Step 7 — Commit and push

Stage only the generated output files and any new static resources:

```bash
git add linkedin-posts/text/$DATE-weekly-intel-roundup-*.txt
git add linkedin-posts/shots/$DATE-weekly-intel-roundup.png
git add application/src/main/resources/static/insights/$DATE-weekly-intel-roundup.html
git add application/src/main/resources/static/images/$DATE-weekly-intel-roundup.png
```

Commit with a descriptive message:

```bash
git commit -m "Add $DATE weekly intel roundup: LinkedIn/Facebook/Substack posts

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

Push to both remotes:

```bash
git push AIHealthcare_Origin <current-branch>
git push showcase <current-branch>
```

## Step 8 — Report results

Report to the user:

```
## Weekly Intel Roundup — [Date Range]

**LLM Status:** [synthesized / fallback]
**Articles:** [count] ([legal count] legal, [competitor count] competitor)
**LinkedIn body:** [char count] / 2,900
**Substack article:** [char count]

**Output files:**
- LinkedIn/FB post: linkedin-posts/text/$DATE-weekly-intel-roundup-linkedin.txt
- Substack article: linkedin-posts/text/$DATE-weekly-intel-roundup-substack.txt
- Infographic: linkedin-posts/shots/$DATE-weekly-intel-roundup.png
- Hosted page: https://app.bigskylabs.ai/insights/$DATE-weekly-intel-roundup.html

**Next steps:**
1. Take screenshot of the article preview column
2. Post to LinkedIn (body → image → publish → comment)
3. Post to Facebook (same body + image)
4. Post to Substack (article + cover image → publish)
```

## Platform rules reminder

- **LinkedIn:** Links in post body reduce reach 40-60%. Put links in first comment only.
- **LinkedIn/Facebook images:** NOT clickable. Use the insights URL in the post for OG card.
- **Substack images:** CAN be hyperlinked to the insights page.
- **No source names or URLs** in any output. The whole point is original analysis.
- **Page is public** — no login required at /dashboard/weekly-roundup.
- **Run Pipeline link** is ADMIN-only (hidden from public visitors).

## Troubleshooting

### Anthropic API quota exhausted
Error: `"You have reached your specified API usage limits"`
- Check quota at console.anthropic.com → Settings → Limits
- Payment may not reset limit — the monthly cap is separate from balance
- Fallback narrative will render automatically until quota resets

### No articles found
If 0 LEGAL/COMPETITOR articles in past 7 days:
- Check RSS harvest ran: `ssh ... "sudo journalctl -u aihealthcare --since 'today' | grep harvest"`
- Manual trigger: log into app as admin → Pipelines → Run RSS Harvest

### SSH key not found
Key location: `/c/Users/Administrator/.ollama/.ssh/aihealthcare-kp.pem`
If host key verification fails: `ssh -o StrictHostKeyChecking=accept-new -i "$KEY" ec2-user@${IP} "echo ok"`
