# AIHealthcare — Production LLM Cost Discipline

> Governs the **deployed app's own** Anthropic/OpenAI/Perplexity/Gemini API spend.
> Distinct from "Session Cost Discipline" in CLAUDE.md, which governs Claude Code's behavior.
> Initiative started 2026-11-02, targeting a 50% cut in November API spend vs. October's baseline.
> See `docs/cost-investigation-2026-11-02.md` for the full adapter/scheduler survey this was sized from.
>
> Keep this checklist current as rules are implemented, reverted, or superseded — each entry
> carries its own status + date so this stays a log, not a wishlist.

---

## Implemented

- [x] **Freshness-gate topic summaries and wiki compilation.** `TopicSummaryGenerationService.generateSummaries()`
      skips a topic when no article has `publishedAt` after that topic's last saved summary.
      `FeedHarvestScheduler.compileWikiPages()` skips entirely when no article in the batch was
      published after the most recent `CompilationReport`. If no article carries a known
      `publishedAt`, both gates err toward regenerating rather than silently going stale forever.
      *(2026-11-02)*

- [x] **AI post-processing runs once per day, not 6x/day.** Topic summaries, wiki compilation,
      and wiki gap analysis only run from `FeedHarvestScheduler.harvestDailyFeeds()` (once daily,
      across every harvested tier, not just ACADEMIC/REGULATORY). `harvestIndustryFeeds()`
      (every 4h) stays limited to article storage, wiki lint (rule-based, no LLM call), and
      watchlist matching — no AI calls on that tick. *(2026-11-02)*

- [x] **No duplicate deal-detection crons.** Deal-signal detection on a sub-daily cadence runs
      from exactly one place: `StartupPipelineOrchestrator.runDealDetectionCron()` (every 6h, via
      `aihealthcare.deals.schedule`). The call embedded in `FeedHarvestScheduler.harvestIndustryFeeds()`
      was removed — it duplicated the standalone cron at 1.5x the frequency for no added value.
      *(2026-11-02)*

- [x] **AI Search defaults to one model, not every available provider.** `AiSearchService.search()`
      defaults to Claude-only when the caller omits `modelNames` — matching the UI's documented
      default — instead of fanning out to every registered `AiSearchPort` (up to 5 providers:
      Claude/GPT/Perplexity/Gemini/Bedrock). Falls back to all available models only if Claude
      itself is unavailable. Multi-model comparison stays available via an explicit `modelNames`
      list. *(2026-11-02)*

---

## Candidate — Not Yet Implemented

- [ ] **Pre-filter before `ClaimClassifierAdapter` calls**, mirroring the existing deal-signal
      keyword pre-filter in `DealSignalDetectionService`. Every article in the 1-day scan window
      currently reaches the LLM with no cheap gate first.

- [ ] **Sample or gate `AiEvaluationAdapter`** (LLM-as-judge) instead of running it on every
      generated newsletter section with no pre-filter. Confirm it's still load-bearing before
      cutting — it may be an internal QA signal nobody currently acts on.

- [ ] **Prompt caching** — deferred. Most current prompts (batch sizes of 10–20 articles ×
      300–500 char snippets) are below Anthropic's cache-eligibility minimum (4,096 tokens for
      Haiku, the model most adapters use). Revisit only if prompts are restructured to batch more
      static content per call.

- [ ] **Downgrade select Sonnet-tier extraction tasks to Haiku** (`TrendExtractionAdapter`,
      `ClaudeImpactClassifierAdapter`, `WikiGapAnalysisAdapter`) — these look like structured
      extraction rather than narrative judgment, but confirm with a side-by-side quality check
      before switching; `ClaimClassifierAdapter` was *deliberately* kept on Sonnet and should stay
      that way absent a specific decision to revisit it.

- [ ] **Replace the scheduled Perplexity Deep Research trend-summary job** (`PerplexityDeepResearchAdapter`,
      weekly via `TrendDetectionScheduler`, capped at 5 jobs/run) with Claude synthesizing over
      articles already in the DB — same pattern `TopicSummaryGenerationService` already uses.
      Sonar Deep Research runs many sub-searches per call internally and is priced well above a
      normal Sonar/Sonar-Pro call even at this low volume (small-to-moderate, bounded win).
      Tradeoff: loses live web research depth — trend summaries would be scoped to what the
      feeds already harvested that week. Do **not** touch the on-demand AI Search deep-research
      option (`PerplexityDeepResearchAiSearchAdapter`) — it's user-opt-in and already priced at
      10 credits vs. 1 for a regular Perplexity search, so its spend already self-limits.
      *(Flagged 2026-11-02)*

---

## Verifying the 50% Target

These rules were sized from the code (call frequency × batch size × model tier), not from
measured spend — `llm_call_usage` (PC-2) has only 14 rows from a 2026-10-18 smoke test, not a
full month of live production data. To confirm the actual November-vs-October reduction, check:

- The admin dashboard's "7-day cost by pipeline/model" panel (PC-2), once it has enough data, or
- The Anthropic console's usage/billing page, comparing October and November totals.

If the target isn't hit, the next levers are the "Candidate" items above — in priority order:
the claim-classifier pre-filter (same mechanism that already works for deal signals) first, then
the AiEvaluationAdapter gate.
