# Slice A2A-1 — Agent-to-Agent Integration (AIHealthcare ↔ ArchitectureExploration)

> **Status: NOT STARTED — zero-API-cost infrastructure work. All code changes are pure
> wiring, persistence, and protocol-level connections. No LLM calls required to build
> or unit-test any item in this slice. API tokens are only spent when the agent loop
> actually runs end-to-end, which is deferred until this infrastructure is in place.**
>
> Planned: 2026-11-08  
> Prerequisite: Both apps running locally on their standard ports (AIHealthcare: 8080,
> ArchitectureExploration: 8081)

---

## What This Slice Is

Five infrastructure changes split across two repositories that together close the
largest gaps in the 8-step agent architecture framework — specifically Steps 4 (Tools
& Integrations / A2A), 5 (Memory Systems / Episodic), and 6 (Orchestration /
Triggers). None of these changes require a live LLM call to build, test, or validate.

The five items are ordered by dependency — each item can unblock the next.

---

## Why Now

A gap analysis against the agent architecture diagram (2026-11-08) confirmed:
- AIHealthcare has the data/knowledge layer AE needs but lacks the agentic runtime
- ArchitectureExploration has the agentic runtime AIHealthcare lacks but loses all
  state on request completion
- The A2A bridge (`connectSse()` → AIHealthcare's `/mcp/sse`) is already 90% built —
  the client transport is written, the server is running, the wire is simply not
  connected

The five items below represent everything that can be built for free before the next
API-spending session.

---

## Item 1 — Wire the A2A bridge

**Repositories:** ArchitectureExploration (primary), AIHealthcare (verify server)  
**Step closed:** Step 4 — Tools & Integrations (A2A)  
**API cost:** Zero (MCP handshake is a protocol-level operation, not an LLM call;
all 6 AIHealthcare MCP tools are pure database queries)

### What to build

**ArchitectureExploration — `HexagonalMain.java`**

Add a second `SpringMcpToolSource` alongside the existing stdio filesystem source:

```java
try (SpringMcpToolSource aihMcp =
        SpringMcpToolSource.connectSse("http://localhost:8080/mcp/sse")) {
    List<Tool> aihTools = aihMcp.tools();
    // merge with existing tools list
    List<Tool> allTools = Stream.concat(tools.stream(), aihTools.stream()).toList();
    Agent agent = new Agent(modelClient, SYSTEM_PROMPT, allTools);
    ...
}
```

**ArchitectureExploration — `PriorAuthService.java`**

The web-layer agent needs the same bridge. Add an optional `List<Tool> extraTools`
constructor parameter (nullable, empty-list default) so the Spring context can inject
AIHealthcare tools without breaking the existing two-constructor test-isolation pattern
already in place.

**AIHealthcare — `SecurityConfig.java`**

Verify (grep, don't assume) that `/mcp/sse` and `/mcp/messages` are on the
`permitAll()` list or at least accessible from localhost without a session. If not,
add them — the MCP server is data-read-only and already guarded by what tools it
exposes.

**New smoke test — `AiHealthcareMcpSmokeTest.java` in ArchitectureExploration**

```java
// No @SpringBootTest, no agent run — just protocol verification
try (SpringMcpToolSource source =
        SpringMcpToolSource.connectSse("http://localhost:8080/mcp/sse")) {
    List<Tool> tools = source.tools();
    assertThat(tools).hasSize(6);
    assertThat(tools.stream().map(Tool::name).toList())
        .containsExactlyInAnyOrder(
            "get_wiki_page", "search_state_laws", "get_regulatory_events",
            "get_market_digest", "browse_companies", "get_trends");
}
```

This test verifies the connection and tool names without calling Anthropic.

### Files changed

| File | Repo | Change |
|------|------|--------|
| `hexagonal/bootstrap/HexagonalMain.java` | AE | Add SSE source + tool merge |
| `web/service/PriorAuthService.java` | AE | Optional extra-tools constructor param |
| `infrastructure/config/SecurityConfig.java` | AIH | Verify /mcp/** permitAll |
| `AiHealthcareMcpSmokeTest.java` (new) | AE | Protocol-level smoke test |

### Verification (zero cost)
Run `AiHealthcareMcpSmokeTest` with AIHealthcare running locally. Assert 6 tools
discovered. No Anthropic key needed.

---

## Item 2 — Episodic memory persistence in ArchitectureExploration

**Repository:** ArchitectureExploration  
**Step closed:** Step 5 — Memory Systems (Episodic)  
**API cost:** Zero (pure JPA persistence; decision is already computed when we save)

### What to build

**New entity: `PriorAuthDecisionEntity.java`**

```
Table: prior_auth_decisions
- id              BIGSERIAL PK
- patient_id      VARCHAR(100) NOT NULL
- determined_at   TIMESTAMPTZ NOT NULL
- determination   VARCHAR(20) NOT NULL  -- APPROVED / DENIED / NEEDS_MORE_INFO
- rationale       TEXT
- criteria_json   TEXT  -- JSON array of CriterionResult records
- gaps_json       TEXT  -- JSON array of gap strings
- appeal_points_json TEXT
- agent_trace_json   TEXT  -- JSON array of ToolCallRecord (for audit)
- cpt_code        VARCHAR(20)
- icd_code        VARCHAR(20)
- npi             VARCHAR(20)
```

**New repository: `PriorAuthDecisionRepository.java`**

```java
List<PriorAuthDecisionEntity> findByPatientIdOrderByDeterminedAtDesc(String patientId);
```

**New tool: `GetPriorAuthHistoryTool.java`**
in `com.wgblackmon.hexagonal.adapter.db`

Queries the last 5 decisions for the patient ID, returns a structured text summary:
```
Prior authorization history for patient P001 (last 5 decisions):
2026-10-15: DENIED — BMI criterion MET (40.2), behavioral program UNMET (4 months, 6 required)
2026-11-01: NEEDS_MORE_INFO — awaiting psychosocial evaluation documentation
```

**Save call in `PriorAuthService.java`**

After `agent.run()` completes and `parseDetermination()` succeeds, save:
```java
decisionRepository.save(toEntity(result, request));
```

**Update system prompt in `PriorAuthService.java`**

Add step 1a: "First call `get_prior_auth_history` for the patient ID. If prior denials
exist, note which criteria were previously unmet and check whether they are now
satisfied."

### Files changed

| File | Repo | Change |
|------|------|--------|
| `PriorAuthDecisionEntity.java` (new) | AE | JPA entity |
| `PriorAuthDecisionRepository.java` (new) | AE | Spring Data repository |
| `PriorAuthDecisionAdapter.java` (new) | AE | Save + query methods, JSON mapping |
| `GetPriorAuthHistoryTool.java` (new) | AE | Tool: last 5 decisions for patient |
| `PriorAuthService.java` | AE | Inject repository, save after completion, add tool, update system prompt step |
| `schema.sql` | AE | Add `prior_auth_decisions` table DDL |

### Tests (zero cost)

| Test | What it verifies |
|------|-----------------|
| `PriorAuthDecisionRepositoryTest` (`@DataJpaTest`, H2) | Save + findByPatientId ordering |
| `GetPriorAuthHistoryToolTest` (Mockito) | No history → returns "no prior decisions"; 2 decisions → correct text format |
| `PriorAuthServiceTest` (existing, extend) | Verify `decisionRepository.save()` called after agent run; verify tool is in agent's tool list |

---

## Item 3 — Conversation session storage in ArchitectureExploration

**Repository:** ArchitectureExploration  
**Step closed:** Step 7 — User Interface (Chat Interface)  
**API cost:** Zero to build; costs tokens only when the follow-up actually runs  
**Prerequisite:** Item 2 (needs `PriorAuthDecisionEntity` for session seeding)

### What to build

**New class: `ConversationStore.java`**
in `com.wgblackmon.web` — mirrors the existing `CursorStore` TTL pattern exactly:

```java
// TTL: 30 minutes, max 100 sessions (same CursorStore constants)
// Key: sessionId (UUID, returned with the initial determination)
// Value: List<Message> — the full agent conversation history
```

**New endpoint: `POST /prior-auth/follow-up`**
in `PriorAuthController.java`

Request: `sessionId` (from prior determination) + `question` (free text)  
Behavior:
1. Load `List<Message>` from `ConversationStore` (404 if expired/not found)
2. Append `Role.USER` message with the question
3. Run agent with the same tools — returns quickly (all context already in history)
4. Append agent response to history, save back to store
5. Return `result-fragment.html` via HTMX (reuses existing fragment, follow-up
   determinations don't get saved to `prior_auth_decisions` — they're clarifications)

**UI change: `result-fragment.html`**

After the determination panel, add a collapsed "Ask a follow-up" section:
```html
<div x-data="{ open: false }">
  <button @click="open = !open">Ask a follow-up question</button>
  <div x-show="open">
    <input type="hidden" name="sessionId" th:value="${sessionId}"/>
    <textarea name="question" placeholder="e.g. Which criterion needs the most documentation?"/>
    <button hx-post="/prior-auth/follow-up" hx-target="#result-panel">Ask</button>
  </div>
</div>
```

**Session ID threading**

`PriorAuthService` generates a `UUID sessionId` on each new determination and stores
the full conversation history in `ConversationStore`. The `PriorAuthResult` record gains
a `sessionId` field so the template can render it into the hidden input.

### Files changed

| File | Repo | Change |
|------|------|--------|
| `ConversationStore.java` (new) | AE | TTL session store for `List<Message>` |
| `PriorAuthController.java` | AE | New `POST /prior-auth/follow-up` endpoint |
| `PriorAuthService.java` | AE | Generate sessionId; store history; new `continueConversation()` method |
| `PriorAuthResult.java` | AE | Add `sessionId` field |
| `result-fragment.html` | AE | Follow-up input panel (collapsed by default) |

### Tests (zero cost)

| Test | What it verifies |
|------|-----------------|
| `ConversationStoreTest` | Store + retrieve; TTL expiry returns empty; max-entries eviction |
| `PriorAuthControllerTest` (extend) | Follow-up 200 when session exists; 404 when session expired/unknown |

---

## Item 4 — Batch submission endpoint in ArchitectureExploration

**Repository:** ArchitectureExploration  
**Step closed:** Step 6 — Orchestration (Triggers / Schedulers)  
**API cost:** Zero — scheduler class built but agent call is **explicitly gated off**
behind `app.priorauth.batch.enabled=false` until ready to spend tokens

### What to build

**New entity: `PendingPriorAuthRequestEntity.java`**

```
Table: pending_prior_auth_requests
- id            BIGSERIAL PK
- patient_id    VARCHAR(100) NOT NULL
- cpt_code      VARCHAR(20)
- icd_code      VARCHAR(20)
- npi           VARCHAR(20)
- clinical_notes TEXT
- status        VARCHAR(20) NOT NULL DEFAULT 'QUEUED'  -- QUEUED / PROCESSING / COMPLETE / FAILED
- submitted_at  TIMESTAMPTZ NOT NULL
- completed_at  TIMESTAMPTZ
- decision_id   BIGINT FK → prior_auth_decisions.id (nullable until complete)
- error_message TEXT (nullable)
```

**New endpoint: `POST /api/prior-auth/batch`**

Accepts a JSON array of requests, inserts each as a `QUEUED` row, returns the IDs.
Also add `GET /api/prior-auth/batch/{id}` for status polling.

**New scheduler: `PriorAuthBatchScheduler.java`**
in `com.wgblackmon.web.service`

```java
@Scheduled(cron = "${app.priorauth.batch-cron:0 */15 * * * *}")
public void processBatch() {
    if (!batchEnabled) {
        log.debug("processBatch() | batch disabled, skipping");
        return;
    }
    // pick up QUEUED rows, call PriorAuthService.evaluate(), save result
}
```

The `batchEnabled` flag is `@Value("${app.priorauth.batch.enabled:false}")` — defaults
off, so the scheduler runs but exits immediately in every environment until explicitly
enabled.

**`application.properties` additions:**
```
app.priorauth.batch.enabled=false
app.priorauth.batch-cron=0 */15 * * * *
```

### Files changed

| File | Repo | Change |
|------|------|--------|
| `PendingPriorAuthRequestEntity.java` (new) | AE | JPA entity |
| `PendingPriorAuthRequestRepository.java` (new) | AE | findByStatus, findById |
| `PriorAuthBatchController.java` (new) | AE | POST /api/prior-auth/batch + GET status |
| `PriorAuthBatchScheduler.java` (new) | AE | @Scheduled, feature-flagged off |
| `schema.sql` | AE | Add `pending_prior_auth_requests` table DDL |
| `application.properties` | AE | batch.enabled + batch-cron keys |

### Tests (zero cost)

| Test | What it verifies |
|------|-----------------|
| `PriorAuthBatchControllerTest` | POST inserts QUEUED rows; GET returns status |
| `PriorAuthBatchSchedulerTest` (Mockito) | When disabled: no service call; when enabled: calls service for each QUEUED row (mocked service, no Anthropic) |

---

## Item 5 — AIHealthcare MCP tool unit tests

**Repository:** AIHealthcare  
**Step closed:** Step 8 — Testing & Evals (coverage gap on MCP layer)  
**API cost:** Zero (all 6 tools are pure database queries)

### What to build

One test class per MCP tool handler in `AiHealthcareMcpToolHandlers`:

| Test class | Tool tested | What to verify |
|-----------|-------------|----------------|
| `GetWikiPageToolTest` | `get_wiki_page` | Known slug → page content; unknown slug → error result |
| `SearchStateKawsToolTest` | `search_state_laws` | State filter returns subset; category filter works; empty result for unknown state |
| `GetRegulatoryEventsToolTest` | `get_regulatory_events` | Type filter (FDA/CMS); date range; empty result handled |
| `GetMarketDigestToolTest` | `get_market_digest` | Latest digest present; date param; no digest → error result |
| `BrowseCompaniesToolTest` | `browse_companies` | Returns company list; sector filter; tier gating not applicable (MCP is internal) |
| `GetTrendsToolTest` | `get_trends` | Latest snapshot; rising/fading/new sections present |

Each test uses `@SpringBootTest` with the test-profile Postgres (same as all other
`@DataJpaTest` classes in the project) and calls the handler methods directly — no MCP
protocol layer involved in the test, no Anthropic key needed.

Also add one integration-style test using the actual MCP protocol:

**`AiHealthcareMcpServerTest.java`**

Uses `SpringMcpToolSource.connectSse("http://localhost:8080/mcp/sse")` to verify
the server starts and all 6 tools are discoverable. Annotated `@Tag("mcp-integration")`
so it only runs when the app is actually started (not in CI `mvn test`).

---

## Effort estimate

| Item | Hours (zero-API work only) |
|------|---------------------------|
| 1 — A2A bridge wire + smoke test | 1–2 hrs |
| 2 — Episodic memory (entity + tool + save) | 2–3 hrs |
| 3 — Conversation session storage + follow-up endpoint | 2–3 hrs |
| 4 — Batch endpoint + gated scheduler | 1.5–2 hrs |
| 5 — MCP tool unit tests (AIHealthcare) | 2–3 hrs |
| **Total** | **~9–13 hrs** |

All items are independent after Item 1. Items 2 and 3 share the `PriorAuthService`
file but can be developed sequentially. Item 5 is fully independent of AE.

---

## What is explicitly NOT in this slice

- Running the full agent end-to-end with AIHealthcare tools (costs tokens)
- Testing the follow-up conversation with a real Sonnet call (costs tokens)
- Enabling the batch scheduler (gated off — costs tokens when enabled)
- Working memory in AIHealthcare's AI Search (separate slice — AIH-WM-1)
- Load testing either app
- Slack/Teams integration (not useful at this stage)

---

## Done criteria

- [ ] `AiHealthcareMcpSmokeTest` passes against AIHealthcare running on port 8080
- [ ] `PriorAuthDecisionRepositoryTest` passes (H2, no Anthropic key)
- [ ] `GetPriorAuthHistoryToolTest` passes (Mockito, no Anthropic key)
- [ ] `ConversationStoreTest` passes
- [ ] `PriorAuthBatchControllerTest` passes
- [ ] `PriorAuthBatchSchedulerTest` passes with batch.enabled=false (no service calls)
- [ ] All 6 `*ToolTest` classes in AIHealthcare pass against test-profile Postgres
- [ ] `mvn test` green in both repos with no Anthropic key in environment
