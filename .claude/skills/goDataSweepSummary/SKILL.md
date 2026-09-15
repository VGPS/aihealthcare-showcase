---
name: goDataSweepSummary
description: Pull fresh data from all 6 pipeline sources and identify the strongest LinkedIn narrative hook
---

# Data Sweep Summary — Daily Pipeline Check

Pull data from all 6 BigSkyLabs pipeline sources, evaluate what's new, and recommend
the strongest narrative angle for a LinkedIn post. Stops after presenting findings —
does NOT generate the infographic or post text (use `/goDailyCheckup` for that).

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
```

## Step 1 — Pull data from all 6 sources

Check production at `https://app.bigskylabs.ai` first. For authenticated API data,
SSH into the EC2 instance and query Postgres directly.

### Source 1: Regulatory / Legislation (public)

WebFetch `https://app.bigskylabs.ai/legislation` — count total laws, note any new
enactments or upcoming effective dates in the next 30-90 days.

Also WebFetch `https://app.bigskylabs.ai/legislation/upcoming` for the timeline view.

### Source 2: Deal Signals (DB query)

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -t -c "
SELECT signal_type, title, company_name, deal_amount, detected_at::date
FROM deal_signals
ORDER BY detected_at DESC
LIMIT 25;
"'
```

Also get type breakdown for the last 7 days:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -t -c "
SELECT signal_type, COUNT(*) as cnt,
       COUNT(CASE WHEN deal_amount IS NOT NULL AND deal_amount != '\''undisclosed'\'' THEN 1 END) as with_amounts
FROM deal_signals
WHERE detected_at > NOW() - INTERVAL '\''7 days'\''
GROUP BY signal_type
ORDER BY cnt DESC;
"'
```

### Source 3: Trends (DB query)

Get rising keywords from the latest trend snapshot:

```bash
ssh -i "$KEY" "ec2-user@${IP}" "source /opt/aihealthcare/.env && PGPASSWORD=\"\$DB_PASSWORD\" psql -h \"\$DB_HOST\" -U \"\$DB_USERNAME\" -d aihealthcaredb -t -c \"
SELECT k->>'keyword' as keyword, k->>'direction' as dir, k->>'current30d' as freq30d
FROM trend_snapshots ts,
     jsonb_array_elements(ts.rising_json::jsonb) as k
WHERE ts.id = (SELECT id FROM trend_snapshots ORDER BY generated_at DESC LIMIT 1)
ORDER BY (k->>'current30d')::int DESC
LIMIT 15;
\""
```

Get fading keywords too (for contrarian angles):

```bash
ssh -i "$KEY" "ec2-user@${IP}" "source /opt/aihealthcare/.env && PGPASSWORD=\"\$DB_PASSWORD\" psql -h \"\$DB_HOST\" -U \"\$DB_USERNAME\" -d aihealthcaredb -t -c \"
SELECT k->>'keyword' as keyword, k->>'direction' as dir, k->>'current30d' as freq30d
FROM trend_snapshots ts,
     jsonb_array_elements(ts.fading_json::jsonb) as k
WHERE ts.id = (SELECT id FROM trend_snapshots ORDER BY generated_at DESC LIMIT 1)
ORDER BY (k->>'current30d')::int DESC
LIMIT 10;
\""
```

### Source 4: Wiki Contradictions (public)

WebFetch `https://app.bigskylabs.ai/wiki/contradictions` — note any new reversals
with prior claim vs new claim and detection date.

### Source 5: Company Directory (public)

WebFetch `https://app.bigskylabs.ai/directory?sort=trending` — note top trending
companies, total count, dominant sectors, and any notable signal changes.

### Source 6: Market Digest (DB query)

Get today's market-moving headlines:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -t -c "
SELECT e.headline, e.category, e.fact_classification, e.market_impact_rank
FROM market_digest_entry e
JOIN market_digest d ON e.digest_id = d.digest_id
WHERE d.digest_date = CURRENT_DATE
ORDER BY e.market_impact_rank ASC
LIMIT 15;
"'
```

If today has no digest, check yesterday:

```bash
ssh -i "$KEY" "ec2-user@${IP}" 'source /opt/aihealthcare/.env && PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -t -c "
SELECT digest_date, generated_at::date FROM market_digest ORDER BY digest_date DESC LIMIT 3;
"'
```

## Step 2 — Evaluate what's worth posting

Apply the content calendar day-mapping as a starting suggestion:
- **Monday** — Regulatory signal (FDA or CMS angle)
- **Tuesday** — Deal flow pattern (trend across deals, not a single company)
- **Wednesday** — Fading keyword (contrarian angle)
- **Thursday** — Deal flow or trend data point
- **Friday** — Company directory spotlight or reversal watch

**OVERRIDE the day suggestion if a stronger story exists.** The deciding factors (in order):
1. **Surprising pattern** — a stat that contradicts conventional wisdom
2. **Quantified trend** — a measurable shift with a before/after
3. **Notable single event** — a major FDA clearance, large funding round (>$50M), M&A deal
4. **Reversal/contradiction** — new evidence contradicting prior consensus

If NOTHING has a narrative hook today, say so. Skipping a day is better than a weak post.
The content calendar says 3-4/week, not 7.

## Step 3 — Present findings

Report a structured summary:

```
## Data Sweep Summary — [Day] [Date]

**Source 1 — Regulatory/Legislation:** [count, any new/upcoming]
**Source 2 — Deal Signals (7-day):** [count by type, notable amounts, key companies]
**Source 3 — Trends:** [top rising keywords with counts, any fading]
**Source 4 — Wiki Contradictions:** [any new reversals]
**Source 5 — Company Directory:** [total count, top trending, sector shifts]
**Source 6 — Market Digest (today):** [top headlines ranked by impact]

## Strongest Angles (ranked)

1. **[Angle name]** — [Why it's strong, what data backs it]
2. **[Angle name]** — [Why]
3. **[Angle name]** — [Why]

**Recommended:** [Angle] — [data infographic / text-only / skip today]
```

Stop here and wait for user direction. Do NOT generate infographic or post text.
