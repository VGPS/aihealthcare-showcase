# Slice PPC-1 — Payer Policy Change Tracker

> **Status: NOT STARTED.** Revised 2026-10-29: no auto-publish, provenance-linked
> stats, AMBIGUOUS match bucket, internal-only review console, and a portable
> package design for reuse in another application.

## Non-negotiable constraints

1. **No auto-publish path exists anywhere.** Every detected change event lands in
   a review queue (`reviewStatus = PENDING_REVIEW`). A human explicitly promotes
   or dismisses it. The stats query is structurally incapable of including
   anything but `PROMOTED` rows.
2. **Every published number is provenance-linked** — traces back to the literal
   before/after criterion text and source document.
3. **A third bucket exists: AMBIGUOUS**, not just same/changed. Low-confidence
   matches are excluded from headline counts until a human resolves them.
4. **This phase builds an internal admin review console only** — no public
   dashboard. That's a separate, future, explicitly-approved slice.
5. **Portability constraints:** no imports from `com.wgblackmon.aihealthcare.*`;
   no JPA relationships into AIHealthcare tables; config under a vendor-neutral
   `payer-policy:` prefix; tables prefixed `payer_policy_*`; the Thymeleaf admin
   console is the one piece not claimed portable.

## Phase 0 — Pilot

2 hand-verifiable documents (e.g. Cigna oncology, UHC cardiology). Build the full
pipeline including the review-gate mechanism — fetch → hash → extract →
embedding-match → diff → `PENDING_REVIEW` event → manual promote/dismiss →
confirm stats reflect only the promoted result.

**Exit criteria:** extraction/match verified criterion-by-criterion; AMBIGUOUS
bucket isn't suspiciously empty; review-gate proven by test, not just visual
check; provenance links confirmed.

## Portable package layout

```
com.wgblackmon.payerpolicy        (vendor-neutral root — deliberate, scoped
                                    exception to this project's usual flat-package
                                    convention, made because portability was asked for)
├── model/          Payer, MedicalSpecialty, PayerPolicyDocument, PolicyCriterion,
│                   CriterionMatchResult (UNCHANGED/MODIFIED/ADDED/REMOVED/AMBIGUOUS),
│                   ReviewStatus (PENDING_REVIEW/PROMOTED/DISMISSED),
│                   PolicyChangeEvent, PolicyChangeStats (PROMOTED-only by construction)
├── port/
│   ├── inbound/    MonitorPayerPoliciesUseCase
│   └── outbound/   PayerPolicySourcePort, PolicyDocumentFetchPort,
│                   PolicyCriteriaExtractionPort, PolicyChangeDiffPort,
│                   PayerPolicyDocumentPort, PolicyChangeEventPort
├── service/        PayerPolicyChangeTrackingService (pure orchestration, zero Spring deps)
└── infra/          (the Spring/DB/AI glue a host app wires up)
    ├── config/      PayerPolicyProperties (@ConfigurationProperties(prefix="payer-policy"))
    ├── fetch/       PayerPolicyFetchAdapter (self-contained PDF/HTML parse — deliberately
    │                duplicated, not reused from AIHealthcare, to avoid cross-package deps)
    ├── ai/          PolicyCriteriaExtractionAdapter, PolicyChangeDiffAdapter
    ├── persistence/ PayerPolicy*Entity/Repository/Adapter (payer_policy_* tables, no FKs out)
    ├── scheduler/   PayerPolicyMonitorScheduler
    └── web/         PayerPolicyAdminReviewController (admin-only), PayerPolicyRestController
```

**Porting checklist:** copy the package → add a PDF-parsing lib + Spring AI
starter if missing → add the `payer-policy:` YAML block → confirm host app has
`ChatClient`/`VectorStore`/JPA beans configured → wrap the admin controller in
that app's own auth → build that app's own template for the review console (only
non-portable piece).

## Domain model (key additions from the risk discussion)

| Type | Notes |
|---|---|
| `PolicyChangeEvent` | documentId, detectedAt, criteriaAddedCount, criteriaRemovedCount, criteriaModifiedCount, criteriaAmbiguousCount, changeSummary, reviewStatus, reviewedAt, promotedAt, criterionDiffs (provenance drill-down) |
| `PolicyChangeStats` | computed from PROMOTED events only — never reads PENDING_REVIEW/DISMISSED rows |

## Persistence (standalone, no FKs into AIHealthcare tables)

`payer_policy_documents`, `payer_policy_criterion_snapshots`,
`payer_policy_change_events` (incl. reviewStatus),
`payer_policy_criterion_diffs` (before/after text + matchResult + similarityScore
— the provenance behind every count).

## Web (internal-only this phase)

`GET /admin/payer-policy/review` (admin-gated, promote/dismiss UI) +
`GET /api/payer-policy/stats` and `/documents/{id}/history` (PROMOTED-only JSON).
**No public dashboard page in this slice.**

## Testing additions

- Explicit test: stats query excludes non-PROMOTED rows.
- Explicit test: AMBIGUOUS matches never contribute to published counts.
- Manual criterion-by-criterion verification against the 2 pilot documents,
  repeated after any future prompt change.

## Estimated effort

**Phase 0 pilot: ~1.5 days.** **Phases 1-4: ~5-7 additional days**, still
dominated by extraction/diff hardening (1-3 days, the real unknown). Source
curation runs in parallel, not counted in engineering days.
