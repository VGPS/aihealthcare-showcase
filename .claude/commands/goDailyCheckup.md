# Daily LinkedIn Post Checkup

Run the daily morning data check across all 5 BigSkyLabs data sources. Identify the strongest
narrative hook, generate a LinkedIn-ready infographic HTML + post text, and open the result
for SnagIt screenshot.

## Step 1 — Pull data from all 5 sources

Fetch data from the live app at `https://app.bigskylabs.ai`. If endpoints redirect to login
(authenticated), try the local app at `http://localhost:8080`. If neither is available, tell
the user to start the app first.

Check these sources in this order (same as the content calendar's "5-minute routine"):

1. **Regulatory** — `GET /api/v1/legislation/state-laws` + `GET /dashboard/regulatory` — any new FDA clearances, CMS rules, or legislation changes?
2. **Deal Signals** — `GET /api/v1/deals` — any new M&A, funding, or partnership flagged? Check amounts and counterparties.
3. **Trends** — `GET /api/v1/trends/latest` — check rising/fading columns. Anything moved significantly?
4. **Wiki Contradictions** — `GET /wiki/contradictions` (public) — any new reversals?
5. **Company Directory** — `GET /directory` (public) — any notable new companies or signal changes?
6. **Market Digest** — `GET /api/v1/market-digest/latest` — today's market-moving headlines?

For public pages (`/directory`, `/wiki/contradictions`, `/legislation`), use WebFetch.
For authenticated API endpoints, try curl against localhost.

## Step 2 — Evaluate what's worth posting

Apply the content calendar day-mapping as a starting suggestion:
- **Monday** — Regulatory signal (FDA or CMS angle)
- **Tuesday** — Deal flow pattern (trend across deals, not a single company)
- **Wednesday** — Fading keyword (contrarian angle)
- **Thursday** — Deal flow or trend data point
- **Friday** — Company directory spotlight or reversal watch

But OVERRIDE the day suggestion if a stronger story exists. The deciding factors (in order):
1. **Surprising pattern** — a stat that contradicts conventional wisdom (e.g., "92% convergence")
2. **Quantified trend** — a measurable shift with a before/after (e.g., "+N% in 90 days")
3. **Notable single event** — a major FDA clearance, large funding round (>$50M), M&A deal
4. **Reversal/contradiction** — new evidence contradicting prior consensus (strongest engagement)

If NOTHING has a narrative hook today, say so. Skipping a day is better than a weak post.
The content calendar says 3-4/week, not 7.

## Step 3 — Present findings to user

Report what was found across all sources in a brief summary:
- What's new since yesterday
- Which angle is strongest and why
- Recommended post type (data infographic, text-only, or skip today)

Wait for user approval before generating.

## Step 4 — Generate the infographic

Use the **approved BigSkyLabs infographic template**. The reference implementation is at:
`linkedin-posts/legislation-convergence.html`

### Design spec (LOCKED — do not deviate)

**Page dimensions:** 1400px wide, auto height (landscape, SnagIt-friendly)
**Background:** `#ffffff` (white)
**Font:** Inter (Google Fonts import), weights 400/500/600/700/800
**Layout:** Horizontal flow — left-to-right, then new row. NOT top-down stacking.

**Brand block (top-left, REQUIRED on every infographic):**
- Brand icon: use `src="/images/brand-icon.png"` (absolute path — works when served by the app)
- For the local screenshot copy, use `src="brand-icon.png"` (relative — copy `brand-icon.png` to `linkedin-posts/` if not already there)
- Size: 84×84px, 10px border-radius
- Brand name: "AI in Healthcare" — Inter 27px weight 700, color `#64748b`
- Icon + name in a flex row with 14px gap

**Row 1 — Header:**
- Left: brand block, eyebrow (12px, uppercase, `#94a3b8`, 2.5px letter-spacing), h1 (48px, weight 800, `#0f172a`), subtitle (18px, `#64748b`)
- Right: 2-3 hero stat numbers (48px weight 800) with labels (12px uppercase `#94a3b8`)
- Separated by `border-bottom: 1px solid #e2e8f0`

**Row 2 — Data cards (2-4 cards in a CSS grid row):**
- Card background: `#f8fafc`, border-radius 12px, border `1px solid #e2e8f0`
- Each card has a colored top border (4px) — use blue `#2563eb`, purple `#7c3aed`, cyan `#0891b2`
- Big percentage/number: 52px weight 800, colored to match the top border
- Supporting count: 15px weight 500, `#94a3b8`
- Label: 18px weight 700, `#1e293b`
- Optional progress bar: 10px track `#e2e8f0`, filled with gradient of the card color

**Row 3 — Callout + Quote (2-column grid):**
- Left: callout card — background `#fffbeb`, border-left 4px `#f59e0b`, amber chips (`#fef3c7` bg, `#92400e` text, `1px solid #fde68a`)
- Right: quote card — background `#f8fafc`, border `1px solid #e2e8f0`, quote mark 40px `#cbd5e1`, quote text 18px weight 600 italic `#334155`

**Footer:**
- `border-top: 1px solid #e2e8f0`
- Left: `BigSkyLabs` (16px weight 700, "BigSky" in `#2563eb`, "Labs" in `#94a3b8`)
- Right: source name + URL + date (13px, `#64748b` / `#94a3b8`)

### Adapting the template to different topics

The layout is fixed. What changes per post:
- **Eyebrow text** — the data source name (e.g., "Deal Signal Tracker", "Trend Detection", "Regulatory Monitor")
- **h1 title** — the narrative hook (e.g., "The Series B Gap", "Ambient Documentation Surge")
- **Hero stats** — 2-3 headline numbers relevant to the topic
- **Data cards** — 2-4 cards with the key metrics. Adjust `grid-template-columns` if using 2 or 4 cards instead of 3
- **Callout** — the "so what" insight. Can be a list of entities (state chips, company names) or a single bold statement
- **Quote** — the pull-quote for the LinkedIn post text
- **Footer source** — the relevant app page URL

## Step 5 — Generate the post text

Write the LinkedIn post following the content calendar format:
- 1-3 punchy lines above the fold (the hook)
- 2-4 lines of data/context
- One closing question or observation
- Save to `linkedin-posts/text/YYYY-MM-DD-{topic-slug}-post.txt`

**ONE link goes in the post body — the live infographic page.** This is the clickable link
that connects the image the reader is looking at to the actual live page. Place it as the
very last line of the post text, after all insight content.

The link MUST have explanatory text telling the reader what they get when they click.
Format — two lines, pointing-hand emoji + description on line 1, bare URL on line 2:
```
👉 Explore the live data behind this analysis — trend charts, deal signals, market-moving headlines, and 318 tracked AI healthcare companies updated daily:
https://app.bigskylabs.ai/insights/YYYY-MM-DD-{topic-slug}.html
```
Adapt the description to match the specific data sources used in that day's infographic.
The hosted page at `/insights/` mirrors the screenshot image exactly, plus has CTA buttons
at the bottom linking into Market Digest, Trends, Deals, Directory, and Pricing.
Users should not need to find the first comment to reach the page.

**All other links go in the first comment:**
1. **Relevant dashboard pages** — the app pages for the data sources used (e.g., `/dashboard/market`, `/dashboard/trends`, `/dashboard/deals`)
2. **Free directory link** — `https://app.bigskylabs.ai/directory` (always — free, no signup)
3. **Signup CTA** — `https://app.bigskylabs.ai/pricing` (always last)

## Step 6 — Screenshot with Playwright

Playwright + Chromium are installed. Take an automatic screenshot — no SnagIt needed.

```bash
npx playwright screenshot \
  --viewport-size="1400,900" \
  --full-page \
  --wait-for-timeout=5000 \
  "file:///C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/YYYY-MM-DD-{topic-slug}.html" \
  "C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots/YYYY-MM-DD-{topic-slug}.png"
```

- `--viewport-size="1400,900"` matches the body width
- `--full-page` captures the entire page height
- `--wait-for-timeout=5000` waits 5s for Google Fonts to load
- Timeout the command at 60s (`timeout: 60000`)

After the screenshot:
1. Read the PNG to visually verify it rendered correctly (icon present, no broken layout)
2. Open the PNG for the user: `start "" "<path>.png"`
3. Display the post text for copy-paste
4. Report the file paths for both PNG and post text

## File naming

All outputs use the same `YYYY-MM-DD-{topic-slug}` convention — date is the differentiator.

**Two copies of the infographic HTML are generated:**

1. **Live hosted copy** (served by the app — this is the version LinkedIn viewers see):
   `application/src/main/resources/static/insights/YYYY-MM-DD-{topic-slug}.html`
   - Uses `src="/images/brand-icon.png"` (absolute path, works when served by Spring Boot)
   - Live URL: `https://app.bigskylabs.ai/insights/YYYY-MM-DD-{topic-slug}.html`
   - `/insights/**` is public (no login required)
   - **MUST be responsive** — LinkedIn traffic is heavily mobile. Key differences from screenshot copy:
     - `<meta name="viewport" content="width=device-width, initial-scale=1"/>`
     - `body { max-width: 1400px; width: 100%; margin: 0 auto; }` (NOT `width: 1400px`)
     - Media queries for `@media (max-width: 900px)` and `@media (max-width: 500px)` to stack cards/columns
   - **MUST include CTA section** between the bottom-row and footer — card grid with
     descriptions explaining what each link offers. Use absolute URLs (`https://app.bigskylabs.ai/...`).
     - **GATED pages (dashboards) MUST link to `/pricing`, NEVER to the dashboard URL.**
       Unauthenticated LinkedIn visitors would hit a login wall. All 3 CTA cards point to pricing.
     Always include these links:
     - 3 CTA cards (grid): feature descriptions (e.g., Market Digest, Trend Detection, Deal Signals)
       — each with an icon, title, and 1-line description. ALL link to `https://app.bigskylabs.ai/pricing`
     - Bottom row: `/directory` (free, no signup — this is the ONLY non-pricing link) + `/pricing` (signup CTA)
   - Use the CTA section CSS/HTML pattern from the reference infographic:
     `application/src/main/resources/static/insights/2026-09-15-ehr-ai-race.html`

2. **Local screenshot copy** (used only for Playwright screenshot — NO CTA bar):
   `linkedin-posts/YYYY-MM-DD-{topic-slug}.html`
   - Uses `src="brand-icon.png"` (relative path, works with local `file://` URLs)
   - Does NOT include the CTA bar (keeps the screenshot clean for LinkedIn image)
   - Ensure `linkedin-posts/brand-icon.png` exists (copy from `application/src/main/resources/static/images/brand-icon.png` if missing)

- Screenshot PNG: `linkedin-posts/shots/YYYY-MM-DD-{topic-slug}.png`
- Post text: `linkedin-posts/text/YYYY-MM-DD-{topic-slug}-post.txt`

**IMPORTANT:** The live hosted copy must be committed and deployed via `/goremote` before the
LinkedIn post goes live. The live URL won't work until the next deploy.

## Content calendar reference

Full calendar at: `C:\workspaces\SpringAIClaude\market-analysis-v2\LinkedInTemplates\LinkedIn Content Calendar — 2 Weeks.md`

## Standing templates (use when day-mapped topic has no data)

- **New FDA clearance** — 3 things to watch format (coverage path, De Novo vs predicate, competitor count)
- **HuggingFace model observation** — new healthcare LLMs by category
- **Sentiment/risk observation** — distribution across company corpus
- **Company directory spotlight** — free resource framing, link in body OK
