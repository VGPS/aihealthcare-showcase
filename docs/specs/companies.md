# Companies and Relationships Area — AIHealthcare Reference

> Last updated: 2026-10-05

This area covers three pages that build an internal map of the AI healthcare industry: a discovery-pipeline company directory (`/dashboard/companies`), an inter-company relationship graph (`/dashboard/relationships`), and an AI clinical trial tracker (`/dashboard/clinical-trials`). These are distinct from the public-facing `/directory` (covered in `reference.md`). The pages here are the operational intelligence layer — they show the raw output of automated discovery and relationship-detection pipelines, including validation status and confidence scores.

---

## Pages

### `/dashboard/companies` — Internal Company Directory

**What it shows:**
A sortable, filterable table of all `HealthcareAiCompany` records discovered by the automated Perplexity-powered company discovery pipeline. Each row shows: company name, sub-sector classification, funding stage, HQ location, founded year, a `validated` badge (green check if the record has been human- or algorithm-validated, gray question mark if not), and discovered/validated dates. Summary stats at the top show total count and validated count. Sub-sector filter tabs limit the list to a chosen vertical. Sort columns: name (default), sector, funding, location, discovered, founded; append `_desc` for descending.

**Data source:**
`healthcare_ai_companies` table via `HealthcareAiCompanyPort.findAll()`. The controller also calls `findAll()` a second time to build the sub-sector filter tab list — this is a double query on every page load. Dates are formatted server-side in America/New_York.

**Access / tier gating:**
No tier gating — any authenticated user can access this page. No anonymous access (Spring Security requires login for all `/dashboard/**` routes).

**How data gets here:**
`CompanyDiscoveryScheduler` runs weekly and calls `DiscoverCompaniesUseCase.discoverAll()`, which orchestrates the Perplexity-powered scraping pipeline (`StartupDirectoryHarvester`, `CompanyDiscoveryService`). The pipeline scrapes YC, TopStartups, and anchors known incumbents. Results are deduped against existing records by fuzzy name matching (`CompanyDeduplicator`). The `validated` flag is set to false on discovery and can be toggled manually. Companies can also be added manually via `POST /admin/outreach/{slug}/register-company` (see `admin.md`).

**How `/dashboard/companies` differs from `/directory`:**
`/directory` is the public-facing, SEO-optimized company showcase with signal scoring, sort tabs (Relevance/Trending/Funded), and marketing copy. `/dashboard/companies` is the internal operational view showing raw discovery metadata: validation status, funding stage, sub-sector, HQ location, and founded year — fields not surfaced on the public directory. The two pages read from the same `healthcare_ai_companies` table.

---

### `/dashboard/relationships`, `/dashboard/relationships/graph` — Company Relationship Map

**What it shows:**
`/dashboard/relationships` shows a sortable table of detected inter-company relationships: source company, target company, relationship type (PARTNERSHIP, ACQUISITION, INVESTMENT, INTEGRATION, COMPETITION, LICENSING, DISTRIBUTION), a confidence percentage, evidence article title/link, summary, and detected date. Summary badges at the top show per-type counts. A company filter dropdown narrows the list to relationships involving a specific company. Sort columns: type, source, target, confidence, detected (default: detected_desc). A "View Graph" button links to `/dashboard/relationships/graph`.

`/dashboard/relationships/graph` renders the same relationship data as an interactive node-link graph (D3.js or similar) with company nodes and typed edges. No filtering or sorting is available on the graph view — it is a visualization companion to the table page.

**Data source:**
`company_relationships` table via `MapCompanyRelationshipsUseCase.getAllRelationships()` and `getRelationshipsForCompany(company)`. The controller deduplicates results in memory before rendering: a `{sourceCompany|targetCompany|relationshipType}` composite key is used; only the first occurrence of each key survives. No dedup is done at the persistence layer.

**Access / tier gating:**
No tier gating — any authenticated user can access both pages.

**How data gets here:**
`StartupPipelineOrchestrator` runs `CompanyRelationshipService.detectRelationships()` as a cascade step after each harvest. The service scans harvested articles for co-mentions of known company names and uses LLM classification to assign a `RelationshipType` and confidence score. Evidence is linked back to the source article by `articleId`. Relationships with confidence below a configurable threshold (`aihealthcare.relationships.min-confidence`, default 0.6) are persisted but may be filtered in future UI iterations.

**Known limitations:**
- Deduplication by `{source|target|type}` is directional — a PARTNERSHIP between A→B and B→A are treated as distinct relationships and both appear in the table.
- The `confidence` field is formatted as `"%.0f%%"` (integer percent) in the controller, so values like 0.756 display as "76%". Rounding loses precision.
- `CompanyRelationshipService` looks up companies by name substring matching against the article corpus. Company names with common substrings (e.g., "AI" in "OpenAI" and "Google AI") can generate spurious relationships.
- The graph page (`/dashboard/relationships/graph`) loads all relationships at once with no pagination or filtering — performance degrades as relationship count grows.

---

### `/dashboard/clinical-trials` — AI Clinical Trial Tracker

**What it shows:**
A sortable, filterable table of AI-related clinical trials. Each row shows: NCT ID (linked to ClinicalTrials.gov), trial title, sponsor, phase (PHASE_1 through PHASE_4, OBSERVATIONAL), status (RECRUITING, ACTIVE_NOT_RECRUITING, COMPLETED, TERMINATED, etc.), conditions list, and start date. Summary badges at the top show counts of recruiting trials, completed trials, Phase 2 trials, and Phase 3 trials. Filter buttons: "All", "Recruiting", "Completed". Sort columns: status (default), title, sponsor, phase, conditions, nctid; append `_desc` for descending.

**Data source:**
`clinical_trials` table via `MonitorClinicalTrialsUseCase.getRecentTrials(limit)` and `getTrialsByStatus(status, limit)`. Start dates are formatted server-side in America/New_York.

**Access / tier gating:**
- FREE: limited to 5 trials (FREE_TRIAL_LIMIT = 5).
- SUBSCRIBER / DEMO / ADMIN: up to 50 trials (FULL_TRIAL_LIMIT = 50).
- No anonymous access.

**How data gets here:**
`ClinicalTrialHarvestScheduler` (separate from the main RSS harvest) calls `MonitorClinicalTrialsUseCase.triggerHarvest()` on a configured schedule (cron key: `aihealthcare.clinical-trials.schedule`). The harvester queries the ClinicalTrials.gov REST API for studies matching AI/machine learning search terms in the healthcare domain. Results are deduped by NCT ID before persisting to `clinical_trials`. A manual trigger is available via `POST /monitoring/clinical-trials-harvest` (ADMIN; accessible from the Pipeline page).

**Known limitations:**
- `sortTrials()` is implemented in-memory in the controller as an insertion-sort loop over the already-fetched list. For large trial sets this is O(n²). A database-level ORDER BY would be more appropriate.
- The conditions column displays `trial.conditions().get(0)` for sort purposes, which means sorting by conditions is actually sorting by the alphabetically first condition, not the full condition set.
- Trials terminated or withdrawn by sponsors are not automatically removed from the table — they remain visible until the harvest pipeline overwrites them (upsert behavior) or they are manually deleted.
- The 50-trial cap (FULL_TRIAL_LIMIT) applies to all three load paths: recent, recruiting, and completed. If there are 200 recruiting trials, SUBSCRIBER users still see only 50 with no indication that the list is truncated.

---

## How Data Gets In

| Pipeline | Trigger | What it writes |
|---|---|---|
| `CompanyDiscoveryScheduler` | Weekly | `healthcare_ai_companies` — company records from scraping pipeline |
| `StartupPipelineOrchestrator` (cascade after harvest) | After every RSS harvest | `company_relationships` — LLM-detected inter-company relationships |
| `ClinicalTrialHarvestScheduler` | Scheduled (cron: `aihealthcare.clinical-trials.schedule`) | `clinical_trials` — trials from ClinicalTrials.gov API |
| Manual: `POST /monitoring/clinical-trials-harvest` | On demand (ADMIN) | Triggers immediate harvest into `clinical_trials` |
| Manual: `POST /admin/outreach/{slug}/register-company` | On demand (ADMIN) | Adds a single company record to `healthcare_ai_companies` |

---

## Cross-References

- `/dashboard/companies` and `/directory` read from the same `healthcare_ai_companies` table. The public directory shows marketing-facing data (signal scores, descriptions); this page shows operational data (validation status, discovered/validated dates, funding stage, sub-sector, location).
- `/dashboard/relationships` evidence articles link back to records in `news_articles`. The `evidenceArticleId` field on each relationship can be used to retrieve the source article from `ArticleIngestionPort`.
- `/dashboard/clinical-trials` trial records are independent of the article corpus — they come from ClinicalTrials.gov, not from the RSS/Perplexity harvest pipeline.
- The company dropdown on `/dashboard/relationships` is populated from the deduplicated list of company names present in the `company_relationships` table, not from `healthcare_ai_companies`. A company that is in the directory but has no detected relationships will not appear in the filter dropdown.
