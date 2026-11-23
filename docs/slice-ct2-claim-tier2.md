# Slice CT-2: Claim Tracker Tier 2 — Structured Hype Fields as First-Class DB Columns

**Project:** AIHealthcare (Spring AI)
**Owner:** Bill Blackmon
**Status:** Ready for implementation
**Depends on:** CT-1 (Frontier Claim Tracker), `claim-classifier.txt` Tier 1 prompt improvements
**Created:** 2026-11-24

---

## 1. Problem

The Tier 1 prompt improvements (commit `3caf604c`) added three structured fields to every
MARKETING_HYPE `evidenceNotes` value:

```
[SEVERITY:SEVERE] [CONTEXT:CLINICAL] [TRIGGER:"detects sepsis 6 hours earlier"] rationale sentence.
```

These are parsed at display time by an Alpine.js regex in the browser. That works for rendering
but means:

- **No filtering**: can't query "show me only SEVERE claims" — the value is locked in a TEXT column
- **No sorting**: can't rank claims by severity
- **No aggregation**: can't produce a "SEVERE claim count" per company for scorecards
- **No watchlist integration**: SEVERE clinical claims can't trigger alerts
- **evidenceNotes is polluted**: the rationale text is buried after the prefix, making it hard
  to read raw DB records or use in future prompts

---

## 2. Goals

- Extract `hypeSeverity`, `triggeringPhrase`, and `deploymentContext` into proper DB columns
- Keep `evidenceNotes` as clean rationale text only (prefix stripped at parse time)
- Support filtering and sorting by severity and context in the UI
- Replace Alpine.js parsing with server-side Thymeleaf rendering
- All existing claims keep their data; new fields are nullable (old records = null)

---

## 3. Out of Scope

- Backfilling existing claims (re-running the classifier on old records) — separate future task
- Watchlist SEVERE alert integration — Tier 3
- Company hype scorecard — Tier 3
- Weekly hype report auto-post — Tier 3

---

## 4. New Types (domain module)

### `HypeSeverity.java`

```java
package com.wgblackmon.aihealthcare.domain.model;

public enum HypeSeverity {
    MILD,       // generic positioning language, no clinical framing
    MODERATE,   // specific capability/outcome claim, not patient-facing
    SEVERE      // clinically-framed; a clinician could be misled into a care decision
}
```

### `DeploymentContext.java`

```java
package com.wgblackmon.aihealthcare.domain.model;

public enum DeploymentContext {
    CLINICAL,       // direct patient care, diagnosis, or treatment decisions
    OPERATIONAL,    // hospital administration, billing, scheduling, staffing
    RESEARCH,       // drug discovery, clinical trials, genomics
    GENERAL         // no specific deployment context stated
}
```

---

## 5. FrontierClaim Record Change

Add 3 nullable fields (after `evidenceNotes`):

```java
public record FrontierClaim(
        String claimId,
        String company,
        String claimText,
        LocalDate claimDate,
        String sourceUrl,
        String sourceTitle,
        ClaimType claimType,
        ClaimVerdict verdict,
        String evidenceNotes,       // clean rationale only — no [SEVERITY/CONTEXT/TRIGGER] prefix
        HypeSeverity hypeSeverity,  // null for non-MARKETING_HYPE claims
        String triggeringPhrase,    // null for non-MARKETING_HYPE claims
        DeploymentContext deploymentContext, // null when not specified by LLM
        String articleId,
        Instant detectedAt,
        Instant lastReviewedAt
) { ... }
```

Compact constructor validates existing required fields unchanged. New fields are nullable — no
new validation rules.

---

## 6. FrontierClaimEntity Change

Add 3 new nullable columns (ddl-auto=update adds them automatically on next startup):

```java
@Column(name = "hype_severity", nullable = true)
private String hypeSeverity;      // stored as enum name string

@Column(name = "triggering_phrase", nullable = true, columnDefinition = "TEXT")
private String triggeringPhrase;

@Column(name = "deployment_context", nullable = true)
private String deploymentContext; // stored as enum name string
```

---

## 7. ClaimClassifierAdapter — Evidence Notes Parser

Add a private `parseEvidenceNotes(String raw)` method that extracts the structured prefix
and returns a `ParsedNotes` record:

```java
private record ParsedNotes(
    HypeSeverity severity,
    DeploymentContext context,
    String triggerPhrase,
    String rationale
) {}
```

Regex: `\[SEVERITY:([^\]]+)\]\s*\[CONTEXT:([^\]]+)\]\s*(?:\[TRIGGER:([^\]]+)\]\s*)?([\s\S]*)`

Parsing rules:
- Strip outer quotes from TRIGGER value (`"phrase"` → `phrase`)
- `SEVERITY:N/A` → `severity = null`
- `TRIGGER:N/A` or absent → `triggerPhrase = null`
- Unrecognised SEVERITY or CONTEXT values → `null` (defensive, not an exception)
- If the regex does not match at all → `rationale = raw`, all three fields null

This keeps `evidenceNotes` on `FrontierClaim` as clean rationale text; the structured prefix
is never stored in the DB.

---

## 8. FrontierClaimAdapter Change

`toDomain()`: map `hype_severity` and `deployment_context` via `valueOf()` with null guard.
`toEntity()`: map `HypeSeverity` and `DeploymentContext` via `name()` with null guard.

---

## 9. UI Changes (claim-tracker.html)

### 9a. Remove Alpine.js parser
Delete the `claimNotes()` JS function and replace `x-data="claimNotes()"` on the notes
div with direct Thymeleaf rendering. The `data-notes` attribute is no longer needed.

### 9b. Severity badge in the table row
Show `hypeSeverity` as a small badge **in the Verdict cell**, directly below the MARKETING_HYPE
badge, so severity is visible without expanding Notes:

```
[ MARKETING HYPE ]
[ SEVERE         ]   ← new, red, only shown when non-null
```

### 9c. Notes panel (simplified)
The expandable Notes panel no longer needs to parse anything. Thymeleaf renders:
- Deployment context pill (if non-null)
- Triggering phrase in orange curly quotes (if non-null)
- Rationale text (clean, no prefix)

### 9d. New filter dropdowns
Add two new filter params to the filter bar:
- **Severity**: All / SEVERE / MODERATE / MILD
- **Context**: All / CLINICAL / OPERATIONAL / RESEARCH / GENERAL

Both are optional `@RequestParam` on `ClaimTrackerController.list()`, passed to
`FrontierClaimService.findClaims()`, which adds them to the JPA `Specification`.

---

## 10. Controller / Service / Repository Changes

### ClaimTrackerController
- Accept `@RequestParam(required=false) String severity`
- Accept `@RequestParam(required=false) String context`
- Pass both to service; expose as model attributes for pre-selecting dropdowns

### FrontierClaimService / Specification
- Add `hypeSeverity` and `deploymentContext` predicates to the existing `Specification`
  builder (same pattern as existing `company`, `verdict`, `type` filters)

### ClaimTrackerRestController (REST API)
- Add `severity` and `context` as optional query params (same pattern)

---

## 11. Tests

| Test class | What to test |
|-----------|-------------|
| `HypeSeverityTest` | All 3 values present in `values()` |
| `DeploymentContextTest` | All 4 values present in `values()` |
| `FrontierClaimTest` | Constructor with new fields; null fields accepted; existing validations unchanged |
| `ClaimClassifierAdapterTest` | `parseEvidenceNotes`: SEVERE/CLINICAL/trigger → correct fields; N/A sentinel → null; no prefix → rationale only; unrecognised severity → null |
| `FrontierClaimAdapterTest` | Round-trip toEntity/toDomain with SEVERE+CLINICAL; null severity round-trips cleanly |
| `ClaimTrackerControllerTest` | severity=SEVERE filter returns only SEVERE claims; context=CLINICAL filter works; combined company+severity filter works |

Target: ~20 new/updated tests.

---

## 12. Implementation Order

1. `HypeSeverity.java`, `DeploymentContext.java` — new enums (domain module, no deps)
2. `FrontierClaim.java` — add 3 fields, update compact constructor comment
3. `FrontierClaimTest.java` — update constructor calls, add new field tests
4. `FrontierClaimEntity.java` — add 3 columns
5. `ClaimClassifierAdapter.java` — add `ParsedNotes` record + `parseEvidenceNotes()`, wire into claim builder
6. `ClaimClassifierAdapterTest.java` — parsing tests
7. `FrontierClaimAdapter.java` — map new fields in toEntity/toDomain
8. `FrontierClaimAdapterTest.java` — round-trip tests
9. `FrontierClaimService.java` + `FrontierClaimRepository.java` — severity/context filter predicates
10. `ClaimTrackerController.java` — new filter params
11. `ClaimTrackerControllerTest.java` — filter tests
12. `claim-tracker.html` — replace Alpine parser, add severity badge, add filter dropdowns
13. Compile + selective test run + deploy

---

## 13. What Does NOT Change

- `claim-classifier.txt` prompt — LLM output format is unchanged; Tier 2 is a pure parsing lift
- `ClaimVerdict` enum — unchanged
- `ClaimType` enum — unchanged
- Existing `frontier_claims` rows — new columns are nullable; old rows get null values and display normally
- REST API shape — only additive (new optional query params)
