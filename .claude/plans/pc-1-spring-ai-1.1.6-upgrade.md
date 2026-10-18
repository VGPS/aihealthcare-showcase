# PC-1 Plan: Spring AI 1.0.0 → 1.1.6 Upgrade (Prompt Caching Groundwork)

## Problem

Anthropic org-level monthly spend hit its configured limit on 2026-10-18, blocking every
Claude-backed pipeline (legal trends, deal classification, sentiment, framework analysis,
etc.) until month rollover. Investigating mitigations, prompt caching looked like an easy
win since several adapters reuse the same static prompt template on every scheduled run.

## Investigation — why prompt caching is NOT implemented this slice

Decompiled the actual jars in the local Maven repo rather than guessing:

- **Spring AI 1.0.0** (the version this project was on) has no `cache_control` support at
  all. Confirmed by inspecting `AnthropicApi.ContentBlock`, `ChatCompletionRequest`, and
  `Tool` — none have a cache field, and `system` is a plain `String`. There is no code path
  to set `cache_control` in this version.
- **Spring AI 1.1.6** adds a full caching subsystem: `AnthropicChatOptions.cacheOptions(...)`,
  `AnthropicCacheOptions`, `AnthropicCacheStrategy` (NONE/TOOLS_ONLY/SYSTEM_ONLY/
  SYSTEM_AND_TOOLS/CONVERSATION_HISTORY), `AnthropicCacheType.EPHEMERAL`, per-message-type
  TTL and minimum-content-length config.
- **But caching has a hard floor Anthropic enforces server-side**, and every prompt template
  in this codebase is below it for the model it actually runs on:

  | Template | ~Tokens | Model used | Cache minimum for that model | Clears? |
  |---|---|---|---|---|
  | `framework-analysis.txt` | 1,830 | `claude-haiku-4-5` (via `FrameworkAnalysisLlmAdapter`'s `classification-model` override) | 4,096 | No |
  | `claim-classifier.txt` | 952 | `claude-sonnet-4-5` | 1,024 | No |
  | all other 29 templates | 97–763 | `claude-sonnet-4-6` (default) | 1,024 | No |

  Below the minimum, Anthropic silently skips caching — no error, `cache_creation_input_tokens: 0`.
  A refactor to split static/dynamic prompt content and wire `AnthropicCacheOptions` would
  therefore produce **zero cost reduction today**, for real engineering effort and version-bump
  risk. Confirmed with the user before writing any adapter code — scope was cut to the
  version bump only.

## What This Slice Actually Does

- Bump `spring-ai.version`: `1.0.0` → `1.1.6` in root `pom.xml`. One property change.
- No adapter changes, no new beans, no `AnthropicCacheOptions` wiring — there is nothing to
  cache yet.

## Why Bump Anyway

- Unblocks future caching the moment a template grows past the threshold (e.g. if few-shot
  examples or long reference documents are added to a prompt later).
- 1.0.0 → 1.1.6 is a minor-version jump within the same major; `mvn compile` and the full
  `infrastructure.ai` adapter test suite passed with zero code changes, and a local app
  boot (`mvn spring-boot:run`) confirmed no Spring context wiring breakage.

## What This Is NOT

- Not a prompt-caching implementation — no `cache_control`, no `AnthropicCacheOptions` bean,
  no adapter refactor.
- Not a fix for the Anthropic spend-limit incident — that was resolved directly in the
  Anthropic Console (raised the monthly limit), unrelated to this slice.
- Not a broader Spring AI feature adoption pass (e.g. adaptive thinking, effort levels) —
  out of scope; only the dependency version changed.

## Revisit When

- Any prompt template crosses ~1,024 tokens (Sonnet-tier) or ~4,096 tokens (Haiku 4.5) for
  the model it actually calls — re-run the token-count check in this plan before assuming
  caching will help, since model-to-adapter mapping isn't always the application-wide default
  (`FrameworkAnalysisLlmAdapter` silently overrides to Haiku).
- If a future adapter adds large static context (reference documents, few-shot examples,
  long tool definitions), wire `AnthropicCacheOptions.builder().strategy(SYSTEM_ONLY)...`
  on that adapter's own `ChatClient.Builder` via `.options(...)` — see the existing
  per-call-options precedent in `WikiGapAnalysisAdapter`.

## Verification Performed

- `mvn compile` — BUILD SUCCESS, no breaking API changes surfaced.
- `mvn test -Dtest=<23 infrastructure.ai adapter test classes>` — 200/200 passing.
- `mvn package -DskipTests` — JAR builds successfully.
- `mvn spring-boot:run` locally — app starts, `/login` returns 200, no errors in startup log.
