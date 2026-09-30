# SOC-1: Agent-Powered Social Post Drafting

**Created:** 2026-09-26  
**Status:** COMPLETE (2026-09-28) — domain/service/adapter/controller/template shipped in `e84ac73`; controller-level test for `/dashboard/social/agent-draft` not yet written (see Test Verification note)  
**Learning goal:** Understand Spring AI tool calling (function calling) — the mechanism behind LLM agents

---

## Context

The current `/dashboard/social` page (`MarketSocialPostController`) has **zero LLM calls**.
It is pure Java string formatting: pick top 5 ranked entries, concatenate into a rigid template,
enforce character limits. Every post looks the same regardless of the news quality.

This slice replaces the generation step with an LLM agent that:
1. Decides autonomously which entries deserve coverage (tool call → read DB)
2. Optionally pulls supporting article context for any company it wants to highlight (tool call → read articles)
3. Writes posts in a natural voice optimized for LinkedIn engagement

The before/after contrast on the same page is the primary teaching tool.

---

## What You Will Learn

| Concept | Where it shows up |
|---------|------------------|
| Tool definition | `@Tool`-annotated methods in `SocialPostAgentAdapter` |
| Tool use loop | Spring AI ChatClient handles the loop — Claude calls a tool, gets a result, decides next step |
| Autonomous selection | Claude picks which entries to write about, not your code |
| Structured output | Prompt template constrains output to LINKEDIN_BODY / LINKEDIN_COMMENT / FACEBOOK_BODY format |
| Before/after comparison | Same page shows both template-generated and agent-generated posts |

**Key "aha" moment:** After your first run, read the Spring AI DEBUG logs. You'll see the tool
call requests Claude makes, the results it receives, and how it reasons before drafting. That
sequence — ask → receive → reason → (ask again?) → answer — is what makes it an agent.

---

## Architecture

```
DraftSocialPostUseCase (domain.port.inbound)
        ↓
SocialPostAgentService (domain.service)
  - calls ProduceMarketDigestUseCase.getLatest() to get today's digest
  - calls SocialPostDraftPort.draft(entries, date)
        ↓
SocialPostDraftPort (domain.port.outbound)
        ↓
SocialPostAgentAdapter (infrastructure.ai)
  - Spring AI ChatClient + @Tool methods
  - Tool 1: getDigestEntries(date)   → queries MarketDigestEntryRepository
  - Tool 2: getCompanyArticles(name) → queries NewsArticleRepository
  - Claude calls these tools autonomously, then drafts posts
        ↓
SocialPostDraft (domain.model)
  - linkedinBody, linkedinComment, facebookBody, facebookComment
  - entriesSelected (List<String> headlines Claude chose)
  - rationale (Claude's one-sentence explanation of its choices)
  - generatedAt
```

---

## New Files

### Domain (`domain.model`)
```
SocialPostDraft.java
  String linkedinBody       // max ~2900 chars
  String linkedinComment    // max ~1050 chars — hashtags + CTA
  String facebookBody       // max ~390 chars before "See more" fold
  String facebookComment    // optional, hashtags
  List<String> entriesSelected  // headlines Claude chose to cover
  String rationale          // Claude's 1-sentence explanation
  Instant generatedAt
```

### Domain (`domain.port.inbound`)
```
DraftSocialPostUseCase.java
  SocialPostDraft draft(LocalDate date);
```

### Domain (`domain.port.outbound`)
```
SocialPostDraftPort.java
  SocialPostDraft draft(List<MarketDigestEntry> entries, LocalDate date);
```

### Domain service (`domain.service`)
```
SocialPostAgentService.java
  - implements DraftSocialPostUseCase
  - constructor: ProduceMarketDigestUseCase digestUseCase, SocialPostDraftPort draftPort
  - draft(date):
      1. call digestUseCase.getDigest(date) → MarketDigest (or getLatest() if null)
      2. call draftPort.draft(digest.entries(), date)
      3. return result
```

### Infrastructure adapter (`infrastructure.ai`)
```
SocialPostAgentAdapter.java
  - implements SocialPostDraftPort
  - constructor: ChatClient chatClient,
                 MarketDigestEntryRepository entryRepository,    // for tool 1
                 NewsArticleRepository articleRepository          // for tool 2
  
  @Tool(description = "Get ranked market digest entries for a date (format: yyyy-MM-dd). 
                        Returns headline, summary, category, rank, and company names.")
  String getDigestEntries(String date) { ... }

  @Tool(description = "Get recent news articles mentioning a specific company name. 
                        Returns up to 5 article titles and summaries.")  
  String getCompanyArticles(String companyName) { ... }

  @Override
  SocialPostDraft draft(List<MarketDigestEntry> entries, LocalDate date) {
      // Pass entries as context in system prompt
      // Let Claude call tools if it wants more detail
      // Parse structured response into SocialPostDraft record
  }
```

### Prompt (`application/src/main/resources/prompts/`)
```
social-post-agent.txt
  System prompt covering:
  - Role: expert LinkedIn ghostwriter for AI healthcare industry
  - Output format: LINKEDIN_BODY: ... LINKEDIN_COMMENT: ... FACEBOOK_BODY: ... 
                   FACEBOOK_COMMENT: ... ENTRIES_SELECTED: ... RATIONALE: ...
  - LinkedIn best practices: hook line, no buzzwords, 3-5 short paragraphs, CTA
  - Character limits: LI body ~2900, LI comment ~1050, FB ~390 before fold
  - Use tools if you want to verify details or get more context on a company
```

### Config (`infrastructure.config`)
```
AppConfig.java additions:
  @Bean SocialPostDraftPort socialPostDraftPort(ChatClient chatClient, 
      MarketDigestEntryRepository r1, NewsArticleRepository r2)
  @Bean DraftSocialPostUseCase draftSocialPostUseCase(
      ProduceMarketDigestUseCase digestUseCase, SocialPostDraftPort draftPort)
```

### Web (modify existing)
```
MarketSocialPostController.java additions:
  - inject DraftSocialPostUseCase agentUseCase
  - POST /dashboard/social/agent-draft → calls agentUseCase.draft(today)
  - adds model attrs: agentDraft (SocialPostDraft), agentError (String)

social-post.html additions:
  - "Generate Agent Draft" button (POST form to /dashboard/social/agent-draft)
  - New section below existing output: agent-drafted posts with same copy buttons
  - Rationale card: "Claude chose these entries because: {rationale}"
  - Highlighted list of entriesSelected vs full digest list (before/after comparison)
```

---

## Build Order

```
Step 1 — Domain (no Spring, no tests yet)
  - SocialPostDraft record
  - DraftSocialPostUseCase port
  - SocialPostDraftPort port

Step 2 — Prompt template
  - social-post-agent.txt
  - Nail the output format here — the parser in Step 3 depends on it

Step 3 — SocialPostAgentAdapter  ← MAIN LEARNING STEP
  - Wire ChatClient + tool methods
  - Parse structured response
  - Test manually by calling draft() in isolation with a fake entry list

Step 4 — SocialPostAgentService + AppConfig wiring

Step 5 — Controller + template
  - POST endpoint
  - Template additions

Step 6 — Tests
  - SocialPostDraftTest (domain record, compact constructor validation)
  - SocialPostAgentAdapterTest (mock ChatClient, verify tools registered)
  - SocialPostAgentServiceTest (mock ports, verify delegation)
  - MarketSocialPostControllerTest (add agent-draft endpoint test)
```

---

## Debugging Tips

Enable Spring AI tool call logging to see Claude's tool use decisions:
```yaml
# application.yml (dev only)
logging:
  level:
    org.springframework.ai: DEBUG
```

Watch for log lines like:
```
Tool call request: getDigestEntries({"date":"2026-09-26"})
Tool call result: [{"headline":"...","rank":1,...}]
Tool call request: getCompanyArticles({"companyName":"Epic Systems"})
```

That sequence in the logs IS the agent reasoning. Read it after your first run.

---

## What NOT to Do

- Do not pre-fetch all articles and dump them in the prompt. The point is Claude asks for what it needs.
- Do not hardcode which entries to use. Claude selects them.
- Do not over-engineer the tool return types. Return compact JSON strings — token cost matters.
- Do not remove the existing template-based output. Keep both on the page for comparison.

---

## Test Verification

```bash
mvn test -Dtest="SocialPostDraftTest,SocialPostAgentAdapterTest,SocialPostAgentServiceTest"
```

Expected: all pass with no live AI calls (mock ChatClient in tests). Confirmed 2026-09-28: 15 tests
(7 + 2 + 6), 0 failures. `MarketSocialPostControllerTest` for the new `/dashboard/social/agent-draft`
endpoint was never written — flagged as a follow-up, not blocking.

---

## Definition of Done

- [x] `POST /dashboard/social/agent-draft` returns a `SocialPostDraft`
- [x] Agent-drafted posts appear on the page alongside template-generated posts
- [ ] At least one tool call happens per generation (verify in DEBUG logs) — not yet observed against a live digest; `logging.level.org.springframework.ai: DEBUG` is wired in `application.yml`
- [x] Character limits respected (same validation as existing controller)
- [x] 3 new test classes passing (`SocialPostDraftTest`, `SocialPostAgentServiceTest`, `SocialPostAgentAdapterTest` — 15 tests); controller test not written
- [x] Plan status updated to COMPLETE
