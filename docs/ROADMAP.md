# AIHealthcare — Wiki Slice Roadmap

> Order is provisional.  Each slice will be spec'd individually before implementation.
> Referenced from `CLAUDE.md`.

---

## Slice W1 — Wiki Domain Records & Ports (COMPLETE)

Domain records (`WikiPageType`, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport`),
port interfaces (`KnowledgeCompilationPort`, `WikiQueryPort`), and unit tests.
No adapters, no persistence, no Spring AI.

---

## Slice W2 — Wiki Persistence and Compilation Adapter (COMPLETE)

JPA entities (`WikiPageEntity`, `WikiSourceRefEntity`, `WikiContradictionEntity`,
`WikiPageRevisionEntity`, `CompilationReportEntity`), port adapters (`WikiQueryAdapter`,
`CompilationReportAdapter`), Spring AI/Claude adapter (`WikiCompilationAdapter` implementing
`KnowledgeCompilationPort`), `WikiResponseParser`, prompt template, REST trigger at
`POST /monitoring/wiki/compile`, `FeedHarvestScheduler` wiring, 800 tests.

---

## Slice W3 — Reversal Watch

A recurring newsletter section generated from `Contradiction` records: regulatory U-turns,
walked-back vendor claims, failed replications.  Requires a query in `WikiQueryPort` (already
present as `recentContradictions`) plus a `NewsletterSection` producer.  This is the moat made
visible to readers — no stateless competitor can produce it.

---

## Slice W4 — Topic Timelines

Auto-generated "story so far" wiki pages per `Topic` (the record is already first-class and
config-driven).  Each newsletter links to the compiled history; over time these become standalone
reference assets.  Likely a new `WikiPageType.TIMELINE`.

---

## Slice W5 — Reader-Facing Provenance (COMPLETE)

Public read-only wiki view: `WikiController` with 3 Thymeleaf pages (`wiki-index.html`,
`wiki-detail.html`, `wiki-contradictions.html`). Wiki detail page surfaces `SourceRef` links
in a provenance table — every claim links to its FDA/PubMed/ClinicalTrials origin. CommonMark
Java library renders markdown content. Reversal Watch contradiction feed at `/wiki/contradictions`.
808 tests.

---

## Slice W6 — Wiki Linter

A scheduled agent pass that checks: orphaned pages, broken `relatedSlugs` cross-references,
stale pages (no update since threshold), missing provenance, and (deep mode) factual conflicts
between tag-overlapping pages.  Output is a lint report persisted alongside `CompilationReport`.

---

## Slice W7 — Evaluation Harness

RAGAS-style faithfulness/relevance evals run against wiki-grounded summaries, behind the existing
AI-profile flag to control cost.  Persist scores over time; eventually publish headline numbers
("summaries verified against sources") as a credibility claim.

---

## Slice W8 — Domain-Pack Extraction (Multi-Vertical Readiness)

Enforce and formalize the separation between the domain-agnostic engine and vertical-specific
content so the platform can be replicated for other verticals (e.g. AI Finance, AI Education)
with minimal code changes.  Deliverables:

1. Architecture test or review checklist asserting no healthcare-specific strings, sources, or
   prompts exist in `domain`, `application`, or core `infrastructure` code.
2. Per-vertical configuration profile (YAML) holding topics, feed URLs, and API endpoints.
3. `domain-pack/` skills folder externalizing editorial intelligence as prose — compilation
   prompt, editorial voice, domain glossary, linter rules, and provenance/citation standards.
4. Documentation of the replication recipe: new vertical = skill folder + YAML profile +
   1–2 harvesting adapters + Thymeleaf theme.

Note: the compiled wiki itself intentionally does not transfer — each vertical accumulates its
own knowledge asset, making replication a product line rather than dilution.  From W2 onward,
prefer designs that keep healthcare specifics out of core modules so W8 is an extraction,
not a rewrite.

---

## Ongoing — Documented Construction as a Class Asset

Keep SDD specs, `CompilationReport` logs, and session transcripts organized so the course can
show the system being built decision-by-decision.  The codebase can be cloned; the documented
process cannot.
