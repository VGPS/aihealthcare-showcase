# WK-OKF — Wiki OKF Bundle Export

**Status:** PLANNED  
**Effort:** Medium (1 session)  
**API cost:** Zero — pure structural export, no LLM calls  
**Prerequisites:** W7 complete (wiki hybrid search) ✅

---

## What is OKF?

Google Cloud's [Open Knowledge Format](https://github.com/GoogleCloudPlatform/open-knowledge-format)
(v0.2) is a universal, vendor-neutral format for representing knowledge as plain markdown files
with YAML frontmatter organized in a directory hierarchy (a "bundle"). It is designed to be:

- Human- and agent-readable without tooling
- Version-controllable in git
- Portable (a bundle is just a directory)
- Provenance-first: `sources`, `generated`, `verified`, `stale_after` are first-class fields

The AIHealthcare wiki layer already carries all of these properties internally (slug, title,
pageType, tags, contentMarkdown, SourceRef list, relatedSlugs, revision). OKF is a standardized
export format that makes those properties externally visible and interoperable.

---

## Why This Fits AIHealthcare

The AIHealthcare wiki (Slices W1–W7) is the most defensible long-term asset of the app — a
self-compounding, LLM-compiled knowledge base about AI-in-healthcare. Right now it is locked in
Postgres, visible only through the Thymeleaf wiki UI. OKF export would:

1. **Make the wiki portable and versioned.** A git-committed `wiki-bundle/` directory gives buyers,
   collaborators, or subscribers a diff-able history of every wiki update. This is a material
   selling point in an app acquisition context.

2. **Enable downstream tooling with no code changes.** Obsidian, MkDocs, Hugo, and similar tools
   already consume markdown+YAML frontmatter. The bundle can be browsed immediately.

3. **Generate a stunning interactive knowledge graph.** The OKF reference repo ships a
   `viz.html` generator (self-contained HTML using Cytoscape.js + marked.js, no backend) that
   renders every wiki page as a force-directed graph with cross-links as edges. This is a
   demo-ready artifact for the app sale or marketing content.

4. **Align the wiki provenance model with an open standard.** OKF's `sources` frontmatter
   (credibility signals, author, last_modified) is a richer version of what `SourceRef` already
   tracks. Exporting in OKF makes the provenance model externally legible.

5. **Zero API cost.** No LLM calls — pure serialization of existing Postgres data.

---

## Mapping WikiPage → OKF Concept Document

```yaml
---
type: AI Healthcare Wiki Page      # pageType.name() used as a qualifier
title: <WikiPage.title>
description: <first sentence of contentMarkdown>
resource: https://bigskylabs.ai/wiki/<slug>
tags: <WikiPage.tags>
generated:
  by: WikiCompilationAdapter/claude-haiku-4-5
  at: <WikiPage.updatedAt ISO-8601>
status: current                   # "stale" if updatedAt > 90d ago
stale_after: <updatedAt + 90d>
sources:
  - url: https://bigskylabs.ai/dashboard/news?topic=<topic>
    title: <SourceRef.sourceName>
    last_modified: <SourceRef.harvestedOn ISO-8601>
    credibility: machine-generated
# OKF extension fields (non-breaking)
revision: <WikiPage.revision>
---

<WikiPage.contentMarkdown>

<!-- cross-references become standard markdown links -->
See also: [Related Page Title](/wiki/<relatedSlug>.md)
```

**Directory layout:**
```
wiki-bundle/
  index.md                        # generated: lists all pages by type
  ENTITY/
    index.md
    fda-ai-device-guidance.md
    epic-ehr-ai-integration.md
    ...
  CONCEPT/
    index.md
    machine-learning-clinical-decision-support.md
    ...
  COMPARISON/
    index.md
    ...
  OVERVIEW/
    index.md
    ...
```

---

## Implementation Plan

### Domain (no changes required)
The `WikiPage` record already has everything needed. No new domain types.

### New port (optional)
If we want to follow hexagonal conventions:
- `WikiBundleExportPort` in `domain.port.outbound`:
  - `exportBundle(List<WikiPage> pages, List<Contradiction> contradictions, Path outputDir)`
  - Returns `WikiBundleExportResult` record (pageCount, contradictionCount, bundleDir)

This is optional — an internal service calling the exporter directly is acceptable since export
is a delivery-side concern, not a domain concern.

### New infrastructure adapter
`OkfBundleExporterAdapter` in `infrastructure.delivery`:
- Accepts `List<WikiPage>` + `List<Contradiction>` from `WikiQueryPort` and `WikiContradictionRepository`
- Writes OKF markdown files to a configurable output directory
- Generates `index.md` files at each directory level
- Cross-references: maps `relatedSlugs` to `[Title](/CONCEPT/slug.md)` markdown links
- Contradictions: each `Contradiction` becomes a concept doc under `CONTRADICTIONS/`
  with `type: Contradiction`, prior/new claim in body, sources on both sides

Config:
```yaml
aihealthcare:
  wiki:
    okf-export-dir: ${user.home}/wiki-bundle   # or /var/lib/aihealthcare/wiki-bundle on EC2
    okf-export-cron: "0 30 9 * * *"            # daily 09:30 UTC, 30 min after WikiEmbeddingScheduler
```

### New scheduler (optional)
`WikiBundleExportScheduler` in `infrastructure.scheduler` — daily export cron. Lightweight:
load all wiki pages via `WikiQueryPort.findRelevantPages("", Integer.MAX_VALUE)`, write bundle,
log count.

If a scheduler feels like overkill, wire it into `FeedHarvestScheduler` as a final step in the
post-harvest cascade (Step 16 in `StartupPipelineOrchestrator`).

### New admin endpoint
`POST /admin/wiki/export` on `PipelineManagementController` — manual trigger card on the
Pipeline Management page (same pattern as the existing "Run Harvest" and "Compile Wiki" cards).

### viz.html generation (Phase 2)
The OKF reference agent's `visualize` subcommand is Python. For Phase 1, skip this — the bundle
is fully usable by Obsidian/MkDocs/git without it. For Phase 2, either:
- Port the Cytoscape.js logic to a Thymeleaf template served at `GET /wiki/graph` (self-contained,
  no Python dependency), or
- Run the reference agent's Python visualizer offline as a documentation artifact.

---

## File Checklist

| File | Type | Action |
|------|------|--------|
| `domain.model.WikiBundleExportResult` | record | NEW — pageCount, contradictionCount, bundleDir String |
| `domain.port.outbound.WikiBundleExportPort` | interface | NEW (optional) |
| `infrastructure.delivery.OkfBundleExporterAdapter` | @Component | NEW |
| `infrastructure.scheduler.WikiBundleExportScheduler` | @Component | NEW (or inline in orchestrator) |
| `application.yml` | config | ADD `aihealthcare.wiki.okf-export-dir`, `okf-export-cron` |
| `PipelineManagementController` | existing | ADD manual export endpoint |
| `admin-pipelines.html` | existing | ADD "Export Wiki Bundle" trigger card |

---

## Tests

| Test class | Count | What it covers |
|-----------|-------|---------------|
| `OkfBundleExporterAdapterTest` | 8 | frontmatter generation, cross-link rewriting, index.md generation, contradiction export, stale status detection, empty-wiki guard |
| `WikiBundleExportSchedulerTest` | 3 | scheduling wiring, success path, exception-swallow behavior |

---

## Done Criteria
- [ ] `POST /admin/wiki/export` triggers bundle generation, returns JSON `{pageCount, contradictionCount, bundleDir}`
- [ ] Generated `wiki-bundle/` has one `.md` file per wiki page, organized by pageType directory
- [ ] Each file has valid OKF YAML frontmatter (type, title, description, tags, generated, sources)
- [ ] `relatedSlugs` appear as `[Title](/TYPE/slug.md)` markdown links in the body
- [ ] `index.md` files exist at root and each type subdirectory
- [ ] All 8+ unit tests pass with no LLM or DB calls
- [ ] `mvn test -Dtest="OkfBundleExporterAdapterTest,WikiBundleExportSchedulerTest"` is green
