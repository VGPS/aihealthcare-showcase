# AI in Healthcare — User Manual

> Version 1.0 · Last updated: 2026-10-05
>
> This manual covers every page in the application, organized exactly as the navigation menu appears. Each entry includes the menu label (so you can find it while browsing the app), the URL, who can access it, and what you can do there. For full technical detail see the linked spec files in `docs/specs/`.

---

## Table of Contents

| Group | Page | Access |
|---|---|---|
| — | [Dashboard](#dashboard) | All tiers |
| — | [Tour](#tour) | Public |
| **Content** | [Claim Tracker](#claim-tracker) | FREE (5) · SUBSCRIBER+ |
| **Content** | [Deal Signals](#deal-signals) | FREE (10) · SUBSCRIBER+ |
| **Content** | [Market Digest](#market-digest) | SUBSCRIBER+ |
| **Content** | [Market Enrichment](#market-enrichment) | SUBSCRIBER+ |
| **Content** | [Market History](#market-history) | SUBSCRIBER+ |
| **Content** | [News Listing](#news-listing) | All tiers |
| **Content** | [Trend History](#trend-history) | FREE (4 snaps) · SUBSCRIBER+ |
| **Content** | [Trends](#trends) | FREE (top 5) · SUBSCRIBER+ |
| **Research** | [AI Search](#ai-search) | SUBSCRIBER+ |
| **Research** | [Find Articles](#find-articles) | All tiers |
| **Research** | [Framework Analysis](#framework-analysis) | Public (overview) · SUBSCRIBER+ (detail) |
| **Research** | [Intel Reports](#intel-reports) | SUBSCRIBER+ |
| **Research** | [Research Runs](#research-runs) | All tiers |
| **Research** | [Vendor Compare](#vendor-compare) | All tiers |
| **Legal** | [Legal Timeline](#legal-timeline) | All tiers |
| **Legal** | [Legal Trends](#legal-trends) | FREE (top 3) · SUBSCRIBER+ |
| **Legal** | [Regulatory](#regulatory) | FREE (5) · SUBSCRIBER+ |
| **Legal** | [Legislation](#legislation) | Public (index) · All tiers (detail) |
| **Reference** | [Clinical Trials](#clinical-trials) | FREE (5) · SUBSCRIBER+ |
| **Reference** | [Companies](#companies) | All tiers |
| **Reference** | [Company Directory](#company-directory) | Public |
| **Reference** | [Relationships](#relationships) | All tiers |
| **Reference** | [Sentiment & Risk](#sentiment--risk) | FREE (5) · SUBSCRIBER+ |
| **Reference** | [Wiki](#wiki) | Public (index) · All tiers (detail) |
| **Account** | [Social Posts](#social-posts) | SUBSCRIBER+ |
| **Account** | [Post Drafts](#post-drafts) | SUBSCRIBER+ |
| **Account** | [Weekly Roundup](#weekly-roundup) | All tiers |
| **Account** | [Watchlist](#watchlist) | SUBSCRIBER+ |
| **Account** | [Profile](#profile) | All tiers |
| **Account** | [Webhooks](#webhooks) | All tiers |
| **Account** | [Pricing](#pricing) | Public |
| **Account** | [Developer](#developer) | All tiers |
| **Account** | [Pipelines](#pipelines) _(Admin)_ | ADMIN |
| **Account** | [Admin](#admin) _(Admin)_ | ADMIN |
| **Account** | [Newsletter Runs](#newsletter-runs) _(Admin)_ | ADMIN |
| **Account** | [Document Library](#document-library) _(Admin)_ | ADMIN |
| **Account** | [Wiki Gaps](#wiki-gaps) _(Admin)_ | ADMIN |
| **Account** | [SSO Providers](#sso-providers) _(Admin)_ | ADMIN |
| **Account** | [Outreach CRM](#outreach-crm) _(Admin)_ | ADMIN |
| **Enterprise** | [Data Console](#data-console) | ENTERPRISE+ |
| **Enterprise** | [Intelligence Console](#intelligence-console) | ENTERPRISE+ / ADMIN |
| — | [Login](#login) | Public |
| — | [Register](#register) | Public |
| — | [Privacy Policy](#privacy-policy) | Public |

---

## Subscription Tiers — Quick Reference

| Tier | How you get it | Key capabilities |
|---|---|---|
| **PUBLIC** | No account needed | Company Directory index, Legislation index, Wiki index, Pricing, Privacy, Tour |
| **FREE** | Default after DEMO expires | Dashboard, News, limited archives, top-5 subset of gated pages |
| **DEMO** | Self-registration at `/register` | Full access for 7 days; degrades to FREE at expiry |
| **SUBSCRIBER** | Stripe checkout ($49/mo) | Full access to all content and research pages |
| **ENTERPRISE** | Admin-assigned or SAML SSO | SUBSCRIBER + Data Console + Intelligence Console |
| **ADMIN** | Admin-assigned | Everything, all limits removed, admin pages unlocked |

> **Note:** DEMO expiration is passive — your account tier stays `DEMO` in the database but is treated as FREE by every page after the 7-day window. No upgrade is needed to pay; use the **Pricing** page.

---

## Dashboard

| Field | Detail |
|---|---|
| **Menu label** | Dashboard (top-level link) |
| **URL** | `/dashboard` |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | Articles: daily 04:00 UTC · Regulatory: daily 04:30 UTC · Trends: weekly Sunday 08:00 UTC |

The main landing page after login. Shows four widgets: **Today's Headlines** (top 8 AI-in-healthcare articles from the last 7 days), **Trending Keywords** (top 5 rising signals from the latest trend snapshot), **Regulatory Watch** (3 most recent FDA/CMS events), and **Legal Pulse** (3 most recent AI-healthcare legal articles). If you have a configured Watchlist, a fifth widget appears with recent keyword or company matches.

You can also search the article corpus directly from this page using the `?q=` URL parameter or the inline search bar — results appear below the widgets without navigating away.

**Sub-pages:** `/dashboard/articles?topic=` — full article list for a single topic (no date limit).

---

## Tour

| Field | Detail |
|---|---|
| **Menu label** | Tour (top-level link) |
| **URL** | `/tour` |
| **Access** | Public (no login required) |
| **Data refreshes** | Static content |

A guided walkthrough of the application's capabilities for prospective users and evaluators. Share-friendly — no login is required to view it.

---

## Content

The **Content** group is the primary day-to-day reading surface. It covers the AI-in-healthcare news feed, deal tracking, market intelligence, and trend analysis.

---

### Claim Tracker

| Field | Detail |
|---|---|
| **Menu label** | Content → **Claim Tracker** |
| **URL** | `/dashboard/claims` |
| **Access** | FREE (top 5 claims) · SUBSCRIBER / DEMO / ADMIN (all claims) |
| **Data refreshes** | After each article harvest (via pipeline cascade) · Manual trigger on Pipelines page |

Track specific factual claims made by frontier AI companies (OpenAI, Anthropic, Google, Meta, Microsoft, NVIDIA, etc.) about their own products and capabilities. Each claim is extracted from harvested articles by Claude and rated:

| Verdict | Meaning |
|---|---|
| **Evidence-Backed** | Peer-reviewed or independently verified |
| **Alleged / Unverified** | Stated by the company without supporting citation |
| **Marketing Hype** | Superlative language with no measurable backing |
| **Contradicted** | Conflicts with other sourced evidence |
| **Retracted** | The company has since walked it back |

Filter by company, claim type (Capability / Safety / Benchmark / Timeline / Regulatory / Partnership), or verdict. Each row links to the source article.

**Sub-pages:** Claim detail at `/dashboard/claims/{claimId}` — full evidence notes, source URL, and detected date.

> **Spec reference:** `docs/specs/content.md` (via pipeline integration), CLAUDE.md CT-1 slice

---

### Deal Signals

| Field | Detail |
|---|---|
| **Menu label** | Content → **Deal Signals** |
| **URL** | `/dashboard/deals` |
| **Access** | FREE (10 signals) · SUBSCRIBER / DEMO / ADMIN (100 signals) |
| **Data refreshes** | Every 6 hours (standalone cron) + post-harvest cascade |

Automatically detected deal activity in the AI-in-healthcare sector — acquisitions, partnerships, funding rounds, licensing agreements, and clinical integrations. Each signal shows the company, deal type, extracted amount (when available), counterparty (when available), and a one-sentence LLM summary. A filter bar at the top narrows by deal type.

The detail page cross-references the deal against three other data sources: the company's current **Sentiment Score**, its **Framework Analysis** competitive position, and any related **Regulatory Events** — giving you a fuller picture of what the deal means.

**Sub-pages:** Deal detail at `/dashboard/deals/{signalId}` — cross-reference cards for sentiment, framework, regulatory context, and company profile.

> **Spec reference:** CLAUDE.md DS-1 slice

---

### Market Digest

| Field | Detail |
|---|---|
| **Menu label** | Content → **Market Digest** |
| **URL** | `/dashboard/market` |
| **Access** | SUBSCRIBER / DEMO / ADMIN (FREE: restricted) |
| **Data refreshes** | Daily 12:00 UTC (noon) |

The daily ranked digest of market-moving AI-in-healthcare news. Each entry is classified by Claude as either **Confirmed** (verified facts) or **Speculative** (analyst opinion, unconfirmed reports) and ranked 1–5 by market impact. Impact assessment cards break down each entry across dimensions: Revenue, Earnings, Valuation, Investor Sentiment, and Future Growth. Affected company chips show ticker symbols with real-time Alpaca quote data when available.

Entries that meet the qualifying bar appear in the digest: earnings reports, regulatory actions, M&A, major partnerships, and funding rounds over $50M. Sub-threshold items are still persisted but do not trigger email alerts.

| Category | Qualifies? |
|---|---|
| EARNINGS | Always |
| REGULATORY | Always |
| FUNDING | Only if amount > $50M (strictly) |
| M&A | Always |
| MAJOR_PARTNERSHIP | Always |
| Other categories | Never |

**Sub-pages:** Market weekly rollup at `/dashboard/market/weekly` — 7-day narrative synthesis. Market enrichment at `/dashboard/market/enrichment` (see [Market Enrichment](#market-enrichment)).

> **Spec reference:** `docs/specs/market.md`

---

### Market Enrichment

| Field | Detail |
|---|---|
| **Menu label** | Content → **Market Enrichment** |
| **URL** | `/dashboard/market/enrichment` |
| **Access** | FREE (3 items per tab) · SUBSCRIBER / DEMO / ADMIN (all) |
| **Data refreshes** | Populated by the daily Market Digest pipeline (post-save enrichment step) |

A three-tab view of the structured data extracted from market digest entries:

| Tab | What it shows |
|---|---|
| **Regulatory Trackers** | Pending rulemakings with jurisdiction, stage (Discussion Paper → Enforcement), docket ID, comment deadline |
| **Private Funding Rounds** | Company name, round stage, amount, lead investors, announced date (180-day lookback) |
| **Deal Terms** | Headline, upfront cash, milestone payments, equity stake %, royalty %, disclosed portion |

Data is automatically extracted from digest entries by keyword and regex patterns — no manual entry required. If a tab is empty, the market pipeline has not identified qualifying activity in that category recently.

> **Spec reference:** `docs/specs/market.md`

---

### Market History

| Field | Detail |
|---|---|
| **Menu label** | Content → **Market History** |
| **URL** | `/dashboard/market/history` |
| **Access** | SUBSCRIBER / DEMO / ADMIN |
| **Data refreshes** | Appended daily by the Market Digest pipeline |

A date-range browser for all past market digests. Use the from/to date pickers to narrow the view, or scroll the summary table to find a specific day. Click any row to open the full digest detail for that date. Summary columns show entry count, top categories, and whether the digest triggered any subscriber alerts.

> **Spec reference:** `docs/specs/market.md`

---

### News Listing

| Field | Detail |
|---|---|
| **Menu label** | Content → **News Listing** |
| **URL** | `/dashboard/news` |
| **Access** | All tiers (archive depth varies: FREE = limited days, SUBSCRIBER+ = unlimited) |
| **Data refreshes** | Daily 04:00 UTC (RSS harvest), 04:00 UTC (Perplexity), 05:00–05:30 UTC (web scraping) |

All active news topics listed in order, each with its recent articles. Topics with 2 or more recent articles show a one-paragraph AI-generated summary at the top of their section. Articles show title, source name, publication date, and source tier badge (ACADEMIC / REGULATORY / INDUSTRY / LEGAL / RESEARCH / COMPETITOR).

FREE users see a limited date window with an upgrade prompt. SUBSCRIBER and ADMIN users see the full historical archive for every topic.

> **Spec reference:** `docs/specs/content.md`

---

### Trend History

| Field | Detail |
|---|---|
| **Menu label** | Content → **Trend History** |
| **URL** | `/dashboard/trends/history` |
| **Access** | FREE (last 4 snapshots) · SUBSCRIBER / DEMO / ADMIN (all snapshots) |
| **Data refreshes** | Weekly, Sunday 08:00 UTC |

A multi-line chart showing how the top 10 AI-in-healthcare keywords have trended across all weekly snapshots, with a clickable timeline table below. Each row in the table links to a full snapshot detail page showing the rising, fading, and new keyword cards with linked article examples for that week.

**Sub-pages:** Snapshot detail at `/dashboard/trends/history/{epochMillis}` — full signal cards for a single historical snapshot.

> **Spec reference:** `docs/specs/content.md`

---

### Trends

| Field | Detail |
|---|---|
| **Menu label** | Content → **Trends** |
| **URL** | `/dashboard/trends` |
| **Access** | FREE (top 5 rising) · SUBSCRIBER / DEMO / ADMIN (all signals) |
| **Data refreshes** | Weekly, Sunday 08:00 UTC (snapshot) · Falls back to live computation if no snapshot exists |

The latest keyword trend analysis across 30/90/180-day windows. A bar chart visualizes the top 10 rising keywords. Below the chart, three card sections show **Rising** (accelerating frequency), **Fading** (declining frequency), and **New** (first appearance) keyword signals. Each card links to the specific articles that drove the keyword's activity.

> **Important:** Fading and new keyword cards currently show as empty — only the rising signals list is populated by the detection pipeline.

> **Spec reference:** `docs/specs/content.md`

---

## Research

The **Research** group provides tools for deeper, query-driven analysis of the article corpus and competitive landscape using LLM synthesis.

---

### AI Search

| Field | Detail |
|---|---|
| **Menu label** | Research → **AI Search** |
| **URL** | `/research/ai-search` |
| **Access** | SUBSCRIBER / DEMO / ADMIN (credit-based throttle) · FREE / anonymous: blocked |
| **Data refreshes** | Vector index updated daily 07:00 UTC (after article harvest) |

Enter any query to get: (1) a ranked list of semantically similar articles retrieved from the vector store, and (2) a full-text synthesis card from each enabled AI model. You can select which models to query using the checkboxes (Claude, GPT, Gemini, Perplexity Sonar). Each synthesis card shows a multi-paragraph summary and key findings, with numbered `[N]` citations linking to source articles.

| Model | Credit cost |
|---|---|
| Claude / GPT / Gemini | 3 credits per search |
| Perplexity Sonar | 1 credit per search |
| Deep research models | 10 credits per search |

ADMIN users have no credit limit. The topK result count is capped at 20 for SUBSCRIBER, 50 for ADMIN.

> **Note:** New articles are not searchable until after the 07:00 UTC embedding job runs the following morning.

> **Spec reference:** `docs/specs/research.md`

---

### Find Articles

| Field | Detail |
|---|---|
| **Menu label** | Research → **Find Articles** |
| **URL** | `/dashboard/search` |
| **Access** | All tiers (any authenticated user, no date limit) |
| **Data refreshes** | Real-time against the article database |

A multi-field search form with filters for title, topic, author, source name, body text, and date range. Results update in-place via HTMX as you type or adjust filters — no page reload needed. Unlike AI Search, this searches stored article metadata directly (no vector similarity) and imposes no tier limits or credit costs.

> **Spec reference:** `docs/specs/content.md`

---

### Framework Analysis

| Field | Detail |
|---|---|
| **Menu label** | Research → **Framework Analysis** |
| **URL** | `/dashboard/frameworks` (overview) · `/dashboard/frameworks/{slug}` (company detail) |
| **Access** | Public (overview radar chart) · SUBSCRIBER / DEMO / ADMIN (detail + source articles) |
| **Data refreshes** | After each article harvest (via pipeline cascade) · Manual trigger on Pipelines page |

Competitive analysis of configured AI healthcare companies scored across 6 dimensions by Claude:

| Dimension | What it measures |
|---|---|
| Clinical Evidence | Peer-reviewed validation of outcomes |
| Market Penetration | Adoption breadth across health systems |
| Regulatory Positioning | FDA clearances and compliance posture |
| Technical Differentiation | Model architecture and proprietary data moats |
| Partnership Ecosystem | Integration depth with EHR and payer partners |
| Business Momentum | Funding trajectory and revenue signals |

Each company gets a 1–10 score per dimension, an overall score, strengths list, weaknesses list, and recent developments summary. The overview page shows a Chart.js radar chart comparing all companies. The detail page shows dimension breakdown with rationale paragraphs.

> **Adding companies:** Configured via `aihealthcare.frameworks.companies` in `application.yml`. No code changes needed.

> **Spec reference:** `docs/specs/reference.md`

---

### Intel Reports

| Field | Detail |
|---|---|
| **Menu label** | Research → **Intel Reports** |
| **URL** | `/research/intel` |
| **Access** | SUBSCRIBER / DEMO / ADMIN |
| **Data refreshes** | On demand only (no scheduler) |

On-demand competitive intelligence reports. Enter a freeform query (e.g., "AI-assisted prior authorization market sizing") and the system calls Perplexity to gather live web sources, then synthesizes a structured report with numbered citations using Claude. Reports are stored permanently and shared across all SUBSCRIBER+ users — you can browse the full history of past queries.

> **Important:** Report generation is synchronous and can take 30–60 seconds. If the browser connection times out, the report may still have been generated — check the list page for it.

> **Spec reference:** `docs/specs/research.md`

---

### Research Runs

| Field | Detail |
|---|---|
| **Menu label** | Research → **Research Runs** |
| **URL** | `/research/runs` |
| **Access** | All tiers (any authenticated user) |
| **Data refreshes** | Written after every research pipeline execution (automated + manual) |

A sortable audit log of every research pipeline run — both automated daily runs and queries triggered manually via the REST API. Columns: timestamp, query text, pipeline mode (LEGACY_GOOGLE / STAGED_RESEARCH / COMBINED), and citation count. Click any row for the full detail including the complete synthesized answer and source citation list.

> **Note:** Automated daily runs (from `ResearchHarvestScheduler`) are mixed in with manual user queries — there is no flag distinguishing them.

> **Spec reference:** `docs/specs/research.md`

---

### Vendor Compare

| Field | Detail |
|---|---|
| **Menu label** | Research → **Vendor Compare** |
| **URL** | `/research/vendors` |
| **Access** | All tiers (any authenticated user) |
| **Data refreshes** | Vendor checkboxes pull from COMPETITOR-tier articles (refreshed daily 05:00 UTC); free-form uses live Perplexity |

Two comparison modes:

| Mode | How it works |
|---|---|
| **Vendor-select** | Check boxes for specific competitors; cards appear with Strengths (+), Weaknesses (−), and a relevance score bar drawn from stored COMPETITOR-tier articles |
| **Free-form query** | Enter any question; the system runs a live Perplexity + article search and synthesizes a structured vendor comparison |

Results are not saved. There is no history — each comparison is generated fresh.

> **Spec reference:** `docs/specs/research.md`

---

## Legal

The **Legal** group covers time-sensitive regulatory and legislative intelligence for AI-in-healthcare compliance tracking.

---

### Legal Timeline

| Field | Detail |
|---|---|
| **Menu label** | Legal → **Legal Timeline** |
| **URL** | `/dashboard/legal` |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | Article harvest daily 04:00 UTC · Regulatory harvest daily 04:30 UTC |

A unified chronological view merging two streams: **AI-healthcare legal articles** (from LEGAL and ACADEMIC-tier feeds) and **regulatory events** (from FDA and CMS APIs). Both appear on a single timeline sorted by date. Filter by source type to view articles-only or regulatory events-only. Each item links to its source (article URL or regulatory reference number) and shows a category badge.

> **Spec reference:** `docs/specs/market.md` (Legal Trends section), CLAUDE.md L-1 slice

---

### Legal Trends

| Field | Detail |
|---|---|
| **Menu label** | Legal → **Legal Trends** |
| **URL** | `/dashboard/legal/trends` |
| **Access** | FREE (top 3 trends) · SUBSCRIBER / DEMO / ADMIN (all trends, chart capped at 10) |
| **Data refreshes** | Configurable schedule (`aihealthcare.legal-trends.schedule`) · Manual trigger (ADMIN) |

Keyword trend analysis focused specifically on legal, regulatory, and policy language. Rising trend cards show the keyword, category badge (LITIGATION / REGULATION / POLICY), 30-day frequency, 90-day baseline, momentum score, and a linked list of the specific articles or regulatory events that drove the keyword's activity.

> **Note:** The "Detect Now" trigger is ADMIN-only. If no snapshot exists yet, the page renders with empty cards and no chart.

> **Spec reference:** `docs/specs/market.md`

---

### Regulatory

| Field | Detail |
|---|---|
| **Menu label** | Legal → **Regulatory** |
| **URL** | `/dashboard/regulatory` |
| **Access** | FREE (5 events) · SUBSCRIBER / DEMO / ADMIN (50 events) |
| **Data refreshes** | Daily 04:30 UTC |

The regulatory event feed from the FDA (510(k) clearances, De Novo grants) and CMS (Federal Register rules). Each event shows a color-coded type badge, reference number, applicant/organization name, device or rule name, regulatory body badge (FDA / CMS), and event date. Filter tabs let you narrow to FDA-only or CMS-only.

> **Spec reference:** CLAUDE.md R-REG slice

---

### Legislation

| Field | Detail |
|---|---|
| **Menu label** | Legal → **Legislation** |
| **URL** | `/legislation` (index) · `/legislation/{id}` (detail) · `/legislation/map` (state map) · `/legislation/upcoming` (timeline) |
| **Access** | Public (index, map, upcoming) · All tiers (detail) |
| **Data refreshes** | Seeded at every app startup · Source freshness check: Monday 10:30 UTC · New bill discovery: Monday 11:00 UTC |

A registry of 41 enacted U.S. state laws regulating AI in healthcare across 24 states. The index is a searchable, filterable table with columns for state, bill number, year enacted, categories (Payer Utilization Review, Provider Disclosure, Consumer Chatbots, etc.), and status. The state map shows per-state law counts at a glance. The upcoming page shows laws with effective dates in the next 90 days.

The detail page for each law includes: full requirements text, enforcement provisions, official source links with freshness status (checked weekly), a change history log, related wiki pages, and related company directory entries.

| Filter type | How to use |
|---|---|
| Free-text search | Matches bill number, title, requirements text |
| State filter | Drop-down by `StateCode` (all 50 states + DC) |
| Category filter | One of 8 law categories |
| Status filter | ENACTED / ENACTED_STAYED / PENDING |

> **Note:** Filters are applied one at a time (first non-blank parameter wins). You cannot combine state + category filters in the same query.

> **Spec reference:** `docs/specs/reference.md`

---

## Reference

The **Reference** group provides structured intelligence about companies, clinical research, and regulatory risk — the kind of data subscribers use for due diligence.

---

### Clinical Trials

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Clinical Trials** |
| **URL** | `/dashboard/clinical-trials` |
| **Access** | FREE (5 trials) · SUBSCRIBER / DEMO / ADMIN (50 trials) |
| **Data refreshes** | Configurable schedule (`aihealthcare.clinical-trials.schedule`) · Manual trigger on Pipelines page |

AI-related clinical trials from ClinicalTrials.gov. Each row shows the NCT ID (linked to the trial record), trial title, sponsor, phase, status, and conditions list. Summary badges at the top count recruiting trials, completed trials, Phase 2, and Phase 3 by tier. Filter buttons let you narrow to Recruiting or Completed trials only.

> **Spec reference:** `docs/specs/companies.md`

---

### Companies

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Companies** |
| **URL** | `/dashboard/companies` |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | Weekly (company discovery scheduler) |

The **internal** operational view of the company database — distinct from the public-facing [Company Directory](#company-directory). Shows raw discovery metadata including: sub-sector classification, funding stage, HQ location, founded year, validation status badge (green ✓ = validated, gray ? = unreviewed), and discovered/validated dates.

Use this page to review what the automated discovery pipeline has found, check which companies have been human-validated, and identify gaps in coverage by sub-sector.

> **Spec reference:** `docs/specs/companies.md`

---

### Company Directory

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Company Directory** |
| **URL** | `/directory` (index) · `/directory/{slug}` (detail) · `/directory/export.csv` (CSV) |
| **Access** | Public (no login required) · CSV export: ENTERPRISE / ADMIN |
| **Data refreshes** | Signal scores recomputed on every page request (articles, deals, sentiment) |

The public-facing, SEO-optimized showcase of AI healthcare companies. Sort the directory four ways:

| Sort tab | What it prioritizes | Access |
|---|---|---|
| **Relevance** | Recent article velocity + signal score | Public |
| **Trending** | Momentum over 90-day article window | Authenticated |
| **Recently Funded** | Latest deal signal date (FUNDING type) | SUBSCRIBER+ |
| **Watch List** | Companies with active watchlist items | SUBSCRIBER+ |

Each company card shows a relevance score chip and signal badges: 🔥 Trending, 💰 Recently Funded, ⚠ Watchlist Alert. The detail page shows the full company profile, JSON-LD Organization schema (for SEO), up to 5 related wiki pages, and up to 5 relevant state laws.

> **Spec reference:** `docs/specs/reference.md`

---

### Relationships

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Relationships** |
| **URL** | `/dashboard/relationships` (table) · `/dashboard/relationships/graph` (D3 visualization) |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | After each article harvest (via pipeline cascade) |

LLM-detected inter-company relationships in the AI healthcare sector. Each relationship shows: source company, target company, relationship type badge (Partnership / Acquisition / Investment / Integration / Competition / Licensing / Distribution), confidence percentage, evidence article title and link, and detected date. Use the company filter dropdown to zoom in on a specific organization.

The graph view (`/relationships/graph`) renders the same data as an interactive node-link diagram — useful for visualizing the overall partnership and investment network.

> **Spec reference:** `docs/specs/companies.md`

---

### Sentiment & Risk

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Sentiment & Risk** |
| **URL** | `/dashboard/risk` (overview) · `/dashboard/risk/{slug}` (company detail) |
| **Access** | FREE (top 5 companies) · SUBSCRIBER / DEMO / ADMIN (all companies + detail) |
| **Data refreshes** | After each article harvest (via pipeline cascade) · Manual trigger on Pipelines page |

Company-level sentiment aggregated from article-by-article LLM classification. Each company gets:
- An **overall score** on a continuous [-1.0, +1.0] scale
- A **label** (POSITIVE / NEGATIVE / MIXED / NEUTRAL)
- An article count and distribution bar (% positive, % negative, % mixed, % neutral)

The detail page adds a per-article sentiment table with source name, URL, publication date, individual label, confidence score, and the LLM's one-sentence rationale. Authenticated users can also add personal analyst notes on the detail page.

> **Spec reference:** `docs/specs/reference.md`

---

### Wiki

| Field | Detail |
|---|---|
| **Menu label** | Reference → **Wiki** |
| **URL** | `/wiki` (index) · `/wiki/{slug}` (page detail) · `/wiki/contradictions` (reversal watch) |
| **Access** | Public (index) · Authenticated for detail + contradiction pages · SUBSCRIBER+ for analyst notes |
| **Data refreshes** | After each RSS harvest (wiki compilation cascade) · Manual trigger: `POST /monitoring/wiki/compile` |

A persistent, LLM-compiled knowledge base of AI-in-healthcare topics built from harvested articles. The index is a searchable, paginated grid (20 pages per page) filterable by page type (ENTITY / CONCEPT / COMPARISON / OVERVIEW).

Each wiki page shows:
- **Rendered markdown** content
- **Source provenance table** — every claim traces back to a specific article with a grade
- **Contradiction history** — when later articles contradict earlier claims
- **Revision history** — all past versions of the page
- **Related pages** — cross-links to other wiki topics
- **Analyst notes** — personal notes for SUBSCRIBER+ users

The **Contradictions** feed (`/wiki/contradictions`) shows all detected reversals across all pages in the last 90 days — useful for tracking when a company reverses a prior claim.

> **Spec reference:** `docs/specs/content.md`

---

## Account

The **Account** group (right side of the nav) contains publication tools, personal settings, and admin operations.

---

### Social Posts

| Field | Detail |
|---|---|
| **Menu label** | Account → **Social Posts** |
| **URL** | `/dashboard/social` |
| **Access** | Authenticated users |
| **Data refreshes** | Generated on demand from recent Market Digest entries |

Generate LinkedIn and Facebook post drafts from the latest market digest intelligence using a Spring AI agent. The agent accesses market digest data, company profiles, and deal signals as tools to produce a post body, hashtags, and a first-comment citation block appropriate for each platform. Generated drafts are saved to Post Drafts for editing before publishing.

> **Spec reference:** CLAUDE.md SOC-1 slice

---

### Post Drafts

| Field | Detail |
|---|---|
| **Menu label** | Account → **Post Drafts** |
| **URL** | `/dashboard/social/drafts` |
| **Access** | Authenticated users |
| **Data refreshes** | Written by Social Post generator and Editorial Calendar |

A queue of saved LinkedIn and Facebook post drafts. Drafts are created by the Social Posts generator and by the Editorial Calendar's "generate post" action. Edit any draft before copying it to publish manually on the respective platform.

---

### Weekly Roundup

| Field | Detail |
|---|---|
| **Menu label** | Account → **Weekly Roundup** |
| **URL** | `/dashboard/weekly-roundup` |
| **Access** | All authenticated tiers (no tier gate) |
| **Data refreshes** | Generated live on every page load — no caching |

Produces copy-ready social content for three channels from the past 7 days of LEGAL and COMPETITOR-tier articles:

| Output | Format | Limit |
|---|---|---|
| LinkedIn post body | Analytical narrative, no URLs | ≤ 2,900 chars |
| LinkedIn first comment | Source publication names + insights page link | — |
| Substack article | Long-form with section headers | — |

The synthesis is generated by Claude at page-load time. If the LLM call fails, a fallback narrative is constructed from article headlines.

> **Important:** Every page visit makes a live LLM call. Avoid refreshing repeatedly to minimize cost.

> **Spec reference:** `docs/specs/market.md`

---

### Watchlist

| Field | Detail |
|---|---|
| **Menu label** | Account → **Watchlist** |
| **URL** | `/watchlist` |
| **Access** | SUBSCRIBER / DEMO / ENTERPRISE / ADMIN only (FREE redirected to Pricing) |
| **Data refreshes** | After each RSS harvest (watchlist matching runs automatically) |

Configure keyword, company, and topic monitors. After every article harvest, the watchlist matching service scans new articles for your configured items and records matches with a snippet. The page shows:
- Your watchlist items grouped by type (COMPANY / KEYWORD / TOPIC)
- A table of the 50 most recent matches with article titles, snippets, and source links

Add items using the form at the top. There is no limit on the number of items.

> **Spec reference:** `docs/specs/content.md`

---

### Profile

| Field | Detail |
|---|---|
| **Menu label** | Account → **Profile** |
| **URL** | `/profile` |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | Usage metrics updated in real-time by search and article-read events |

Your subscription status page. Shows: email address, tier badge, demo expiration date (if DEMO), monthly AI search usage meter, articles read today, and digest email subscription status. SUBSCRIBER-tier users see a Stripe billing portal link for managing payment method, invoice history, and cancellation.

**Actions:**
- **Unsubscribe** — stops digest email delivery; does not cancel Stripe billing or change your tier
- **Resubscribe** — re-enables digest email delivery
- **Stripe Portal** — manage your subscription payment (SUBSCRIBER only)

> **Spec reference:** `docs/specs/account.md`

---

### Webhooks

| Field | Detail |
|---|---|
| **Menu label** | Account → **Webhooks** |
| **URL** | `/settings/webhooks` |
| **Access** | All tiers (authenticated) |
| **Data refreshes** | Triggered in real-time by application events |

Configure webhook delivery channels to receive real-time notifications when events occur. Supported channel types: Slack, Microsoft Teams, or Generic HTTP. Each channel subscribes to one or more event types:

| Event | When it fires |
|---|---|
| **DEAL_SIGNAL** | A new deal signal is detected |
| **REGULATORY_EVENT** | A new FDA/CMS regulatory event is harvested |
| **TREND_ALERT** | A new trend snapshot is generated |
| **DIGEST_READY** | A new newsletter digest run is available |

Each channel entry shows the last-triggered timestamp. Use the **Test** button to send a `TEST_PING` payload and verify the endpoint is reachable.

> **Spec reference:** `docs/specs/account.md`

---

### Pricing

| Field | Detail |
|---|---|
| **Menu label** | Account → **Pricing** |
| **URL** | `/pricing` |
| **Access** | Public (no login required) |
| **Data refreshes** | Static (tier pricing is configured in Stripe) |

Side-by-side comparison of FREE and SUBSCRIBER tiers with feature checkmarks. The **Upgrade** button initiates Stripe Checkout ($49/month). On successful payment, Stripe calls the application webhook and upgrades your account — this typically takes a few seconds after checkout completes.

> **Spec reference:** `docs/specs/account.md`

---

### Developer

| Field | Detail |
|---|---|
| **Menu label** | Account → **Developer** |
| **URL** | `/developer` |
| **Access** | All tiers (authenticated) |

API key management for programmatic access to the REST API. Keys use `X-API-Key` header authentication. Generate keys to access endpoints like `/api/v1/articles`, `/api/v1/search/ai`, and `/api/v1/market-digest` from external tools or scripts.

---

### About

| Field | Detail |
|---|---|
| **Menu label** | Account → **About** |
| **URL** | `/about` |
| **Access** | Public |

Background on the AI in Healthcare platform, Big Sky Labs, and the data sources that power the application.

---

### Notes

| Field | Detail |
|---|---|
| **Menu label** | Account → **Notes** |
| **URL** | `/notes` |
| **Access** | Authenticated users |

Personal notes page for the authenticated user.

---

---

## Admin Pages

The following pages are visible in the **Account** menu only to users with the **ADMIN** role.

---

### Pipelines

| Field | Detail |
|---|---|
| **Menu label** | Account → **Pipelines** _(ADMIN only)_ |
| **URL** | `/admin/pipelines` |
| **Access** | ADMIN role required |
| **Data refreshes** | Last-run status updated after each pipeline execution |

The operations console for all 24 background data pipelines. Each card shows: pipeline name, description, schedule, last-run status (SUCCESS / FAILED / PARTIAL), last-run duration and item count, pre-flight warnings (missing API keys, connectivity issues), and a **Run Now** button for manual triggering.

A recent history table at the bottom shows the last 50 pipeline run events with error classification by type (LLM_AUTH / LLM_QUOTA / NETWORK / FATAL) and responsible provider (Anthropic / OpenAI / Perplexity / Alpaca).

| Pipeline card | What it triggers |
|---|---|
| rss-feeds | RSS + research article harvest |
| web-monitoring | Competitor page scraping |
| huggingface | HuggingFace model discovery |
| regulatory-harvest | FDA/CMS event fetch |
| embedding | pgvector article embedding |
| wiki-compile | LLM wiki compilation |
| sentiment-analysis | Company sentiment scoring |
| framework-analysis | Competitive framework scoring |
| trend-detection | Keyword trend snapshot |
| deal-signals | Deal signal detection scan |
| claim-detection | Frontier claim extraction |
| market-digest | Daily market digest |
| price-reaction | Multi-horizon price-reaction scoring |
| legislation-source-check | Source URL freshness check |
| legislation-discovery | Perplexity new bill discovery |
| notebooklm-sync | EC2 → NotebookLM export _(dev-machine only)_ |

> **Spec reference:** `docs/specs/admin.md`

---

### Admin

| Field | Detail |
|---|---|
| **Menu label** | Account → **Admin** _(ADMIN only)_ |
| **URL** | `/admin` |
| **Access** | ADMIN role required |
| **Data refreshes** | Real-time (user and article counts read directly from DB) |

User management and system analytics. The top section shows all registered users with email, role, enabled status, tier, and demo expiration. Inline actions let you toggle a user's enabled flag or change their role. Below the user table: ingestion analytics (article counts, daily ingestion chart, topic distribution), newsletter run count, and subscriber count.

> **Spec reference:** `docs/specs/admin.md`

---

### Newsletter Runs

| Field | Detail |
|---|---|
| **Menu label** | Account → **Newsletter Runs** _(ADMIN only)_ |
| **URL** | `/newsletter/runs` |
| **Access** | ADMIN role required |
| **Data refreshes** | New DRAFT written daily at 00:00 UTC |

The newsletter run list with status badges (DRAFT / SENT / ARCHIVED). Click any DRAFT row to open the TinyMCE editor where you can revise the HTML content before sending. The **Send** button delivers the newsletter to all active subscribers via AWS SES.

> **Spec reference:** `docs/specs/content.md`

---

### Document Library

| Field | Detail |
|---|---|
| **Menu label** | Account → **Document Library** _(ADMIN only)_ |
| **URL** | `/admin/documents` |
| **Access** | ADMIN role required |
| **Data refreshes** | Written on upload |

Upload PDF, DOCX, TXT, or Markdown files (max 50 MB each) for ingestion into the vector store and wiki. Each uploaded document is parsed, chunked, embedded into the pgvector store, and compiled into a wiki page. Status badges (INGESTED / PROCESSING / FAILED) track ingestion progress. Wiki deep-links appear once a page is successfully compiled.

> **Spec reference:** `docs/specs/admin.md`

---

### Wiki Gaps

| Field | Detail |
|---|---|
| **Menu label** | Account → **Wiki Gaps** _(ADMIN only)_ |
| **URL** | `/admin/wiki-gaps` |
| **Access** | ADMIN role required |
| **Data refreshes** | Generated by the wiki gap analysis pipeline (manual or scheduled) |

Review the LLM's analysis of which topics in the article corpus lack wiki coverage. Each gap item shows: the suggested page title, page type (ENTITY / CONCEPT / COMPARISON / OVERVIEW), and a one-sentence rationale. **Approve** items you want to create; **Dismiss** items that are out of scope. Approving marks the item for wiki compilation — trigger `POST /monitoring/wiki/compile` from the Pipelines page afterward to actually create the pages.

> **Spec reference:** `docs/specs/admin.md`

---

### SSO Providers

| Field | Detail |
|---|---|
| **Menu label** | Account → **SSO Providers** _(ADMIN only)_ |
| **URL** | `/admin/sso` |
| **Access** | ADMIN role required |
| **Data refreshes** | Manual (no pipeline) |

Configure SAML2 identity providers for enterprise customer SSO. Each IdP entry requires: a registration ID (permanent, cannot be changed after creation), entity ID, SSO URL, X.509 certificate, email attribute name, and optional metadata URL. Users who authenticate via a configured IdP are automatically provisioned at ENTERPRISE tier.

> **Spec reference:** `docs/specs/admin.md`

---

### Outreach CRM

| Field | Detail |
|---|---|
| **Menu label** | Account → **Outreach CRM** _(ADMIN only)_ |
| **URL** | `/admin/outreach` (list) · `/admin/outreach/{slug}` (company detail) |
| **Access** | ADMIN role required |
| **Data refreshes** | Manual entry |

A lightweight CRM for tracking outreach to healthcare AI companies. The list page shows all outreach records with purpose (e.g., SALES / PARTNERSHIP / INTELLIGENCE), status (CONTACTED / RESPONDED / CLOSED / etc.), and notes. A combo box populated from the company database lets you add a new record by company name with live contact preview.

The company detail page shows all outreach rows for that company, the full contact list (name, title, email, LinkedIn), and inline status/notes editing. If the company is not yet in the public Directory, a **Register to Directory** form appears to add a minimal listing.

> **Spec reference:** `docs/specs/admin.md`

---

## Enterprise

The **Enterprise** group is visible only to users with the **ENTERPRISE** tier or **ADMIN** role.

---

### Data Console

| Field | Detail |
|---|---|
| **Menu label** | Enterprise → **Data Console** |
| **URL** | `/enterprise/data` |
| **Access** | ENTERPRISE tier or ADMIN role |
| **Data refreshes** | Jobs written on submit · Push schedules fire per configured cron |

A three-tab self-service console for structured data exports:

| Tab | What it shows / does |
|---|---|
| **Feeds** | Available data feeds (article corpus, market digests, company data) with supported formats and row limits |
| **Jobs** | Your last 50 export jobs with status, row count, file size, timestamps, and download link for completed jobs. Auto-polls for in-flight jobs. |
| **Schedules** | Push delivery schedules — configure a cron expression, recipient list, and format for automated email delivery of exports |

Each job is owner-scoped: you see only your own jobs and schedules. Completed artifacts are available at a signed download URL (`/d/{token}`) that requires no login.

> **Spec reference:** `docs/specs/admin.md`

---

### Intelligence Console

| Field | Detail |
|---|---|
| **Menu label** | Enterprise → **Intelligence** _(ADMIN only in nav; ENTERPRISE+ in practice)_ |
| **URL** | `/admin/intelligence` |
| **Access** | SUBSCRIBER (5 tabs) · ENTERPRISE / DEMO / ADMIN (all 9 tabs) |
| **Data refreshes** | Live — proxied to the Claude Intelligence Service |

A tabbed console for the Claude Healthcare Intelligence Service — a companion application running alongside the main app. All tab content is fetched live from the intelligence service via an HTTP proxy.

| Tab | Purpose | Access |
|---|---|---|
| Chat | Conversational AI queries about healthcare AI | SUBSCRIBER+ |
| Analyze | Document or market segment deep-dive | SUBSCRIBER+ |
| Synthesis | Multi-source narrative synthesis | SUBSCRIBER+ |
| Platform Race | Competitive platform tracking | SUBSCRIBER+ |
| History | Past session history | SUBSCRIBER+ |
| Wiki Ask | Natural language queries against the wiki | SUBSCRIBER+ |
| Verify | Claim verification against sourced evidence | ENTERPRISE+ |
| Trending | Live trending signals from intelligence service | ENTERPRISE+ |
| Files | Uploaded document management | ENTERPRISE+ |

> **Note:** If the Claude Intelligence Service is unavailable, all tabs show an error state. The console page itself still loads.

> **Spec reference:** `docs/specs/research.md`

---

## Login & Registration

---

### Login

| Field | Detail |
|---|---|
| **Menu label** | (Redirect — no direct nav link) |
| **URL** | `/login` |
| **Access** | Public |

Standard email + password login form. On failure, a generic error message appears (no hint of which field was wrong). On success, you are redirected to the page you originally tried to reach, or to `/dashboard` if you navigated directly to `/login`. SAML SSO users bypass this form and authenticate via their configured IdP.

Password reset is available at `/forgot-password` (not linked from the login page — navigate directly).

> **Spec reference:** `docs/specs/account.md`

---

### Register

| Field | Detail |
|---|---|
| **Menu label** | (Linked from Login page and Pricing page) |
| **URL** | `/register` |
| **Access** | Public |

Self-registration for a 7-day DEMO account. Provide full name, email, and password. On submit, your account is created, a welcome email with your expiration date is sent, and you are automatically logged in and directed to the dashboard.

After 7 days, your effective access downgrades to FREE. Upgrade at any time on the Pricing page.

> **Spec reference:** `docs/specs/account.md`

---

### Privacy Policy

| Field | Detail |
|---|---|
| **Menu label** | Account → Pricing (footer link) |
| **URL** | `/privacy` |
| **Access** | Public |

The privacy policy for bigskylabs.ai. Covers data collection, email usage, third-party services (AWS SES, Stripe, Perplexity), and a contact address (`newsletter@bigskylabs.ai`).

> **Spec reference:** `docs/specs/account.md`

---

## Spec File Index

Each section of this manual corresponds to one or more detailed spec files in `docs/specs/`:

| Spec file | Pages covered |
|---|---|
| `docs/specs/content.md` | Dashboard, News Listing, Find Articles, Wiki, Trends, Trend History, Watchlist, Newsletter Runs |
| `docs/specs/market.md` | Market Digest, Market Enrichment, Market History, Deal Signals, Legal Trends, Weekly Roundup |
| `docs/specs/research.md` | AI Search, Vendor Compare, Research Runs, Intel Reports, Intelligence Console |
| `docs/specs/reference.md` | Sentiment & Risk, Framework Analysis, Company Directory, Legislation |
| `docs/specs/companies.md` | Clinical Trials, Companies (internal), Relationships |
| `docs/specs/admin.md` | Admin, SSO Providers, Outreach CRM, Pipelines, Data Console, Document Library, Wiki Gaps, Editorial Calendar |
| `docs/specs/account.md` | Login, Register, Profile, Pricing, Privacy, Webhooks |
