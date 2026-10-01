# Claude Code Integration Brief: 90-Day Healthcare AI Editorial Calendar

**Workspace context:** AI in Healthcare application  
**Prepared for:** Claude Code review and integration  
**Scope note:** Refactored from the original "AI Healthcare + Finance" Perplexity draft.
Personal-finance items (themes: finance-rules, evergreen-finance, finance-protection, and
consumer AI-literacy) have been removed. 13 healthcare-AI-scoped items are retained.
**Companion datasets:**
- Markdown planning sheet: `docs/perplexity/AI Healthcare Editorial Calendar.md`
- Import-ready JSON: `docs/perplexity/AI Healthcare Editorial Calendar.json`
- Target window: October 1–December 29, 2026 (13 items)

## Objectives

1. Ingest the 13-item healthcare AI calendar into the AI in Healthcare application as structured, queryable data.
2. Establish a clear schema and state model so the application can schedule, draft, verify, and measure content.
3. Apply a transparent search-validation policy that distinguishes time-sensitive regulatory priorities from evergreen topics, without relying on fabricated keyword volume.
4. Provide Claude Code with concrete database, API, and validation requirements for implementation.

## Priority and search-validation policy

Search-volume validation was investigated directly during original Perplexity research:

- A live Google Trends session returned HTTP 429 during automated collection, and no third-party SEO tool API was connected.
- In the import-ready JSON, `search_validation.estimated_monthly_searches` is set to `null` with explicit `query_candidates`, `source_type`, `required_before_publish`, and `next_action` fields.

### Three priority tiers (healthcare-only examples)

| Priority tier | Definition | Search-volume gate | Examples |
|---|---|---|---|
| **P0 (Immediate / Event-driven)** | Hard legal effective dates, active regulatory changes, major enforcement events, or annual deadlines. | **Bypasses the search-volume gate.** Publication priority is driven by the event date and primary-source verification. | Texas TRAIGA disclosure ([Texas AG](https://www.texasattorneygeneral.gov/consumer-protection/file-consumer-complaint/consumer-ai-rights)), EU AI Act timeline ([EC](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai)), Colorado HB26-1139 ([Colorado GA](https://leg.colorado.gov/bills/HB26-1139)), FDA PCCP ([FDA](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/marketing-submission-recommendations-predetermined-change-control-plan-artificial-intelligence)), prior-authorization 72-hour clocks ([CMS](https://www.cms.gov/newsroom/blog/moving-prior-authorization-21st-century)). |
| **P1 (Strategic / Repeatable)** | High-value operational, architectural, or economics topics with steady demand and strong application alignment. | **Requires qualitative query-proxy validation or internal search analytics** before expansion into cornerstone content, but can proceed through drafting. | Ambient scribe adoption and equity ([Emory](https://sph.emory.edu/news/new-study-finds-nearly-two-thirds-us-hospitals-using-epic-have-adopted-ambient-ai-disparities)), HIPAA generative AI controls ([AWS](https://aws.amazon.com/blogs/industries/building-a-hipaa-ready-generative-ai-architecture-for-healthcare-on-aws/)), total cost of AI pilots, clinical AI validation beyond accuracy. |
| **P2 (Evergreen / Foundation)** | Fundamental concepts requiring demand validation. | **Gated by search demand.** No P2 items remain in this calendar after removing the personal-finance scope. P2 items may be added in future cycles if healthcare AI evergreen topics are identified with supporting first-party data. | — |

## Structured data model

The application treats each editorial item as a structured record. Claude Code maps this schema to JPA entities (no Flyway — `ddl-auto: create-drop`, same pattern as all other entities):

### JSON schema structure (as implemented)

```typescript
interface EditorialItem {
  id: string;          // "editorial-2026-01"
  slug: string;        // "texas-traiga-healthcare-ai-disclosure" (PK)
  title: string;
  hook: string;
  theme: 'healthcare-governance' | 'healthcare-operations' | 'healthcare-economics' | 'ai-health-finances';
  audiences: string[];
  demand_signal: 'hot' | 'steady' | 'evergreen';
  priority_tier: 'P0' | 'P1' | 'P2';
  effort: 'S' | 'M' | 'L';
  format: string;
  publish_window: {
    start: string;       // YYYY-MM-DD
    end: string;         // YYYY-MM-DD
    preferred_date: string;
  };
  primary_sources: Array<{
    url: string;
    label: string;
    source_type: string;
  }>;
  cta: string;
  status: 'planned' | 'researching' | 'drafting' | 'review' | 'scheduled' | 'published' | 'needs-update';
  canonical: boolean;
  last_verified: string;
}
```

### JPA entity (implemented in `infrastructure.persistence.EditorialCalendarItemEntity`)

```java
@Entity
@Table(name = "editorial_calendar_items")
public class EditorialCalendarItemEntity {
    @Id String id;           // slug used as PK
    String title;
    String hook;
    String theme;
    String demandSignal;
    String priorityTier;
    String effort;
    String format;
    LocalDate publishWindowStart;
    LocalDate publishWindowEnd;
    LocalDate preferredDate;
    @Column(columnDefinition = "TEXT") String audiences;        // pipe-delimited
    @Column(columnDefinition = "TEXT") String primarySourcesJson; // JSON array
    String cta;
    String status;
    Boolean canonical;
    LocalDate lastVerified;
    LocalDate createdAt;
    LocalDate updatedAt;
}
```

## Content lifecycle and state transitions

```
planned → researching → drafting → review → scheduled → published → needs-update → researching
```

All transitions go through `EditorialStatus.next()` — no arbitrary status jumps.

1. **`planned`:** Item seeded from JSON, awaiting assignment.
2. **`researching`:** Primary sources are being verified.
3. **`drafting`:** Article is under active production.
4. **`review`:** Fact-checking and compliance verification.
5. **`scheduled`:** Ready for publication on `preferred_date`.
6. **`published`:** Live on the application.
7. **`needs-update`:** An underlying regulation or threshold changed — re-enters `researching`.

## Implementation — already built (Slice EC-1)

The following are implemented and seeded:

- **Domain:** `EditorialTheme`, `EditorialDemandSignal`, `EditorialPriority`, `EditorialEffort`, `EditorialStatus` enums + `EditorialItem` record + `EditorialSource` record
- **Ports:** `ManageEditorialCalendarUseCase` (inbound), `EditorialCalendarPort` (outbound)
- **Service:** `EditorialCalendarService` — `getQueue()`, `getNext()`, `advanceStatus()`
- **Persistence:** `EditorialCalendarItemEntity`, `EditorialCalendarItemRepository`, `EditorialCalendarItemAdapter`
- **Seed:** `EditorialCalendarSeedRunner` (`@Order(2)`) — idempotent upsert from `editorial_calendar_seed.json`
- **Web:** `EditorialCalendarController` at `GET /admin/editorial` (admin queue + filter), `EditorialCalendarRestController` at `GET /api/v1/editorial`, `GET /api/v1/editorial/next`, `GET /api/v1/editorial/{id}`, `POST /api/v1/editorial/{id}/status`
- **Template:** `editorial-queue.html` — filterable queue with priority/status badges and advance form

## Notes for content authors

- `GET /api/v1/editorial/next` returns the next recommended article: filtered by `status=planned`, ordered by `priorityTier ASC, preferredDate ASC`.
- Admin queue at `GET /admin/editorial` shows the full queue with optional filter by priority, demand signal, and effort.
- P0 regulatory items (Texas TRAIGA, EU AI Act, FDA PCCP, Colorado HB26-1139) should have `last_verified` refreshed before moving to `scheduled`. A stale `last_verified` (>30 days from `preferredDate`) will display a warning badge.
- All 13 items start in `planned` status after seed. Advance each item through the lifecycle using the admin queue.
