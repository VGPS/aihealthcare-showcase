# AIHealthcare — Domain Model Reference

> Extracted from CLAUDE.md 2026-11-14 to keep the base file compact.
> Update this file when adding new domain records, ports, or modifying `NewsArticle`.

---

## Core Domain Types

| Record / Interface               | Package                   | Notes                                                      |
|----------------------------------|---------------------------|------------------------------------------------------------|
| `NewsArticle`                    | `domain.model`            | Harvested/ingested article; carries source metadata fields |
| `Topic`                          | `domain.model`            | Newsletter topic (name, slug, promptContext, tone, active) |
| `NewsletterSection`              | `domain.model`            | AI-summarized section for one topic                        |
| `NewsletterDraft`                | `domain.model`            | Full draft: intro + sections + sources                     |
| `ArticleIngestionPort`           | `domain.port.outbound`    | Fetch stored articles for a topic/keyword (use-case side)  |
| `ArticleHarvestingPort`          | `domain.port.outbound`    | Bulk-harvest raw articles from all configured feeds        |
| `AiSummarizationPort`            | `domain.port.outbound`    | Summarize articles via AI                                  |
| `IngestArticlesUseCase`          | `domain.port.inbound`     | Inbound port — drive ingestion                             |
| `GenerateNewsletterUseCase`      | `domain.port.inbound`     | Inbound port — drive generation                            |
| `AiSearchResult`                 | `domain.model`            | Wraps query + topK + selected model names                  |
| `AiSearchSynthesis`              | `domain.model`            | Single model's synthesis (summary + key findings)          |
| `Company`                        | `domain.model`            | AI healthcare company (name, url, categories, description) |
| `CompanyDiscoveryResult`         | `domain.model`            | Company discovery pipeline result                          |
| `ConductAiSearchUseCase`         | `domain.port.inbound`     | Inbound port — multi-model AI search                       |
| `DiscoverCompaniesUseCase`       | `domain.port.inbound`     | Inbound port — company discovery pipeline                  |
| `AiSearchPort`                   | `domain.port.outbound`    | AI model synthesis adapter interface                       |
| `TrendSignal`                    | `domain.model`            | Keyword trend signal (30/90/180-day frequency + momentum)  |
| `TrendSnapshot`                  | `domain.model`            | Point-in-time snapshot of rising/fading/new keyword trends |
| `DetectTrendsUseCase`            | `domain.port.inbound`     | Inbound port — trend detection + latest/all snapshot retrieval |
| `TrendSnapshotPort`              | `domain.port.outbound`    | Persist and query trend snapshots                          |
| `RegulatoryEventType`            | `domain.model`            | Enum: FDA_510K_CLEARANCE, DE_NOVO, PMA, CMS rules, etc.   |
| `RegulatoryBody`                 | `domain.model`            | Enum: FDA, CMS, ONC, OTHER                                |
| `RegulatoryEvent`                | `domain.model`            | 13-field record: regulatory event with ref#, applicant, device |
| `MonitorRegulatoryEventsUseCase` | `domain.port.inbound`     | Inbound port — regulatory event retrieval + harvest trigger |
| `RegulatoryEventPort`            | `domain.port.outbound`    | Persist and query regulatory events                        |
| `RegulatoryHarvestingPort`       | `domain.port.outbound`    | Harvest regulatory events from external APIs               |
| `SentimentLabel`                 | `domain.model`            | Enum: POSITIVE, NEGATIVE, MIXED, NEUTRAL                   |
| `ArticleSentiment`               | `domain.model`            | Per-article sentiment: articleId, title, label, confidence, rationale |
| `CompanySentiment`               | `domain.model`            | Aggregated company sentiment: slug, score, counts, riskSummary |
| `AnalyzeCompanySentimentUseCase` | `domain.port.inbound`     | Inbound port — company sentiment analysis + retrieval      |
| `SentimentAnalysisPort`          | `domain.port.outbound`    | LLM-powered article sentiment classification               |
| `CompanySentimentPort`           | `domain.port.outbound`    | Persist and query company sentiments                        |
| `FrameworkAnalysis`              | `domain.model`            | 10-field record: competitive analysis with 6 dimensions + strengths/weaknesses |
| `FrameworkDimension`             | `domain.model`            | Scored dimension (name, score 1-10, rationale)             |
| `FrameworkCompany`               | `domain.model`            | Config record: slug, name, url, topics for article matching |
| `AnalyzeFrameworksUseCase`       | `domain.port.inbound`     | Inbound port — framework competitive analysis              |
| `FrameworkAnalysisPort`          | `domain.port.outbound`    | Persist and query framework analyses                        |
| `FrameworkLlmPort`               | `domain.port.outbound`    | LLM-powered 6-dimension competitive scoring                 |
| `DealContext`                    | `domain.model`            | Enrichment wrapper: DealSignal + sentiment/framework/regulatory/profile |
| `DealClassificationPort`         | `domain.port.outbound`    | LLM-powered deal classification from candidate articles     |

---

## `NewsArticle` Field Inventory (11 fields)

```
articleId    String   required, non-blank (entry URI, link URL, or UUID fallback)
title        String   required, non-blank
url          URI      required, non-null
bodyText     String   optional (RSS descriptions can be empty)
topic        String   required, non-blank (feed name or search keyword for grouping)
author       String   optional (null if not determinable)
topicId      Long     optional — null in Slice 1; populated from FeedSourceConfig in Slice 2
sourceName   String   optional — feed label (e.g. "PubMed AI Healthcare")
sourceTier   String   optional — "ACADEMIC", "REGULATORY", or "INDUSTRY"
sourceWeight double   baseline relevance multiplier [0.0, 1.0]; 0.5 for unknown
publishedAt  Instant  optional — null if not determinable
```

`bodyText` non-blank validation was intentionally relaxed — RSS descriptions are frequently empty.

---

## LLM Wiki Domain Records

| Record           | Package        | Purpose                                                        |
|------------------|----------------|----------------------------------------------------------------|
| `WikiPageType`   | `domain.model` | Enum: `ENTITY`, `CONCEPT`, `COMPARISON`, `OVERVIEW`           |
| `SourceRef`      | `domain.model` | Provenance link: articleId + sourceName + harvestedOn + excerpt |
| `WikiPage`       | `domain.model` | Compiled wiki page with slug, tags, markdown, sources, cross-refs |
| `Contradiction`  | `domain.model` | Prior claim vs new claim with source lists on both sides       |
| `CompilationReport` | `domain.model` | Run summary: pages created/updated, contradictions, warnings |

### Wiki Port Interfaces

| Port                    | Package                  | Methods                                                        |
|-------------------------|--------------------------|----------------------------------------------------------------|
| `KnowledgeCompilationPort` | `domain.port.outbound` | `compileNewSources(List<NewsArticle>)` → `CompilationReport` |
| `WikiQueryPort`         | `domain.port.outbound`   | `findRelevantPages(query, maxResults)`, `getPage(slug)`, `recentContradictions(since)` |

---

## Feed Harvesting YAML Shape

```yaml
aihealthcare:
  feeds:
    sources:
      - topic-id: 1
        name: PubMed AI Healthcare
        url: https://pubmed.ncbi.nlm.nih.gov/rss/search/?term=artificial+intelligence+healthcare&format=rss
        tier: ACADEMIC        # ACADEMIC | REGULATORY | INDUSTRY
        base-weight: 0.9      # [0.0, 1.0]
        max-items: 50
```
