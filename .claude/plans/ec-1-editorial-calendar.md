# Slice EC-1: Healthcare AI Editorial Calendar

**Status:** In progress  
**Tests target:** 17 new tests  
**Scope:** 13 healthcare-AI-only editorial items (personal finance items dropped)

---

## What it does

Seeds a structured `editorial_calendar_items` table from a JSON dataset,
tracks items through a lifecycle state machine, exposes an admin queue at
`/admin/editorial`, and provides a REST endpoint for the next recommended item.
Public surface: `GET /api/v1/editorial/next` and `GET /api/v1/editorial`.

---

## Architecture decisions

- **No Flyway** — JPA `@Entity` with `ddl-auto: create-drop`, same as every other entity.
- **Flat packages** — records in `domain.model`, ports in their respective `domain.port.*` packages,
  service in `domain.service`, entity/adapter in `infrastructure.persistence`,
  seed runner in `infrastructure.config`, controllers in `web.controller`.
- **Seed runner `@Order(2)`** — `StateLawSeedRunner` owns `@Order(1)`.
- **Audiences** — pipe-delimited string in entity (consistent with other multi-value fields).
- **Primary sources** — JSON TEXT column in entity, Jackson serialized.
- **Domain service** uses `System.Logger` (no Lombok — domain purity rule).
- **`/insights` unchanged** — InsightsController serves static HTML files; editorial calendar
  only tracks the writing workflow. No new public page.

---

## State machine

```
PLANNED → RESEARCHING → DRAFTING → REVIEW → SCHEDULED → PUBLISHED
                                                            ↓
                                                       NEEDS_UPDATE
                                                            ↓
                                                       RESEARCHING
```

---

## Files created

### Domain
| File | Package |
|------|---------|
| `EditorialTheme.java` | `domain.model` |
| `EditorialDemandSignal.java` | `domain.model` |
| `EditorialPriority.java` | `domain.model` |
| `EditorialEffort.java` | `domain.model` |
| `EditorialStatus.java` | `domain.model` |
| `EditorialSource.java` | `domain.model` |
| `EditorialItem.java` | `domain.model` |
| `EditorialCalendarPort.java` | `domain.port.outbound` |
| `ManageEditorialCalendarUseCase.java` | `domain.port.inbound` |
| `EditorialCalendarService.java` | `domain.service` |

### Infrastructure
| File | Package |
|------|---------|
| `EditorialCalendarItemEntity.java` | `infrastructure.persistence` |
| `EditorialCalendarItemRepository.java` | `infrastructure.persistence` |
| `EditorialCalendarItemAdapter.java` | `infrastructure.persistence` |
| `EditorialCalendarSeedRunner.java` | `infrastructure.config` |
| `editorial_calendar_seed.json` | `resources/data/` |

### Web
| File | Package |
|------|---------|
| `EditorialCalendarController.java` | `web.controller` |
| `EditorialCalendarRestController.java` | `web.controller` |
| `editorial-queue.html` | `resources/templates/` |

### Modified
| File | Change |
|------|--------|
| `AppConfig.java` | Add `editorialCalendarService()` bean |
| `openapi.yaml` | Add `GET /api/v1/editorial`, `/api/v1/editorial/next`, `/api/v1/editorial/{id}` |
| `docs/perplexity/*.md/.json` | Remove personal finance items |

### Tests
| File | Count |
|------|-------|
| `EditorialCalendarServiceTest.java` | 7 |
| `EditorialCalendarControllerTest.java` | 5 |
| `EditorialCalendarRestControllerTest.java` | 5 |
