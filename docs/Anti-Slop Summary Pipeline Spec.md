# Handoff Spec: Anti-Slop Summary Pipeline for AI Healthcare

**Audience:** Claude Code
**Owner:** Bill Blackmon
**Stack assumed:** Java 21, Spring Boot 3.x, Spring AI (1.x or 2.x), PostgreSQL (+ pgvector), existing multi-provider LLM access (Anthropic, Google Gemini, OpenAI, Perplexity)
**Status:** Proposal. Evaluate against the current codebase first, then implement in phases.

---

## 0. Instructions to Claude Code

1. **Evaluate before building.** Read the current summarization code path (where Google search results, Perplexity Deep Research output, wiki content, and Postgres rows get synthesized into display summaries). Write a short findings note (`docs/anti-slop/00-findings.md`) that covers:
   - The files, classes, and prompts that produce summaries today.
   - The Spring AI version in use and which provider starters are configured.
   - How source material is passed to the model (raw text, chunks, rows) and whether source IDs or URLs survive to the output.
   - Where summaries are stored (table and columns) and how they are rendered.
   - Any conflicts with this spec, and recommended changes to the spec.
2. **Do not rewrite working code unnecessarily.** Where possible, add the new components as a layer around the existing pipeline.
3. **Verify every Spring AI API you use against the version actually in `pom.xml`/`build.gradle`.** The code in this spec is illustrative. Spring AI class names and packages changed between versions (for example, evaluator packages and structured-output options). Adapt it rather than copying it blindly.
4. Put everything behind feature flags (Section 8) so old and new output can be compared side by side.
5. Stop and report back after Phase 1 (Section 9) with before/after samples before continuing.

---

## 1. Problem Statement

Today's summaries read as generic "AI slop": hedged openings, filler phrases ("it's important to note", "in today's rapidly evolving landscape"), reflexive lists of three, too much bold, too many bullets, vague claims with no source attached, and closing paragraphs that repeat the summary.

Switching models will not fix this by itself. The main causes are:

- A single open-ended "summarize this" prompt, which lets the model fill space with its default style.
- No explicit style guide or example of the target voice.
- No automated check on the output.

For healthcare content, the most serious gap is claims that cannot be traced to a source.

## 2. Target Architecture

```
Sources (Google results, Perplexity Deep Research, wiki, Postgres rows)
        │
        ▼
[Stage 1] EXTRACT  — model: Gemini (strongest on grounded faithfulness benchmarks)
        │   Output: SummaryExtraction (typed record, JSON validated against schema)
        │   Every finding carries sourceIds pointing at the input sources.
        ▼
[Stage 2] WRITE    — model: Claude (strongest prose); or deterministic template
        │   Input: the extraction ONLY (not the raw sources) + house style guide + voice samples
        │   Output: markdown summary with inline [S#] citations
        ▼
[Stage 3] CHECK    — deterministic Java linter (SlopLinter) + LLM faithfulness check
        │   Pass → store & display
        │   Fail → [Stage 3b] EDIT pass (one attempt, "edit, don't rewrite")
        │          → re-check → pass: store; fail: store with flag for human review
        ▼
summary_version table (keeps original, new, scores, lint findings)
```

**Why split extraction from writing:** the model that writes the prose never sees raw sources. It can only express facts that were extracted with source IDs. That removes most of the space for padding and unsupported claims. Stage 2 can be a pure Java template for highly structured summary types (for example vendor comparisons), with no LLM at all.

**Model routing is configuration, not code.** Each stage names a provider/model in `application.yml` so it can be swapped after evaluation (Section 7).

---

## 3. Item 1 — Extraction Record (Spring AI Structured Output)

### 3.1 Source normalization

Before Stage 1, normalize all inputs into a common shape with a stable short ID the model can cite.

```java
package ai.healthcare.summary.source;

import java.time.LocalDate;

public enum SourceType { GOOGLE_RESULT, PERPLEXITY_RESEARCH, WIKI, DB_ROW, VECTOR_CHUNK }

/** One piece of source material, labeled S1, S2, ... for citation. */
public record SourceDoc(
        String citeId,          // "S1", "S2" ... assigned per summary run
        SourceType type,
        String title,
        String url,             // nullable for DB rows
        String dbRef,           // e.g. "vendor:1234" or "trial:NCT01234567"; nullable
        LocalDate publishedOn,  // nullable
        String content          // trimmed text passed to the model
) {}
```

Render sources into the prompt as:

```
[S1] (PERPLEXITY_RESEARCH, 2026-08-14) Title — url
<content>

[S2] (DB_ROW vendor:1234) Title
<content>
```

### 3.2 Extraction schema

```java
package ai.healthcare.summary.extract;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record SummaryExtraction(
        @JsonPropertyDescription("One sentence, max 30 words, stating the single most important finding with a number if one exists. No hedging.")
        String headline,

        @JsonPropertyDescription("3 to 8 concrete findings, most important first.")
        List<Finding> findings,

        @JsonPropertyDescription("Specific numbers from the sources: dollar amounts, percentages, counts, dates.")
        List<KeyNumber> keyNumbers,

        @JsonPropertyDescription("Named organizations, products, regulators, or people central to the findings.")
        List<Entity> entities,

        @JsonPropertyDescription("Places where sources disagree. Empty list if none.")
        List<Conflict> conflicts,

        @JsonPropertyDescription("Important questions the sources do NOT answer. Empty list if none.")
        List<String> unknowns,

        @JsonPropertyDescription("One or two sentences on the practical implication for a healthcare buyer, operator, or investor. Must be grounded in findings.")
        String soWhat
) {
    public enum Confidence { HIGH, MEDIUM, LOW }

    public record Finding(
            @JsonPropertyDescription("Plain factual statement, max 35 words. No adjectives like 'significant' or 'crucial' unless quoting a source.")
            String statement,
            @JsonPropertyDescription("Cite IDs supporting this statement, e.g. [\"S1\",\"S4\"]. Must be non-empty.")
            List<String> sourceIds,
            @JsonPropertyDescription("HIGH = stated directly by a primary or authoritative source; MEDIUM = stated by a secondary source or inferred from one; LOW = single weak source.")
            Confidence confidence,
            @JsonPropertyDescription("ISO date the fact applies to or was published, if known; else null.")
            String asOf
    ) {}

    public record KeyNumber(String label, String value, String unit, List<String> sourceIds) {}

    public record Entity(String name, String kind, String role) {}  // kind: VENDOR, PROVIDER, PAYER, REGULATOR, PRODUCT, PERSON, OTHER

    public record Conflict(String topic, String positionA, List<String> sourcesA,
                           String positionB, List<String> sourcesB) {}
}
```

### 3.3 Extraction service

```java
package ai.healthcare.summary.extract;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExtractionService {

    private final ChatClient extractClient;   // bound to the model configured for stage "extract"
    private final PromptLibrary prompts;

    public ExtractionService(@Qualifier("extractChatClient") ChatClient extractClient, PromptLibrary prompts) {
        this.extractClient = extractClient;
        this.prompts = prompts;
    }

    public SummaryExtraction extract(String summaryType, String topic, List<SourceDoc> sources) {
        String sourceBlock = SourceRenderer.render(sources);

        SummaryExtraction result = extractClient.prompt()
                .system(prompts.extractionSystem(summaryType))
                .user(u -> u.text(prompts.extractionUser())
                        .param("topic", topic)
                        .param("sources", sourceBlock))
                .call()
                .entity(SummaryExtraction.class);

        validate(result, sources);   // throws ExtractionValidationException -> one retry with error fed back
        return result;
    }

    /** Structured output is requested, not guaranteed. Enforce the contract in code. */
    static void validate(SummaryExtraction x, List<SourceDoc> sources) {
        Set<String> valid = sources.stream().map(SourceDoc::citeId).collect(Collectors.toSet());
        if (x == null || x.headline() == null || x.findings() == null || x.findings().isEmpty())
            throw new ExtractionValidationException("Missing headline or findings");
        for (var f : x.findings()) {
            if (f.sourceIds() == null || f.sourceIds().isEmpty())
                throw new ExtractionValidationException("Finding without sourceIds: " + f.statement());
            for (String id : f.sourceIds())
                if (!valid.contains(id))
                    throw new ExtractionValidationException("Unknown source id " + id);
        }
    }
}
```

**Claude Code notes:**
- If the project uses Spring AI 2.x, check whether native structured output / schema validation with retry is available and prefer it over the manual retry.
- On `ExtractionValidationException`, retry once, appending the error message to the user prompt ("Your previous output was invalid because: ... Fix only that."). After a second failure, log it and fall back to the legacy summarizer so the UI never breaks.
- Persist the `SummaryExtraction` JSON (Section 6). It is reusable for templates, search, and vendor comparisons.

### 3.4 Extraction prompt (system)

Store in `src/main/resources/prompts/extract-system.st`:

```
You are a research analyst extracting facts for a healthcare AI intelligence platform.
Your readers are hospital operators, health-system buyers, and investors. They are busy and skeptical.

Rules:
- Use ONLY the provided sources. If something is not in the sources, it does not exist for this task.
- Every finding must cite one or more source IDs exactly as given (S1, S2, ...).
- Prefer specific facts: names, numbers, dates, study sizes, dollar amounts, regulatory status.
- Do not generalize. "Epic added ambient documentation to 40 sites (S3)" beats "EHR vendors are embracing AI."
- If sources disagree, record a conflict. Do not average or smooth over it.
- If an important question is not answered, put it in unknowns. Do not guess.
- Confidence: HIGH only when a primary or authoritative source states it directly.
- Do not add opinions, predictions, or advice beyond the single soWhat field.
- Summary type for this run: {summaryType}. Follow the emphasis notes for that type below.

{typeNotes}
```

`typeNotes` examples (one per summary type, stored in `PromptLibrary`):
- `SCENARIO`: emphasize the operational problem, the AI intervention, measured outcomes, and cost.
- `VENDOR_COMPARISON`: emphasize capabilities, deployments, pricing signals, regulatory clearances, and gaps.
- `MARKET_ALERT`: separate confirmed events from speculation; `asOf` is required for every finding.

---

## 4. Item 2 — House Style Guide and Writer Prompt

### 4.1 Style guide (shared by the writer and edit passes)

Store in `src/main/resources/prompts/house-style.md`. This is the single source of truth, and the linter's banned-phrase list (Section 5) should be generated from it or kept in sync with it.

```
# AI Healthcare House Style

Voice: a sharp analyst briefing a hospital CFO in an elevator. Plain, specific, confident about facts, honest about gaps.

## Structure
- First sentence = the most important finding, with a number if one exists. No throat-clearing.
- Short paragraphs (2–4 sentences). Use bullets only for genuinely parallel items (e.g. a list of vendors), never for reasoning.
- Maximum one bolded phrase per summary. Usually zero.
- No headings inside summaries under 250 words.
- No concluding paragraph that restates the summary. End on the "so what" or an open question.
- Lists of three only when there are actually three things.

## Language
- Use concrete nouns and verbs. Name the vendor, the health system, the regulator, the number.
- Cut intensifiers: very, highly, extremely, incredibly, truly, significantly (unless statistical).
- Hedge once, precisely, where the evidence is weak: "one vendor-funded study (S4) reports..." — not "it may potentially suggest".
- Write "unknown" or "not reported" instead of vague filler.
- Plain language. Define any acronym on first use, except: AI, FDA, EHR, CMS, HIPAA.

## Citations
- Every factual sentence ends with one or more [S#] citations.
- Never cite a source for a claim it does not make.

## Banned phrases (never use)
delve, delving, tapestry, landscape (figurative), realm, navigate the complexities,
in today's rapidly evolving, ever-evolving, game-changer, game-changing, revolutionize,
revolutionizing, transformative, paradigm shift, unlock the potential, unleash, harness the power,
it's important to note, it is worth noting, it's worth mentioning, notably, importantly,
in conclusion, in summary, overall, ultimately, at the end of the day,
plays a crucial role, plays a pivotal role, crucial, pivotal, vital, robust, seamless, seamlessly,
cutting-edge, state-of-the-art, leverage (as verb), synergy, holistic, multifaceted,
a testament to, stands as, serves as a, not only ... but also, whether you're ... or ...,
moreover, furthermore, additionally (as sentence openers), embark, journey (figurative), foster.

## Voice samples
(Claude Code: leave these three placeholders. Bill will replace them with real paragraphs he likes.
 Until then, use the two examples below.)

GOOD:
"Mayo Clinic cut prior-authorization turnaround from 5.2 to 1.9 days across 3 service lines after deploying an AI triage tool, according to its Q2 operations report (S2). The vendor, Cohere Health, has not published error rates (S2, S5). Buyers should ask for denial-overturn data before signing."

BAD (do not write like this):
"In today's rapidly evolving healthcare landscape, AI is playing a pivotal role in transforming prior authorization. Notably, leading institutions are leveraging cutting-edge solutions to unlock efficiency, reduce burden, and improve outcomes. Overall, the future looks promising."
```

### 4.2 Writer prompt (Stage 2)

`src/main/resources/prompts/write-system.st`:

```
You write summaries for the AI Healthcare platform.
Follow the house style exactly:

{houseStyle}

You receive a structured extraction as JSON. Write ONLY from it.
- Do not add facts, numbers, names, or claims that are not in the extraction.
- Carry every sourceId through as [S#] citations at the end of the sentence it supports.
- Omit LOW-confidence findings unless they are the only evidence on an important point; if used, say they are weakly supported.
- If conflicts exist, state both positions in one or two sentences with their citations.
- If unknowns exist, end with the single most important one as a plain question or "Not yet reported: ...".
- Target length: {targetWords} words (±15%). Summary type: {summaryType}.
Output markdown only. No preamble, no title unless requested.
```

`write-user.st`:

```
Extraction:
{extractionJson}
```

### 4.3 Writer service

```java
@Service
public class SummaryWriter {
    private final ChatClient writeClient;
    private final PromptLibrary prompts;
    private final ObjectMapper mapper;

    public SummaryWriter(@Qualifier("writeChatClient") ChatClient writeClient,
                         PromptLibrary prompts, ObjectMapper mapper) { ... }

    public String write(String summaryType, SummaryExtraction x, int targetWords) throws JsonProcessingException {
        return writeClient.prompt()
                .system(s -> s.text(prompts.writeSystem())
                        .param("houseStyle", prompts.houseStyle())
                        .param("targetWords", targetWords)
                        .param("summaryType", summaryType))
                .user(u -> u.text(prompts.writeUser())
                        .param("extractionJson", mapper.writeValueAsString(x)))
                .call()
                .content();
    }
}
```

**Template option (no LLM):** for `VENDOR_COMPARISON` and other rigid types, implement `TemplateSummaryWriter` (same interface, e.g. `SummaryRenderer`) that builds the text from the extraction with Java string templates. Choose per summary type via config.

**Recommended settings:** writer temperature 0.3–0.5; extractor temperature 0–0.2.

---

## 5. Item 3 — Automated Check, Edit Pass, and Backfill

### 5.1 Deterministic linter (no LLM, runs on every summary)

```java
package ai.healthcare.summary.check;

import java.util.*;
import java.util.regex.*;

public class SlopLinter {

    public enum Severity { BLOCK, WARN }
    public record Finding(String rule, Severity severity, String detail) {}
    public record LintResult(int score, List<Finding> findings) {
        public boolean passes() { return findings.stream().noneMatch(f -> f.severity() == Severity.BLOCK) && score >= 80; }
    }

    private final List<Pattern> bannedPhrases;   // loaded from house-style.md "Banned phrases" section
    private static final Pattern CITATION = Pattern.compile("\\[S\\d+(?:,\\s*S\\d+)*\\]");
    private static final Pattern BOLD = Pattern.compile("\\*\\*[^*]+\\*\\*");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*([-*•]|\\d+\\.)\\s+");
    private static final Pattern SENTENCE_OPENER =
            Pattern.compile("(?m)(^|[.!?]\\s+)(Moreover|Furthermore|Additionally|Notably|Importantly|Overall|Ultimately),");
    private static final Pattern TRIPLET =
            Pattern.compile("\\b\\w+(?:\\s\\w+)?,\\s\\w+(?:\\s\\w+)?,\\sand\\s\\w+(?:\\s\\w+)?\\b");
    private static final Pattern EM_DASH = Pattern.compile("—");

    public SlopLinter(List<String> banned) {
        this.bannedPhrases = banned.stream()
                .map(p -> Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE))
                .toList();
    }

    public LintResult lint(String md, Set<String> validSourceIds) {
        List<Finding> out = new ArrayList<>();
        int score = 100;

        for (Pattern p : bannedPhrases) {
            Matcher m = p.matcher(md);
            while (m.find()) { out.add(new Finding("banned-phrase", Severity.BLOCK, m.group())); score -= 10; }
        }

        long bold = BOLD.matcher(md).results().count();
        if (bold > 1) { out.add(new Finding("too-much-bold", Severity.WARN, bold + " bold spans")); score -= 5 * (bold - 1); }

        List<String> sentences = splitSentences(md);
        long bullets = BULLET.matcher(md).results().count();
        if (bullets > 0 && bullets > sentences.size() / 2) {
            out.add(new Finding("bullet-heavy", Severity.WARN, bullets + " bullets")); score -= 10;
        }

        long uncited = sentences.stream().filter(s -> looksFactual(s) && !CITATION.matcher(s).find()).count();
        if (uncited > 0) { out.add(new Finding("uncited-claim", Severity.BLOCK, uncited + " factual sentences without [S#]")); score -= 8 * uncited; }

        Matcher c = Pattern.compile("S\\d+").matcher(String.join(" ", CITATION.matcher(md).results().map(MatchResult::group).toList()));
        while (c.find()) if (!validSourceIds.contains(c.group())) {
            out.add(new Finding("bad-citation", Severity.BLOCK, c.group())); score -= 15;
        }

        long openers = SENTENCE_OPENER.matcher(md).results().count();
        if (openers > 0) { out.add(new Finding("filler-opener", Severity.WARN, openers + "")); score -= 3 * openers; }

        long triplets = TRIPLET.matcher(md).results().count();
        if (triplets > 1) { out.add(new Finding("reflexive-triplet", Severity.WARN, triplets + "")); score -= 3 * triplets; }

        long dashes = EM_DASH.matcher(md).results().count();
        if (dashes > 2) { out.add(new Finding("em-dash-heavy", Severity.WARN, dashes + "")); score -= 2 * dashes; }

        if (restatesAtEnd(sentences)) { out.add(new Finding("restating-conclusion", Severity.WARN, "")); score -= 10; }

        return new LintResult(Math.max(score, 0), out);
    }

    // Heuristics — Claude Code: implement and unit-test.
    // looksFactual: contains a digit, a capitalized proper noun not at sentence start, or a known entity name.
    // restatesAtEnd: last paragraph starts with a summary phrase OR has >60% token overlap with the first paragraph.
    // splitSentences: a simple regex splitter is fine; ignore bullets and headings.
    private static List<String> splitSentences(String md) { ... }
    private static boolean looksFactual(String s) { ... }
    private static boolean restatesAtEnd(List<String> s) { ... }
}
```

Keep thresholds in config (`summary.lint.*`) so they can be tuned after the golden-set comparison.

### 5.2 Faithfulness check (LLM judge, sampled or always-on for high-value types)

Check that every cited sentence is supported by the sources it cites. Two options; Claude Code should choose based on the Spring AI version:

- **Option A: built-in evaluator.** Spring AI ships evaluators (`RelevancyEvaluator`, `FactCheckingEvaluator`) that implement `Evaluator` and take an `EvaluationRequest(userText, List<Document> context, responseContent)`. Verify the package and constructor in the installed version.
- **Option B: custom per-claim judge (preferred for healthcare).** Split the summary into cited sentences. For each one, send the sentence and only its cited sources to a different model from the writer (e.g. Gemini judging Claude's output) and get back a record:

```java
public record ClaimVerdict(String sentence, List<String> cited, Verdict verdict, String reason) {
    public enum Verdict { SUPPORTED, PARTIAL, UNSUPPORTED, CONTRADICTED }
}
```

Judge prompt:

```
You check claims against sources. Answer ONLY about whether the sources support the claim.
Claim: {sentence}
Sources:
{citedSources}
Return verdict SUPPORTED, PARTIAL, UNSUPPORTED, or CONTRADICTED, and a one-sentence reason quoting the source text you relied on.
```

Rule: any `UNSUPPORTED` or `CONTRADICTED` blocks the summary. More than 20% `PARTIAL` sends it to the edit pass.

### 5.3 Edit pass ("edit, don't rewrite")

One attempt only. Input: the draft, the lint findings, the claim verdicts, and the extraction.

`edit-system.st`:

```
You are a copy editor. Improve the draft below with the MINIMUM changes needed to fix the listed problems.
House style:
{houseStyle}

Rules:
- Keep every fact, number, and [S#] citation that is not flagged. Do not add new facts.
- Remove or correct any sentence marked UNSUPPORTED or CONTRADICTED. Do not replace it with a new claim.
- Fix each lint finding listed.
- Keep length within ±15% unless removing unsupported claims.
- Output the corrected markdown only.

Problems to fix:
{problems}
```

After editing, run the linter and judge again. If it passes, store it with `status = EDITED`. If it fails, store the best version with `status = NEEDS_REVIEW` and show the legacy summary in the UI.

### 5.4 Orchestrator

```java
@Service
public class SummaryPipeline {
    public SummaryResult run(SummaryRequest req) {
        List<SourceDoc> sources = sourceGatherer.gather(req);        // wraps EXISTING gathering code
        SummaryExtraction x = extractionService.extract(req.type(), req.topic(), sources);
        String draft = writerFor(req.type()).write(req.type(), x, req.targetWords());
        CheckReport check = checker.check(draft, sources);           // lint + judge
        if (!check.passes()) {
            draft = editor.edit(draft, check, x);
            check = checker.check(draft, sources);
        }
        return repository.save(req, sources, x, draft, check);
    }
}
```

### 5.5 Backfill of existing summaries (no full regeneration)

`SummaryBackfillJob`, a Spring Batch job or a simple `@Scheduled` job with paging:

1. Page through existing summaries (`WHERE backfill_status IS NULL`, batch size configurable, e.g. 50).
2. For each: run `SlopLinter` only (cheap, no LLM).
3. If it passes: mark `LINT_PASS` and leave it untouched.
4. If it fails and the original sources can be reloaded: run the edit pass with the lint findings, re-lint, and optionally judge. Store the result as a new `summary_version` row. Never overwrite the original.
5. If it fails and sources cannot be reloaded: mark `NEEDS_REGEN` for the full pipeline later.
6. Budget controls: `summary.backfill.maxLlmCallsPerRun`, `summary.backfill.dryRun=true` by default. A dry run writes a CSV report of lint scores only.
7. Report: counts per status, average lint score before and after, top 20 most frequent lint findings.

---

## 6. Data Model (PostgreSQL)

Adapt names to the existing schema. Use Flyway/Liquibase if present.

```sql
CREATE TABLE summary_run (
    id              BIGSERIAL PRIMARY KEY,
    summary_key     TEXT NOT NULL,              -- links to existing summary/scenario/vendor id
    summary_type    TEXT NOT NULL,              -- SCENARIO | VENDOR_COMPARISON | MARKET_ALERT | ...
    pipeline_ver    TEXT NOT NULL,              -- e.g. 'anti-slop-v1'
    extract_model   TEXT,
    write_model     TEXT,
    judge_model     TEXT,
    sources_json    JSONB NOT NULL,             -- SourceDoc[] with citeIds
    extraction_json JSONB,                      -- SummaryExtraction
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE summary_version (
    id              BIGSERIAL PRIMARY KEY,
    run_id          BIGINT REFERENCES summary_run(id),
    summary_key     TEXT NOT NULL,
    origin          TEXT NOT NULL,              -- LEGACY | PIPELINE | EDITED | BACKFILL_EDIT
    body_md         TEXT NOT NULL,
    lint_score      INT,
    lint_findings   JSONB,
    claim_verdicts  JSONB,
    status          TEXT NOT NULL,              -- LINT_PASS | PASS | EDITED | NEEDS_REVIEW | NEEDS_REGEN
    is_active       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX summary_version_one_active
    ON summary_version (summary_key) WHERE is_active;
CREATE INDEX summary_version_status ON summary_version (status);
```

The UI reads the `is_active` version. That makes rollback a one-row update.

---

## 7. Model Routing and Evaluation

### 7.1 Configuration

```yaml
summary:
  pipeline:
    enabled: false                 # master flag
    types-enabled: [SCENARIO]      # roll out per type
  stages:
    extract: { provider: google,    model: <gemini model id>,  temperature: 0.1 }
    write:   { provider: anthropic, model: <claude model id>,  temperature: 0.4 }
    judge:   { provider: google,    model: <gemini model id>,  temperature: 0.0 }
    edit:    { provider: anthropic, model: <claude model id>,  temperature: 0.2 }
  writer-mode:
    VENDOR_COMPARISON: TEMPLATE
    default: LLM
  lint:
    pass-score: 80
    max-bold: 1
  judge:
    mode: SAMPLE                   # ALWAYS | SAMPLE | OFF
    sample-rate: 0.25
    always-for: [MARKET_ALERT]
  backfill:
    dry-run: true
    batch-size: 50
    max-llm-calls-per-run: 500
```

Claude Code: create qualified `ChatClient` beans (`extractChatClient`, `writeChatClient`, `judgeChatClient`, `editChatClient`) from the configured provider starters already on the classpath. Fill in the current model IDs that the project's accounts can use. Do not hard-code model names in Java.

**Model choice rationale:**
- Gemini models currently lead Vectara's grounded-summarization hallucination leaderboard and Google's FACTS Grounding benchmark. Both measure faithfulness to supplied documents, which is what extraction and judging need.
  - https://github.com/vectara/hallucination-leaderboard
  - https://digitortoise.com/llm-hallucination-benchmarks/
- Claude is used for writing and editing because prose quality is the goal of those stages. This is a judgment call, and the golden-set test below should confirm or overturn it.
- The judge should use a different model from the writer, so a model is not grading its own output.

### 7.2 Golden-set comparison (required before rollout)

1. Pick 30 existing summaries across types. Include the ones Bill considers the worst.
2. Generate the new version with (a) the current routing, (b) all-Claude, and (c) all-Gemini.
3. Record, per version: lint score, count of unsupported claims, word count, cost, and latency.
4. Produce `docs/anti-slop/golden-set-report.md` with a side-by-side table and 5 full before/after examples.
5. Bill makes a blind preference pick on at least 10 of them. The chosen routing becomes the default.

---

## 8. Feature Flags and Rollout

1. `summary.pipeline.enabled=false`: code merged, nothing changes.
2. Enable in a shadow mode: the new pipeline runs and stores `summary_version` rows with `is_active=false`, and the UI still shows the legacy summary. Add an admin page or endpoint listing legacy vs new side by side.
3. Enable per summary type (start with `SCENARIO`, since it drives LinkedIn acquisition).
4. Run the backfill in `dry-run` mode, review the report, then run it for real.

---

## 9. Implementation Phases

| Phase | Scope | Exit criteria |
|---|---|---|
| 0 | Findings note (Section 0) | Bill reviews `00-findings.md` |
| 1 | House style file + `SlopLinter` + unit tests + dry-run lint report on existing summaries | Report shows baseline lint scores; no LLM changes yet |
| 2 | `SourceDoc` normalization + `SummaryExtraction` + extraction service with validation/retry | Extraction JSON valid for 95%+ of golden set; every finding cited |
| 3 | Writer (LLM + template modes) + orchestrator + tables + shadow mode | Side-by-side admin view working |
| 4 | Faithfulness judge + edit pass | Zero UNSUPPORTED/CONTRADICTED in PASS/EDITED outputs on golden set |
| 5 | Golden-set comparison report + routing decision | Bill picks routing |
| 6 | Backfill job (dry run → real) + per-type rollout | Average lint score ≥ 85; flagged NEEDS_REVIEW < 10% |

Phase 1 alone delivers value. It measures the problem with no model spend and no change to the output.

---

## 10. Tests

- `SlopLinterTest`: each rule has a positive and a negative case. The GOOD sample in the style guide must score ≥ 90, and the BAD sample must fail with a BLOCK.
- `ExtractionValidationTest`: rejects missing sourceIds, unknown IDs, and empty findings.
- `SummaryPipelineIT`: uses mocked `ChatClient` responses (no live calls in CI) and checks the full flow, including the edit-pass branch and fallback to legacy.
- `BackfillJobIT`: in dry-run mode it writes no `summary_version` rows. A real run never modifies LEGACY rows.
- Contract test: the banned-phrase list in the linter matches the list in `house-style.md` (parse the file).

---

## 11. Acceptance Criteria

- No summary shown to users contains a banned phrase or an uncited factual sentence.
- Every `[S#]` in a displayed summary resolves to a real source, and the UI can show it on hover or as a footnote list.
- Legacy summaries are preserved, and rollback is a flag flip or an `is_active` update.
- Model per stage is configurable without code changes.
- Backfill is resumable, budget-capped, and dry-run by default.
- The golden-set report exists, and Bill has signed off on the routing.

---

## 12. Open Questions for Bill (Claude Code: list any others you find)

1. Replace the two voice samples in `house-style.md` with 2–3 paragraphs you consider ideal.
2. Target length per summary type (default guess: SCENARIO 180 words, VENDOR_COMPARISON 250, MARKET_ALERT 120).
3. Should citations display as inline `[S1]` markers, footnotes, or hover cards in the UI?
4. Monthly budget cap for backfill and judge calls.
