# AIHealthcare — Two Contradiction Systems

> Written: 2026-10-05 | Author: Bill Blackmon

The app contains two independent systems that both use the word "contradiction." They serve
different purposes, share no data or code, and should both be kept.

---

## System A — Wiki Contradictions

**What it tracks:** Domain knowledge reversals — when new evidence contradicts an established
fact in the AI-healthcare knowledge base.

**Question it answers:** *Has the field's understanding of a topic changed?*

**Example:** A wiki page on "FDA-cleared AI device outcomes" previously stated that cleared
devices show clinical benefit. A new article reports that less than 1% of cleared devices have
been tested for clinical benefit. The wiki contradiction records both claims side-by-side with
full source provenance.

**Where to see it:** `/wiki/contradictions` (global feed), `/wiki/{slug}` (per-page section)

**Where data comes from:** `WikiCompilationAdapter.persistContradiction()` — written during the
wiki compilation pipeline that runs after each article harvest. The LLM reviews new articles
against existing wiki page content and flags reversals.

**Production state (2026-10-05):** 190 rows in `wiki_contradictions`. Active and producing
high-quality signal. Sample topics: CMS cost inflation debate, RFK Jr. physician backlash,
FDA clearance vs. clinical evidence gap, Oracle Health AI coding vs. fraudulent charges.

**Key files:**
| File | Role |
|------|------|
| `domain.model.Contradiction` | Domain record: pageSlug, priorClaim, newClaim, priorSources, newSources, detectedAt |
| `infrastructure.persistence.WikiContradictionEntity` | JPA entity → `wiki_contradictions` table |
| `infrastructure.persistence.WikiContradictionRepository` | JPA repo |
| `infrastructure.ai.WikiCompilationAdapter` | Sole writer — calls `persistContradiction()` |
| `infrastructure.persistence.WikiQueryAdapter` | Reader — implements `recentContradictions(since)` |
| `domain.port.outbound.WikiQueryPort` | Port interface |
| `web.controller.WikiController` | `/wiki/contradictions` endpoint + per-page section |
| `templates/wiki-contradictions.html` | Global contradiction feed page |
| `templates/wiki-detail.html` | Per-page contradictions section (line 154) |

**Newsletter integration:** W3 Reversal Watch newsletter section draws from this table.

---

## System B — Claim Tracker CONTRADICTED Verdict

**What it tracks:** Company self-contradiction — when a frontier AI company's current public
statement conflicts with something the same company said in the past 90 days.

**Question it answers:** *Did this AI company contradict their own prior statement?*

**Example:** OpenAI claims Claude 3 is the "safest model ever" in March; a later article shows
benchmark data where it underperforms in a safety category. That `FrontierClaim` record gets
its verdict upgraded from `ALLEGED_UNVERIFIED` to `CONTRADICTED`.

**Where to see it:** `/dashboard/claims` — filter by Verdict = CONTRADICTED

**Where data comes from:** `ClaimClassifierAdapter.detectContradictions()` — runs during the
daily claim detection pipeline. Compares new claims against 90-day prior claims for the same
company using the `claim-contradiction.txt` LLM prompt. The result is stored as a `verdict`
field on the `FrontierClaim` record itself — no separate table.

**Production state (2026-10-05):** Near-zero. Expected — needs 90 days of claim history per
company to surface genuine self-contradictions. Will grow organically as the `frontier_claims`
table accumulates data.

**Key files:**
| File | Role |
|------|------|
| `domain.model.ClaimVerdict` | Enum containing CONTRADICTED as one of 5 values |
| `domain.model.FrontierClaim` | Record that carries the verdict field |
| `infrastructure.persistence.FrontierClaimEntity` | JPA entity → `frontier_claims` table |
| `infrastructure.ai.ClaimClassifierAdapter` | `detectContradictions()` method |
| `prompts/claim-contradiction.txt` | LLM prompt for cross-referencing claims |
| `web.controller.ClaimTrackerController` | `/dashboard/claims` with verdict filter |

---

## Side-by-Side Comparison

| Dimension | Wiki Contradictions (A) | Claim Tracker (B) |
|-----------|------------------------|-------------------|
| Unit of analysis | Wiki page (topic) | Individual company claim |
| Source of truth | LLM wiki compilation | 90-day claim history per company |
| Written by | `WikiCompilationAdapter` | `ClaimClassifierAdapter` |
| Storage | `wiki_contradictions` table | `verdict` column on `frontier_claims` |
| Audience | Editorial / newsletter | Subscriber accountability feed |
| Current volume | 190 rows (healthy) | ~0 (needs time to accumulate) |
| Shared code | None | None |

**Decision (2026-10-05):** Keep both. They are not redundant. Wiki contradictions track
how domain knowledge evolves; Claim Tracker contradictions track company accountability.

---

## Future Improvement

Once `frontier_claims` has 6+ months of history, consider cross-referencing new claims
against wiki contradictions (in addition to prior claims) to detect CONTRADICTED verdicts
sooner. The wiki is a richer, more curated source of domain reversals than the raw claim
history alone.
