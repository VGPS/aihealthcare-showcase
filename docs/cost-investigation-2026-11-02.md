# LLM API Cost Investigation — 2026-11-02

Survey of every LLM-calling adapter and its trigger frequency, done to size the rules in
`CLAUDE.md` → "Production LLM Cost Discipline". Pure code/config read — no API calls were
made to produce this.

## Adapters (infrastructure/ai, infrastructure/research, infrastructure/marketanalysis/ai)

| Class / Port | Model | Batch | Prompt size driver | Pre-filter | Cache |
|---|---|---|---|---|---|
| `AiSummarizationAdapter` (AiSummarizationPort) | default (sonnet-4-6) | all articles/section, no cap on `summarize()`; `generateTopicSummary` caps at `MAX_SOURCE_DOCS=8`, `MAX_BODY_CHARS=1500` | full `bodyText()` per article (uncapped on summarize/evaluate paths) | none | no |
| `DigestSummaryAdapter` (DigestSummaryPort) | haiku (classification-model) | one call, all daily articles | title + 300-char snippet | none | no |
| `ArticleScoringAdapter` (ArticleScoringPort — tech-trend scoring) | haiku | 20/batch | title + 300-char snippet | only runs if `tech-trends.scoring-enabled=true` | no |
| `DealClassificationAdapter` (DealClassificationPort) | haiku | BATCH_SIZE=10 | title + 500-char snippet | **yes** — `DealSignalDetectionService` keyword-matches before sending candidates to LLM | no |
| `SentimentAnalysisAdapter` (SentimentAnalysisPort) | haiku | BATCH_SIZE=20 | title + 300-char snippet | none | no |
| `FrameworkAnalysisLlmAdapter` (FrameworkLlmPort) | haiku | single call, capped `MAX_ARTICLES=50` | title + 500-char snippet | none | no |
| `ClaimClassifierAdapter` (ClaimClassifierPort) | **sonnet-4-5** (`claim-classifier-model`, deliberately not haiku) | BATCH_SIZE=10 | title + 500-char body | none — runs over 1-day recent articles | no |
| `RelationshipClassificationAdapter` (RelationshipClassificationPort) | haiku | BATCH_SIZE=10 | title + 500-char snippet | none | no |
| `WikiCompilationAdapter` (KnowledgeCompilationPort) | haiku, `maxTokens=16000` | single call, all newly-harvested articles (no cap) | title/source/URL + 2000-char body per article, plus full existing-page title list | none (now freshness-gated at the scheduler level — see below) | no |
| `WikiGapAnalysisAdapter` (WikiGapAnalysisPort) | default (sonnet) | capped `MAX_ARTICLES=200` | article IDs/titles + existing page summaries | none | no |
| `ArticleBodyFormattingAdapter` | haiku | one article/call | body capped `MAX_BODY_LEN=3000` | none | no |
| `TrendExtractionAdapter` (TrendTopicExtractionPort) | default (sonnet) | all titles in one call | titles only | none | no |
| `AiEvaluationAdapter` (AiEvaluationPort, LLM-as-judge) | haiku | one call/section | full article bodies | none — runs for every generated newsletter section | no |
| `ClaudeImpactClassifierAdapter` (ImpactClassifierPort) | default | single call, all unclassified digest entries | headline+summary per entry | none | no |
| `AnthropicAiSearchAdapter` / `OpenAiSearchAdapter` / `AwsBedrockAiSearchAdapter` (AiSearchPort) | sonnet-4-6 / gpt-4o / amazon.nova-lite-v1 | one call per query per model | up to 500-char/article snippets × topK articles (default topK=20) | none | no |
| `PerplexityAiSearchAdapter` / `GeminiAiSearchAdapter` (AiSearchPort) | perplexity/sonar / gemini-3.5-flash | same as above, direct REST | same | none | no |
| `PerplexityDeepResearchAdapter` (TrendSummaryPort) | sonar deep-research | one job per topic | full agent research | capped `max-summaries-per-run: 5` | no |
| `PerplexityCompanyResearchAdapter` (CompanyResearchPort) | perplexity/sonar (3 calls/company) | capped `max-companies-per-run: 30` | agent research + JSON schema | none beyond cap | no |

No `cache_control`/prompt-caching is used anywhere in the codebase as of this survey (see
PC-1 in `docs/architecture.md` — groundwork laid, but no current prompt template meets
Anthropic's cache-eligibility minimum for the model it runs on).

## AI Search fan-out (pre-fix)

`AiSearchService.search()` fanned out to **every registered `AiSearchPort` whose
`isAvailable()==true`** when no `models` param was supplied — up to 5 parallel LLM calls per
single search (Claude + GPT + Perplexity + Gemini + Bedrock). Fixed 2026-11-02: defaults to
Claude-only unless the caller explicitly requests more models.

## Scheduler frequencies (pre-fix, from `application.yml` + scheduler classes)

- **Every 4h** (`harvest.industry-rate-ms`): INDUSTRY feed harvest → topic summaries (all 20
  topics, unconditional) + wiki compilation (maxTokens 16000) + wiki-gap analysis + deal
  detection. **= 6×/day each**, on top of the same steps running again on the daily tick.
- **Daily 04:00 UTC**: RSS (ACADEMIC/REGULATORY) harvest → same topic-summary + wiki-compile +
  gap-analysis calls, plus `StartupPipelineOrchestrator.runAllPipelines()` (framework analysis,
  frontier-claim detection, sentiment analysis, deal signals, company relationships, trend
  detection, legal-trend detection, research harvest, market digest).
- **Every 6h** (`aihealthcare.deals.schedule`): standalone deal-signal detection cron — this was
  a *duplicate* of the one embedded in the 4h industry tick (both existed simultaneously).

Fixed 2026-11-02: topic summaries/wiki-compile/gap-analysis removed from the 4h industry tick
(now only run once/day from the daily tick, across all tiers); the industry-tick deal-detection
call was removed (the standalone 6h cron is now the only sub-daily source).

## Notable existing cost controls (already in place before this investigation)

- Deal-signal keyword pre-filter — the only real content gate before an LLM call, prior to this
  investigation.
- Tiered model routing: haiku for classification/extraction, sonnet for narrative/judgment tasks
  (claim classifier explicitly kept on sonnet per a prior deliberate decision).
- Market-analysis moved daily→weekly, deep-research capped at 5/run, company-discovery capped
  at 30/run — all pre-existing.

## Biggest cost drivers found, and status

1. Topic-summary + wiki-compile + gap-analysis running 6x/day unconditionally — **fixed**
   (freshness-gated + reduced to 1x/day).
2. Duplicate deal-detection cron — **fixed** (removed the embedded industry-tick call).
3. AI Search fanning out to 5 providers by default — **fixed** (defaults to Claude-only).
4. No prompt caching anywhere — **deferred**, prompts are below the cache-eligibility minimum.
5. `ClaimClassifierAdapter` / `AiEvaluationAdapter` have no pre-filter — **not yet fixed**, see
   `CLAUDE.md` → "Production LLM Cost Discipline" → Candidate items.
