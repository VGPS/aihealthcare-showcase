# LT-1 — AI Liability Case Tracker

**Status:** In progress  
**Zero-cost constraint:** No LLM API calls in Phases 1–3. Phase 4 is deferred until funded.  
**Motivation:** AMA liability shield vote (2026-11-13), OpenAI wrongful death lawsuit, FDA autonomous
robot warning — the collision between patients, physicians, AI vendors, and courts is becoming a
primary story in AI healthcare. This feature creates a structured, longitudinal record of incidents
(the app's "historian" positioning) and surfaces related harvested articles at no additional cost.

---

## Data Model

### Domain enums (`domain.model`)

| Enum | Values |
|------|--------|
| `IncidentType` | LAWSUIT, FDA_FLAG, WRONGFUL_DEATH, REGULATORY_SANCTION, SETTLEMENT |
| `HarmType` | MISDIAGNOSIS, MEDICATION_ERROR, AUTONOMOUS_DEVICE, DOCUMENTATION_TRAP, DELAYED_CARE, OTHER |
| `IncidentStatus` | FILED, ACTIVE, SETTLED, DISMISSED, DECIDED |

### Domain record `LiabilityIncident`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| incidentId | String | no | UUID, caller-assigned |
| incidentType | IncidentType | no | |
| harmType | HarmType | no | |
| status | IncidentStatus | no | |
| title | String | no | ≤500 chars, display label |
| plaintiff | String | yes | patient / family / regulator |
| defendant | String | no | company/vendor — used for article linking |
| legalTheory | String | yes | free text (e.g. "Product liability") |
| court | String | yes | |
| caseNumber | String | yes | |
| harmDate | LocalDate | yes | when harm occurred |
| filingDate | LocalDate | yes | when lawsuit filed / FDA flagged |
| outcome | String | yes | ≤1000 chars |
| sourceUrl | String | yes | ≤2000 chars |
| notes | String | yes | TEXT — free-form context |
| createdAt | Instant | no | |
| updatedAt | Instant | no | |

### Table: `liability_incidents`

Hibernate `ddl-auto: update` creates the table automatically. All nullable columns have no DB DEFAULT
(safe — no NOT NULL migration risk on existing rows). `incident_id` is a VARCHAR(36) UUID PK,
caller-assigned (same pattern as `deal_signals`).

---

## Phases

### Phase 1 — Schema + Admin Seed Form ($0)

**New files:**
- `domain/model/IncidentType.java` — enum, no Spring/Lombok
- `domain/model/HarmType.java` — enum
- `domain/model/IncidentStatus.java` — enum
- `domain/model/LiabilityIncident.java` — record, compact constructor validates non-null required fields
- `domain/port/outbound/LiabilityIncidentPort.java` — save / findAll / findById / deleteById / findRelatedArticles
- `infrastructure/persistence/LiabilityIncidentEntity.java` — JPA entity, enums as String (no @Enumerated)
- `infrastructure/persistence/LiabilityIncidentRepository.java` — extends JpaRepository<LiabilityIncidentEntity, String>
- `infrastructure/persistence/LiabilityIncidentAdapter.java` — @Component, implements LiabilityIncidentPort
- `web/controller/LiabilityTrackerController.java` — @Controller, @RequestMapping("/admin/liability")
- `templates/liability-admin.html` — Tailwind, fragments/head + fragments/nav, CSRF

**Admin endpoints:**
- `GET /admin/liability` — list all incidents, inline add form
- `POST /admin/liability/add` — create, redirect /admin/liability
- `GET /admin/liability/{id}/edit` — edit form
- `POST /admin/liability/{id}/update` — save edit, redirect /admin/liability
- `POST /admin/liability/{id}/delete` — delete, redirect /admin/liability

**Security:** automatically protected by existing `.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")`
rule in SecurityConfig — no config change needed.

**Seed data (data.sql):** 3 cases from 2026-11-13 research week:
1. OpenAI wrongful death (epilepsy medication stop) — WRONGFUL_DEATH / MEDICATION_ERROR / ACTIVE
2. OpenAI ChatGPT blood pressure product liability (Winters v OpenAI) — LAWSUIT / DELAYED_CARE / ACTIVE
3. Florida hospital AI cancer misdiagnosis malpractice — LAWSUIT / MISDIAGNOSIS / ACTIVE

### Phase 2 — Auto-link Existing Harvested Articles ($0)

`findRelatedArticles(String defendant, int limit)` on `LiabilityIncidentPort`.
Implemented in `LiabilityIncidentAdapter` using `NewsArticleRepository.findByTitleContainingIgnoreCase(defendant)`.
Displayed on the admin list page — "Related Articles" column links to `/dashboard/news` articles per incident.

### Phase 3 — CourtListener + Google News Legal RSS Feeds ($0)

Add 2 feeds to `application.yml` under `aihealthcare.feeds.sources`, tier `LEGAL`:
1. CourtListener opinions RSS filtered for AI healthcare liability
2. Google News RSS for "AI healthcare lawsuit liability" — same pattern as existing Google News LEGAL feeds

No code changes. `RomeFeedHarvester` picks up new feeds on next restart.

### Phase 4 — LLM Auto-classification (DEFERRED — requires tokens)

When funded:
- New `@Scheduled` lightweight classifier reads recent LEGAL/REGULATORY articles
- Calls Anthropic to extract: defendant, harm type, incident type, case status, source URL
- Proposes new `LiabilityIncident` records for admin review (status = PENDING_REVIEW)
- Admin approves/rejects via existing admin UI

Stub: `IncidentStatus` already has room to add `PENDING_REVIEW` when needed.

---

## Tests

| Test class | Scope |
|-----------|-------|
| `LiabilityIncidentAdapterTest` | @DataJpaTest — save/load round-trips all enum values |
| `LiabilityTrackerControllerTest` | @WebMvcTest — GET list 200, POST add redirects, POST delete redirects |

---

## Architecture Checklist

- [ ] `domain/model/` — zero Spring/Lombok/JPA imports
- [ ] `domain/port/outbound/` — interface, domain types only
- [ ] `infrastructure/persistence/` — @Component adapter, constructor injection, entry/exit logs
- [ ] `web/controller/` — constructor injection, @Slf4j, redirects for all POSTs
- [ ] Security — no new SecurityConfig entries needed
- [ ] OpenAPI — no new REST endpoints; admin-only form posts don't need openapi.yaml changes
