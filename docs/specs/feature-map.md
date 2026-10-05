# AIHealthcare -- Feature Map

> Last updated: 2026-10-05

This document is a complete reference for the AIHealthcare application at bigskylabs.ai. It catalogs every user-facing page, REST API endpoint, background scheduler, and AI adapter currently built into the system, organized by functional area. Use it to understand what already exists before adding new features, diagnosing issues, or planning the next slice.

---

## Thymeleaf Pages (User-Facing)

### Content

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/dashboard` | `dashboard` | Public | Daily Briefing page with watchlist alerts, top headlines, trending keywords, regulatory events, and legal pulse articles |
| `/dashboard/news` | `news-listing` | Public | All articles grouped by topic; archive depth capped by tier for FREE users |
| `/dashboard/articles` | `articles` | Public (FREE blocked from Companies topic) | Single-topic article detail sorted by date |
| `/dashboard/search` | `search` | Public | Multi-field article search form with HTMX fragment response support |
| `/newsletter/runs` | `newsletter-runs` | Admin | All newsletter runs with status badges and edit links for DRAFT runs |
| `/newsletter/runs/{runId}/edit` | `newsletter-edit` | Admin | TinyMCE WYSIWYG editor for reviewing and editing a newsletter draft before sending |
| `/wiki` | `wiki-index` | Public | Searchable wiki page index with keyword search and page-type filter, paginated 20 per page |
| `/wiki/{slug}` | `wiki-detail` | Public (fullAccess: Subscriber+) | Single wiki page with rendered markdown, source provenance, contradictions, and revision history |
| `/wiki/contradictions` | `wiki-contradictions` | Public (fullAccess: Subscriber+) | Reversal Watch feed of recent claim contradictions across all wiki pages |
| `/wiki/digest` | `wiki-digest` | Public (fullAccess: Subscriber+) | "What Changed This Week" digest of newly created and updated wiki pages |
| `/wiki/ask` | `wiki-ask` | Public (fullAccess: Subscriber+) | Conversational search that synthesizes an AI answer grounded in wiki pages |
| `/dashboard/trends` | `trends` | FREE=top 5 topics, Subscriber+=all | Trend detection dashboard with rising keyword signals and article drill-down cards |
| `/dashboard/trends/history` | `trend-history` | FREE=4 snapshots, Subscriber+=full | Multi-line chart of keyword trends across all historical snapshots with clickable timeline |
| `/dashboard/trends/history/{epochMillis}` | `trend-history-detail` | Authenticated | Detail view of a single past trend snapshot with bar chart and keyword articles |
| `/watchlist` | `watchlist` | Subscriber+ | Personal watchlist of companies, keywords, and topics with recent article match results |

### Research

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/research/ai-search` | `ai-search` | FREE=upgrade prompt, Subscriber=credit-metered, Admin=unlimited | Unified AI-enhanced search with multi-model LLM synthesis (Claude, GPT, Perplexity, Gemini) |
| `/research/vendors` | `vendor-compare` | Public | Vendor comparison page with scored assessments, citation list, and COMPETITOR feed checkboxes |
| `/research/runs` | `research-runs` | Public | History of all Perplexity+DB research runs with mode badge and citation count |
| `/research/runs/{runId}` | `research-run-detail` | Public | Full detail for a single research run |

### Market

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/dashboard/market` | `market-digest` | FREE=top 3, Subscriber+=all | Most recent daily market digest with ranked entries, fact/spec badges, and category filter |
| `/dashboard/market/history` | `market-digest-history` | FREE=last 7 digests, Subscriber+=full | Browse all market digest dates with optional date-range filter |
| `/dashboard/market/weekly` | `market-digest-weekly` | FREE=top 3, Subscriber+=all | Deduplicated weekly rollup of market digest entries |
| `/dashboard/market/{date}` | `market-digest` | FREE=top 3, Subscriber+=all | Market digest for a specific ISO date |
| `/dashboard/deals` | `deals` | FREE=top 10, Subscriber=25/page paginated | Deal signals with type filter pills, trend stats, and ratio analytics |
| `/dashboard/deals/{signalId}` | `deals-detail` | Subscriber/Enterprise | Deal context cross-referenced with sentiment, framework, regulatory events, and company profile |
| `/dashboard/regulatory` | `regulatory` | FREE=5 events, Subscriber+=50 | FDA and CMS regulatory alerts with body filter, sortable columns, and event-type badge counts |
| `/dashboard/legal` | `legal-timeline` | FREE=30-day max, Subscriber+=all time | Unified chronological feed merging litigation articles, policy articles, and regulatory events |
| `/dashboard/claims` | `claim-tracker` | FREE=top 5, Subscriber+=all | Sortable frontier AI company claims table with company, verdict, and type filters |
| `/dashboard/social` | `social-post` | Subscriber+ | Side-by-side LinkedIn and Facebook post drafts sourced from the latest market digest |

### Reference

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/dashboard/risk` | `risk-dashboard` | FREE=top 5 companies, Subscriber+=all | Company sentiment and risk overview with scores, distribution bars, and chart |
| `/dashboard/risk/{slug}` | `risk-detail` | Subscriber+ | Per-article sentiment breakdown with doughnut chart for a single company |
| `/dashboard/frameworks` | `framework-analysis` | Public | Multi-company radar chart and score card grid for competitive framework analysis |
| `/dashboard/frameworks/{slug}` | `framework-detail` | Subscriber+ | Six-dimension radar chart and strengths/weaknesses breakdown for one company |
| `/dashboard/frameworks/{slug}/articles` | `framework-articles` | Subscriber+ | Sortable list of source articles used in a company's framework analysis |
| `/directory` | `company-directory` | Public (full sort tabs: Subscriber+) | Public company directory with sector filter pills and signal-scored sort tabs |
| `/directory/{slug}` | `company-directory-detail` | Public | Public company profile with JSON-LD SEO, related wiki pages, and applicable state laws |
| `/legislation` | `legislation-index` | Public | Searchable state health-AI law registry with state, category, and status filter pills |
| `/legislation/{id}` | `legislation-detail` | Authenticated | Law detail page with requirements, enforcement, sources, and related wiki/company links |
| `/legislation/map` | `legislation-map` | Public | State-by-state summary showing law counts per state |
| `/legislation/upcoming` | `legislation-upcoming` | Public | Laws with effective dates in the next 90 days sorted by date ascending |

### Account

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/profile` | `profile` | Authenticated | Self-service profile with tier badge, usage meter, DEMO expiry countdown, and Stripe portal link |
| `/login` | `login` | Public | Spring Security login form with optional SSO sign-in buttons per active IdP |
| `/register` | `register` | Public | Self-registration form that creates a new DEMO-tier user account |

### Admin

| URL | Template | Access | What it does |
|-----|----------|--------|--------------|
| `/admin` | `admin` | Admin | User management table, role and enabled counts, article analytics charts, and subscriber count |
| `/admin/sso` | `sso-providers` | Admin | List of all configured SSO Identity Providers |
| `/admin/sso/new` | `sso-provider-form` | Admin | Create a new SSO provider configuration |
| `/admin/sso/{id}/edit` | `sso-provider-form` | Admin | Edit an existing SSO provider configuration |
| `/admin/outreach` | `outreach` | Admin | Company outreach CRM list with combo-box for adding new records |
| `/admin/outreach/{slug}` | `outreach-company` | Admin | Company outreach detail with contacts, inline status/notes editing, and Register to Directory form |
| `/legislation/changes` | `legislation-changes` | Admin | Unreviewed source-URL change events for monitored state laws |
| `/enterprise/data` | `enterprise-data-console` | Enterprise | Data export console for submitting jobs, monitoring progress, tailing logs, and managing push schedules |

---

## REST API Endpoints

### Articles & Search

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| GET | `/api/v1/articles` | ArticleController | Returns articles for a topic keyword with archive-depth limits per tier |
| GET | `/api/v1/articles/search` | ArticleSearchController | Searches articles by optional multi-field criteria (title, topic, author, date range, etc.) |
| GET | `/api/v1/search/ai` | AiSearchRestController | Executes AI-enhanced vector search and returns multi-model LLM synthesis results |

### Content & Newsletter

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| GET | `/api/v1/runs` | NewsletterRunController | Lists all newsletter runs as compact summaries without HTML content |
| GET | `/api/v1/runs/{runId}` | NewsletterRunController | Returns full detail of a newsletter run including HTML and plain-text content |
| POST | `/api/v1/newsletter/deliver` | NewsletterDeliveryController | Manually triggers delivery of a newsletter run to all active subscribers |
| POST | `/api/v1/subscribers` | SubscriberController | Registers a new subscriber; returns 409 if the email is already registered |
| GET | `/api/v1/subscribers` | SubscriberController | Lists all subscribers, both active and inactive |
| DELETE | `/api/v1/subscribers/{email}` | SubscriberController | Removes a subscriber by email |
| POST | `/monitoring/wiki/compile` | WikiCompilationController | Triggers wiki knowledge compilation from the last 24 hours of articles |

### Trends & Intelligence

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| GET | `/api/v1/trends/latest` | TrendRestController | Returns the most recent keyword trend snapshot, or 204 if none exists |
| GET | `/api/v1/trends/history` | TrendRestController | Returns all stored trend snapshots ordered newest first |
| POST | `/api/v1/trends/detect` | TrendRestController | Triggers on-demand trend detection and returns the new snapshot |
| GET | `/api/v1/sentiment` | SentimentRestController | Returns all persisted company sentiment records |
| GET | `/api/v1/sentiment/{slug}` | SentimentRestController | Returns the sentiment record for a single company |
| POST | `/api/v1/sentiment/analyze` | SentimentRestController | Triggers a full LLM sentiment re-analysis for all tracked companies (Admin) |
| GET | `/api/v1/frameworks` | FrameworkRestController | Returns all competitive framework analyses ordered by score |
| GET | `/api/v1/frameworks/{slug}` | FrameworkRestController | Returns the framework analysis for a single company |
| POST | `/api/v1/frameworks/analyze` | FrameworkRestController | Triggers a full 6-dimension LLM competitive analysis run (Admin) |

### Market & Deals

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| GET | `/api/market-digest/latest` | MarketDigestController | Returns the most recent market digest, or 404 if none produced yet |
| GET | `/api/market-digest/{date}` | MarketDigestController | Returns the market digest for a specific yyyy-MM-dd date |
| GET | `/api/market-digest` | MarketDigestController | Returns digest summaries with optional from/to date filter |
| GET | `/api/market-digest/weekly-rollup` | MarketDigestController | Returns a 7-day rollup for the week starting on the given weekOf date |
| GET | `/api/v1/deals` | DealSignalRestController | Returns recent deal signals with optional type filter and deduplication |
| GET | `/api/v1/deals/{signalId}` | DealSignalRestController | Returns enriched deal context including sentiment, framework, and regulatory cross-references |
| POST | `/api/v1/deals/detect` | DealSignalRestController | Triggers on-demand deal signal detection asynchronously |
| GET | `/api/v1/claims` | ClaimTrackerRestController | Returns frontier AI claims with optional company, verdict, and type filters |
| GET | `/api/v1/claims/{claimId}` | ClaimTrackerRestController | Returns a single frontier claim by ID |
| POST | `/api/v1/claims/detect` | ClaimTrackerRestController | Triggers claim detection against the last 24 hours of articles (Admin) |

### Legislation

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| GET | `/api/v1/legislation/state-laws` | StateLawRestController | Returns all state health-AI laws with optional state, category, status, and text filters |
| GET | `/api/v1/legislation/state-laws/{id}` | StateLawRestController | Returns a single state law by slug |
| GET | `/api/v1/legislation/state-laws/upcoming` | StateLawRestController | Returns laws with effective dates in the next N days (default 90) |
| GET | `/api/v1/legislation/state-laws/by-state` | StateLawRestController | Returns all laws grouped by state code |
| GET | `/api/v1/legislation/changes` | StateLawRestController | Returns unreviewed source change events for admin review |
| POST | `/api/v1/legislation/changes/{id}/review` | StateLawRestController | Marks a change event as reviewed (Admin) |
| POST | `/api/v1/legislation/refresh` | StateLawRestController | Triggers an async source-freshness check across all law source URLs (Admin) |

### Enterprise Data

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| POST | `/api/v1/enterprise/data/jobs` | EnterpriseDataRestController | Submits a new data export job; returns 202 Accepted with Location header |
| GET | `/api/v1/enterprise/data/jobs/{jobId}` | EnterpriseDataRestController | Returns status and metadata for a specific job owned by the authenticated user |
| GET | `/api/v1/enterprise/data/jobs` | EnterpriseDataRestController | Lists all jobs for the authenticated user |
| POST | `/api/v1/enterprise/data/jobs/{jobId}/cancel` | EnterpriseDataRestController | Cancels a queued or running job |
| GET | `/api/v1/enterprise/data/feeds` | EnterpriseDataRestController | Returns all available data feeds |
| GET | `/api/v1/enterprise/data/feeds/{feedId}/prompts` | EnterpriseDataRestController | Returns all canned prompt templates for a data feed |
| GET | `/api/v1/enterprise/data/jobs/{jobId}/log` | EnterpriseDataRestController | Reads job log content from a given byte offset for tailing |
| GET | `/api/v1/enterprise/data/jobs/{jobId}/artifact` | EnterpriseDataRestController | Downloads the completed export artifact as a file attachment |
| POST | `/api/v1/enterprise/data/schedules` | EnterpriseScheduleRestController | Creates a new push delivery schedule; returns 201 with next run preview |
| GET | `/api/v1/enterprise/data/schedules` | EnterpriseScheduleRestController | Lists all push schedules for the authenticated user |
| PUT | `/api/v1/enterprise/data/schedules/{scheduleId}` | EnterpriseScheduleRestController | Updates an existing push schedule |
| DELETE | `/api/v1/enterprise/data/schedules/{scheduleId}` | EnterpriseScheduleRestController | Deletes a push schedule |
| POST | `/api/v1/enterprise/data/schedules/{scheduleId}/run` | EnterpriseScheduleRestController | Immediately runs a schedule outside its cron cadence; returns 202 Accepted |
| GET | `/api/v1/enterprise/data/schedules/preview` | EnterpriseScheduleRestController | Returns the next N fire times for a cron expression without creating a schedule |

### Admin & Monitoring

| Method | Path | Controller | What it does |
|--------|------|-----------|--------------|
| POST | `/api/v1/monitoring/harvest` | WebMonitoringController | Manually triggers a competitor web page harvest |
| POST | `/api/v1/monitoring/huggingface` | WebMonitoringController | Manually triggers HuggingFace healthcare model discovery |
| POST | `/api/v1/monitoring/research-harvest` | WebMonitoringController | Manually triggers the Perplexity research harvest for all configured topics |
| POST | `/api/v1/monitoring/feeds` | WebMonitoringController | Manually triggers a full RSS feed harvest across all tiers |
| POST | `/api/v1/monitoring/summaries` | WebMonitoringController | Manually triggers AI topic summary generation for all configured topics |
| POST | `/api/v1/monitoring/embeddings` | WebMonitoringController | Manually triggers article embedding into the vector store |
| POST | `/api/v1/monitoring/company-discovery` | WebMonitoringController | Manually triggers the Perplexity-powered company discovery pipeline |
| POST | `/api/v1/monitoring/sentiment-pipeline` | WebMonitoringController | Runs the full sentiment pipeline -- builds company profiles and runs LLM classification |
| POST | `/api/v1/monitoring/run-all-pipelines` | WebMonitoringController | Triggers the full 13-step post-harvest pipeline cascade in a background thread; returns 202 |
| GET | `/api/v1/monitoring/hashes` | WebMonitoringController | Lists all stored page content hashes for change-detection diagnostics |

---

## Background Schedulers

| Scheduler | Schedule | What it does |
|-----------|----------|--------------|
| FeedHarvestScheduler (daily) | Daily 04:00 UTC | Harvests ACADEMIC and REGULATORY RSS feeds, persists articles, generates topic summaries, compiles wiki pages, and triggers the full pipeline orchestrator |
| FeedHarvestScheduler (industry) | Every 4 hours | Harvests INDUSTRY RSS feeds, persists articles, and runs wiki, watchlist, and deal-detection pipelines after each run |
| RegulatoryHarvestScheduler | Daily 04:30 UTC | Harvests FDA 510(k)/De Novo and CMS Federal Register events, deduplicates by reference number, and matches new events against subscriber watchlists |
| WebMonitoringScheduler (competitors) | Daily 05:00 UTC | Scrapes competitor web pages with SHA-256 change detection and saves changed pages as articles |
| ClinicalTrialHarvestScheduler | Daily 05:00 UTC | Harvests AI-healthcare clinical trials from ClinicalTrials.gov and matches new trials against subscriber watchlists |
| WebMonitoringScheduler (HuggingFace) | Daily 05:30 UTC | Discovers new healthcare AI models from the HuggingFace public API and persists them as articles |
| ResearchHarvestScheduler | Daily 06:00 UTC | Runs the COMBINED Perplexity+DB research pipeline per configured topic and persists results to research_runs |
| EmbeddingScheduler | Daily 07:00 UTC | Embeds all un-embedded articles into the pgvector vector store in batches of 10 |
| MarketAnalysisScheduler (digest) | Daily 07:00 UTC | Runs Perplexity news research, Claude impact classification, and Alpaca data enrichment to produce the daily market digest |
| DailyBriefingScheduler | Daily 07:30 UTC | Delivers personalized daily briefing emails to subscribers after harvest and embedding pipelines complete |
| WikiLintScheduler | Daily 08:00 UTC | Runs a lint pass on all wiki pages detecting orphans, broken refs, stale pages, and missing provenance |
| DemoExpirationScheduler | Daily 02:00 UTC | Transitions expired DEMO users to FREE_PENDING and sends each a demo-expiration email |
| EnterpriseDataRetentionScheduler | Daily 03:15 UTC | Deletes expired enterprise data artifacts and logs, and marks those jobs as EXPIRED |
| DigestDeliveryScheduler | Daily midnight UTC | Builds and delivers the FREE-tier daily digest email to all active subscribers |
| NewsletterGenerationScheduler | Monday 08:00 UTC | Ingests the week's articles, generates a newsletter draft, and auto-sends to eligible subscribers unless the admin override is active |
| CompanyDiscoveryScheduler | Sunday 06:00 UTC | Runs Perplexity-powered AI healthcare company discovery and persists newly found companies |
| TrendDetectionScheduler | Sunday 08:00 UTC | Runs keyword frequency analysis across 30/90/180-day windows and persists a TrendSnapshot |
| LegalTrendScheduler | Sunday 09:00 UTC | Runs LLM-based legal and regulatory trend detection and persists a LegalTrendSnapshot |
| LegislationMonitorScheduler | Monday 10:30 UTC | Re-fetches all state law source URLs, computes SHA-256 hashes, and records LawChangeEvents for drifted content |
| MarketAnalysisScheduler (price reaction) | Hourly | Polls for due, uncaptured price-reaction horizons (1h/4h/1d/3d) and persists new PriceReactionSnapshot records |
| EnterpriseDataJobReaper | Every 5 minutes | Marks enterprise data jobs whose heartbeat has timed out as FAILED/ORPHANED |
| EnterpriseDataPushScheduler | Every minute | Atomically claims due push schedules, submits data jobs, and delivers artifacts to recipients via email or signed link |

---

## AI Adapters

| Adapter | Port | Model | Prompt File | What it does |
|---------|------|-------|-------------|--------------|
| AiSummarizationAdapter | AiSummarizationPort | Default ChatClient | summarize-articles.txt, summarize-articles-rag.txt, generate-introduction.txt, topic-summary.txt | Summarizes articles into newsletter sections in standard, RAG-augmented, and topic-summary modes; runs the anti-slop pipeline for topic summaries |
| AiEvaluationAdapter | AiEvaluationPort | claude-haiku-4-5 | evaluate-section.txt | LLM-as-judge scoring of newsletter sections against source articles on five dimensions |
| AiReportAdapter | AiReportPort | Default ChatClient | (caller-supplied) | Sends a caller-provided free-form prompt verbatim to the LLM and returns the raw text response for monthly reports |
| AnthropicAiSearchAdapter | AiSearchPort | claude-sonnet-4-6 | ai-search-synthesis.txt | Synthesizes vector search results using Claude, producing a structured SUMMARY and KEY_FINDINGS response |
| OpenAiSearchAdapter | AiSearchPort | gpt-4o | ai-search-synthesis.txt | Synthesizes vector search results using OpenAI GPT, sharing the same prompt and parser as the Claude adapter |
| GeminiAiSearchAdapter | AiSearchPort | gemini-3.5-flash | ai-search-synthesis.txt | Synthesizes results using Google Gemini via direct REST to the Generative Language API; returns a fallback when GEMINI_API_KEY is absent |
| PerplexityAiSearchAdapter | AiSearchPort | perplexity/sonar | ai-search-synthesis.txt | Synthesizes results using Perplexity Sonar via the Agent API (/v1/agent); returns a fallback when PERPLEXITY_API_KEY is absent |
| PerplexityDeepResearchAiSearchAdapter | AiSearchPort | sonar-deep-research | ai-search-synthesis.txt | Generates analyst-grade multi-round web research using Perplexity's deep-research preset with a 90-second read timeout |
| AwsBedrockAiSearchAdapter | AiSearchPort | amazon.nova-lite-v1:0 | ai-search-synthesis.txt | Synthesizes results using AWS Bedrock Converse API; conditionally activated by aihealthcare.aws.bedrock.enabled=true |
| SentimentAnalysisAdapter | SentimentAnalysisPort | claude-haiku-4-5 | sentiment-analysis.txt | Classifies article sentiment toward a company in batches of 20, parsing a structured SENTIMENT_RESULTS response into ArticleSentiment records |
| FrameworkAnalysisLlmAdapter | FrameworkLlmPort | claude-haiku-4-5 | framework-analysis.txt | Scores a company across six competitive dimensions (1-10) using up to 50 articles per analysis |
| DealClassificationAdapter | DealClassificationPort | claude-haiku-4-5 | deal-classification.txt | Confirms deal signals and extracts type, amount, counterparty, and analysis from candidate articles in batches of 10 |
| ClaimClassifierAdapter | ClaimClassifierPort | claude-sonnet-4-5 | claim-classifier.txt, claim-contradiction.txt | Extracts and classifies frontier AI claims from articles in batches, and separately detects 90-day contradictions against prior claims |
| WikiCompilationAdapter | KnowledgeCompilationPort | (wired from AppConfig) | wiki-compile.txt | Compiles articles into wiki pages, detects contradictions, runs SlopLinter on each page, and notifies search engines of changed URLs |
| WikiGapAnalysisAdapter | WikiGapAnalysisPort | Default ChatClient (maxTokens=8000) | wiki-gap-analysis.txt | Identifies coverage gaps in the wiki by comparing recent article titles against existing page summaries |
| SocialPostAgentAdapter | SocialPostDraftPort | Default ChatClient | social-post-agent.txt | Spring AI tool-calling agent that autonomously fetches market digest and company data via @Tool methods to draft LinkedIn and Facebook posts |
| DigestSummaryAdapter | DigestSummaryPort | claude-haiku-4-5 | digest-summary.txt | Generates a 3-5 paragraph executive summary with [N] citation markers for the FREE-tier daily digest email |
| ArticleScoringAdapter | ArticleScoringPort | claude-haiku-4-5 | tech-trend-score.txt | Scores articles 1-10 for technological significance in batches of 20, filtering out syndicated duplicates |
| ArticleBodyFormattingAdapter | ArticleBodyFormattingPort | claude-haiku-4-5 | article-format.txt | Reformats raw article body text into clean paragraphs with bold key entities, capping input at 3,000 characters |
| TrendExtractionAdapter | TrendTopicExtractionPort | Default ChatClient | trend-extract.txt | Identifies emerging healthcare AI themes from article titles and parses them into ExtractedTrend records |
| PerplexityDeepResearchAdapter | TrendSummaryPort | Perplexity Agent API (preset: medium, background polling) | (inline prompt) | Generates analyst-grade trend summary reports via Perplexity's async deep-research preset, stripping think blocks from the output |
| PerplexityCompanyResearchAdapter | CompanyResearchPort | perplexity/sonar | (inline prompts) | Discovers AI healthcare companies via Perplexity Agent API with JSON schema extraction and exponential backoff retry |
| PromptToQueryAdapter | PromptToQueryPort | Default ChatClient (temperature=0.0) | enterprise-query-plan.txt | Resolves free-text customer data queries into a constrained DataQueryPlan by injecting the feed's parameter schema; treats unparseable responses as rejections |
| VectorStoreArticleSearchAdapter | ArticleSearchPort | (pgvector similarity search, no LLM call) | (none) | Performs cosine similarity search via pgvector and resolves returned document IDs back to full NewsArticle domain records |
| DocumentIngestionAdapter | DocumentVectorPort | (pgvector embedding write, no LLM call) | (none) | Stores document chunks into the pgvector vector store with source file, chunk index, and source label metadata |