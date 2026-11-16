# Slice: Copyright-Safe Content Caching & Retention Layer

**Project:** AIHealthcare (Spring AI)
**Owner:** Bill Blackmon
**Status:** Ready for implementation
**Depends on:** existing Spring AI / Anthropic chat integration (`application.yml`, `ANTHROPIC_API_KEY` via `.env`)

---

## 1. Problem

The RAG pipeline currently has (or risks having) no architectural boundary between
"content we retrieved to answer a question" and "content we are effectively archiving
and re-serving." Recent litigation (NYT v. Perplexity, News Corp v. Perplexity) turns on
three fact patterns this slice exists to make structurally impossible:

1. Storing/serving **full or near-full article text** instead of short excerpts.
2. **No expiry** — indefinite retention that looks like a competing archive.
3. **No attribution/link-out**, so the app substitutes for the original rather than
   sending traffic to it.
4. **Acquisition that ignores `robots.txt` / ToS / paywalls.**

None of this is about the LLM call itself — it's about what sits in the database
between ingestion and generation. The fix is a caching layer that enforces the limits
by construction, not by prompt instructions alone.

## 2. Goals

- Never persist full source text for non-licensed content.
- Every cached record has an expiry and a background sweep that enforces it.
- Every cached record carries enough metadata to render a source link + attribution.
- Ingestion refuses sources disallowed by `robots.txt` or an explicit denylist.
- Licensed/contracted sources (e.g., a paid clinical database with full-text rights)
  are the *only* path allowed to store full text, and only under their own retention
  contract terms.

## 3. Non-goals

- This slice does not change how the LLM is prompted or how answers are phrased
  (paraphrase-quality is a prompt/eval concern, tracked separately).
- Not building a full document-management system — this is a thin retrieval cache.

## 4. Data model

Target: Postgres + `pgvector` (adjust if the project uses a different vector store).

```sql
CREATE TYPE license_status AS ENUM ('public_scrape', 'public_api', 'licensed');

CREATE TABLE source_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_url        TEXT NOT NULL,
    source_domain     TEXT NOT NULL,
    title             TEXT,
    author            TEXT,
    published_at      TIMESTAMPTZ,
    license_status    license_status NOT NULL DEFAULT 'public_scrape',

    -- Content actually stored. For public_scrape/public_api this is a bounded
    -- excerpt only. Full text is ONLY permitted when license_status = 'licensed'
    -- AND the licensing contract explicitly grants storage rights.
    excerpt           VARCHAR(750) NOT NULL,
    full_text         TEXT,                     -- nullable; see constraint below
    embedding         VECTOR(1536) NOT NULL,

    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,      -- enforced, see §6
    last_served_at    TIMESTAMPTZ,

    CONSTRAINT full_text_requires_license
        CHECK (full_text IS NULL OR license_status = 'licensed')
);

CREATE INDEX idx_source_documents_expiry ON source_documents (expires_at);
CREATE INDEX idx_source_documents_embedding ON source_documents
    USING ivfflat (embedding vector_cosine_ops);
```

Notes:
- `full_text_requires_license` is a hard DB constraint, not just app logic — a
  misconfigured scraper physically cannot write full text for a public source.
- `excerpt` cap (750 chars) is a starting point; tune per source type, but keep it
  well under "most of the article."

## 5. Excerpt truncation rules

Implement as a single utility (e.g. `ExcerptTruncator`) called at ingestion time,
never at render time — the DB should never contain more than what's allowed to be shown.

- Hard cap: `min(750 chars, 15% of original length)`.
- Trim at a sentence boundary (don't cut mid-sentence).
- Strip to a single representative passage, not the article's opening paragraphs
  verbatim (opening paragraphs are often the most reused/most protected part —
  prefer a compressed/paraphrased summary generated once at ingestion, stored
  instead of a raw slice, when feasible).
- Never concatenate multiple excerpts from the same source into one longer block.

## 6. TTL / retention policy

| Source type | Default TTL | Notes |
|---|---|---|
| General news / scraped web | 14 days | Aggressive — news value decays fast, and this is the highest-litigation-risk category |
| Public health APIs (PubMed abstracts, NIH, CDC, clinicaltrials.gov) | 90 days | Lower risk, but still not indefinite |
| Licensed clinical databases | Per contract | Store the contract's retention term in a config table, not hardcoded |

Implementation:
- `expires_at` set at insert time from a config-driven TTL-by-source-type map.
- A `@Scheduled` job (e.g. hourly) deletes rows where `expires_at < now()`.
- Log deletions (count + domains) for auditability; don't log deleted content itself.
- Re-ingestion after expiry is just a normal fresh fetch — no "renewal" of old rows.

## 7. Acquisition rules

- Before first fetch from a domain, fetch and cache that domain's `robots.txt`
  (cache it with its own short TTL, e.g. 24h) and check the path is allowed.
- Maintain a simple `source_domain_policy` table: `domain`, `allowed (bool)`,
  `license_status_default`, `notes`. Denylist wins over robots.txt allow.
- Refuse paywalled content reached by any bypass technique — if a fetch requires
  circumventing a paywall or auth wall, the ingestion job should fail closed, not
  silently succeed.
- Rate-limit per domain to avoid look-alike-to-scraping traffic patterns.

## 8. Serving / rendering rules

- Every answer built from `source_documents` rows must include, per source used:
  `title`, `source_url`, `source_domain`. The link-out is mandatory, not optional
  UI — enforce it in the response-assembly code, not just the prompt.
- Update `last_served_at` on read (useful later for "was this ever used" audits
  and for prioritizing what's safe to let expire early).
- Optional guardrail: a lightweight n-gram overlap check between the generated
  answer and the stored excerpt; if overlap exceeds a threshold (e.g. 8
  consecutive words), flag for review/logging rather than blocking silently —
  this catches prompt regressions that drift toward near-verbatim output.

## 9. Migration for existing data

1. Audit any existing cache/vector-store tables for full-text columns.
2. For rows without a `license_status`, default to `public_scrape`.
3. Backfill `excerpt` from existing full text using the truncation rule in §5;
   null out `full_text` for anything not `licensed`.
4. Backfill `expires_at` using the TTL table in §6, anchored to `fetched_at`
   (not `now()`, so already-old content expires immediately if past its TTL).
5. Run the migration in a dry-run/report mode first — print counts of rows that
   would be truncated/deleted per domain before actually mutating data.

## 10. Suggested file layout (Spring AI conventions)

```
src/main/java/.../retention/
    SourceDocument.java              (entity, maps to §4 schema)
    LicenseStatus.java               (enum)
    ExcerptTruncator.java            (§5)
    RetentionPolicy.java             (TTL-by-source-type config, §6)
    RetentionSweepJob.java           (@Scheduled purge job)
    RobotsTxtGate.java               (§7)
    SourceDomainPolicyRepository.java
    AnswerAttributionAssembler.java  (§8 — enforces link-out on every response)
```

## 11. Acceptance criteria

- [ ] `full_text_requires_license` constraint present and verified with a failing
      insert test (public_scrape + non-null full_text must reject).
- [ ] `ExcerptTruncator` unit tests: sentence-boundary trimming, cap enforcement,
      no full-article passthrough.
- [ ] `RetentionSweepJob` integration test: seeds expired + non-expired rows,
      confirms only expired rows are deleted.
- [ ] `RobotsTxtGate` test: disallowed path blocks ingestion; allowed path proceeds;
      denylist overrides an allow.
- [ ] `AnswerAttributionAssembler` test: any answer citing a `source_documents`
      row includes that row's `source_url` in the response payload.
- [ ] Migration dry-run report generated and reviewed before running for real
      against any existing cache table.

## 12. Open questions for Bill

- What's the actual current storage layer (pgvector, a separate vector DB, or
  in-memory)? Schema above assumes Postgres + pgvector per the Spring AI
  reference already in the project's stack.
- Are any current sources under an actual license agreement (e.g., a paid
  medical literature API), or is everything currently `public_scrape` /
  `public_api`?
- Desired TTL for the news category — 14 days is a conservative starting point,
  adjust to product needs.
