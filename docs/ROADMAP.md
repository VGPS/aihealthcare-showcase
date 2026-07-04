# AIHealthcare — Wiki Slice Roadmap

> Order is provisional.  Each slice will be spec'd individually before implementation.
> Referenced from `CLAUDE.md`.

---

## Slice W1 — Wiki Domain Records & Ports (COMPLETE)

Domain records (`WikiPageType`, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport`),
port interfaces (`KnowledgeCompilationPort`, `WikiQueryPort`), and unit tests.
No adapters, no persistence, no Spring AI.

---

## Slice W2 — Wiki Persistence and Compilation Adapter

pgvector-backed `WikiPage` store (markdown content + embeddings in Postgres, revision history
via audit table), Spring AI/Claude adapter implementing `KnowledgeCompilationPort`, scheduler
wiring after harvest.

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

## Slice W5 — Reader-Facing Provenance

Surface `SourceRef` links in the rendered newsletter (Thymeleaf templates): every claim links
to its FDA/PubMed/ClinicalTrials origin.  Trust differentiator specific to healthcare.  Also
consider a public read-only wiki view as a future web-module feature.

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
