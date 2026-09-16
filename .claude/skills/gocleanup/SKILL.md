---
name: gocleanup
description: "Codebase health sweep: duplicates, bugs, security, architecture, load test. Presents all findings for review before any fixes."
---

# Go Cleanup — Codebase Health Orchestrator

Run 5 analysis passes against the AIHealthcare codebase, present all findings grouped by
severity, and wait for user review before touching anything.

**This skill NEVER auto-fixes.** Every pass produces a findings report. After all passes
complete, present a consolidated punch list and ask the user which items to fix.

## Arguments (from `/gocleanup <args>`)

```
/gocleanup              → run all 5 passes
/gocleanup quick        → passes 1-4 only (skip load test)
/gocleanup load         → pass 5 only (load test)
/gocleanup dupes        → pass 1 only
/gocleanup bugs         → pass 2 only
/gocleanup security     → pass 3 only
/gocleanup arch         → pass 4 only
```

If no argument or `all`, run passes 1-5 in order.

---

## Pass 1 — Duplicate Code Detection

Goal: find copy-paste duplication across production source (not tests).

### 1a. Structural duplicates — methods with identical or near-identical bodies

Use the Agent tool with `subagent_type: "Explore"` to search for:

- **Identical method bodies** — scan service, adapter, and controller classes for methods
  that share >80% of their lines. Focus on:
  - `infrastructure/persistence/*Adapter.java` — toEntity/toDomain mappers often duplicated
  - `web/controller/*Controller.java` — model-population patterns often copy-pasted
  - `infrastructure/ai/*Adapter.java` — LLM response parsing logic
  - `domain/service/*Service.java` — pipeline orchestration steps

- **Duplicate utility logic** — grep for repeated patterns:
  ```
  Pattern: pipe-delimited serialization (String.join("|"...) / .split("\\|"))
  Pattern: date formatting (DateTimeFormatter / DISPLAY_FMT)
  Pattern: tier-gating checks (user.getTier() comparisons)
  Pattern: slug generation logic
  ```

### 1b. Report format

For each duplicate cluster found, report:
```
DUPE-N: <short description>
  Files: <file1>:<lines>, <file2>:<lines>
  Similarity: ~XX%
  Suggested fix: <extract helper / shared base method / utility class>
```

---

## Pass 2 — Bug Detection

Goal: find logic errors, null-safety issues, and correctness problems.

Launch an Agent (`subagent_type: "general-purpose"`) to review production source for:

### 2a. Null safety
- Nullable record fields used without null checks in service/adapter code
- `Optional.get()` without `isPresent()` / `orElseThrow()`
- Stream operations on potentially null collections
- `@Nullable` port method returns consumed without guards

### 2b. Logic errors
- Off-by-one errors in pagination, list slicing, or loop bounds
- Missing `break` in switch statements (if any non-pattern switches exist)
- Equality checks using `==` on Strings or enums where `.equals()` is needed
- Inconsistent sorting — comparators that violate transitivity
- try-catch blocks that swallow exceptions silently (catch block is empty or only logs)

### 2c. Concurrency
- Shared mutable state in `@Service` classes (non-final fields mutated after construction)
- Race conditions in `@Scheduled` methods that share state
- Unsynchronized access to `Map`/`List` fields from async executors

### 2d. Data integrity
- JPA entities missing `equals()`/`hashCode()` overrides when used in Sets
- Repository methods returning `List` where duplicate suppression is assumed but not enforced
- `@Transactional` missing on methods that do multiple writes

### 2e. Report format

For each bug found, report:
```
BUG-N: <short description>
  File: <file>:<line>
  Severity: CRITICAL / HIGH / MEDIUM / LOW
  Evidence: <the specific code pattern and why it's wrong>
  Risk: <what could go wrong in production>
```

---

## Pass 3 — Security Review

Goal: find vulnerabilities per OWASP Top 10 and project-specific security invariants.

Launch an Agent (`subagent_type: "general-purpose"`) to check:

### 3a. OWASP Top 10
- **Injection** — any string concatenation in SQL/HQL/JPQL queries, or unsanitized input
  reaching `ProcessBuilder` / `Runtime.exec()`
- **Broken auth** — endpoints missing `@PreAuthorize` or tier checks that should have them,
  session fixation gaps
- **Sensitive data exposure** — secrets, passwords, API keys, or PII in logs, error messages,
  templates, or HTTP responses. Check for `.env` values or `secretRef` values leaking.
- **XXE** — XML parsers without `FEATURE_SECURE_PROCESSING` (Rome/RSS parsing especially)
- **Broken access control** — verify ownership checks use `findByXAndOwnerEmail`, never
  `findById` + post-check (per ED-1 invariant #5)
- **Security misconfiguration** — CORS wildcards, permissive CSP, debug endpoints in prod
- **XSS** — `th:utext` in Thymeleaf templates without sanitization (check every occurrence)
- **SSRF** — any user-controlled URL reaching `RestClient`/`WebClient`/`HttpURLConnection`
  outside `RemoteEndpointGuard`

### 3b. Project-specific invariants (from CLAUDE.md ED-1 section)
- No natural-language to SQL anywhere
- No filesystem call outside `ConfinedFileStore` on enterprise paths
- No outbound HTTP outside `RemoteEndpointGuard` on customer-triggered paths
- No tool calling / MCP client / agent loop on customer-triggered paths
- Secrets are references, never values — `secretRef` never logged/returned/rendered
- Ownership resolved IN the query — never `findById` + check

### 3c. Credential hygiene
- Grep for anything that looks like a hardcoded secret:
  ```
  Pattern: password|secret|api[_-]?key|token|credential followed by = or : and a literal value
  Pattern: Base64-encoded strings >40 chars in .java or .yml files
  ```
- Check that `data.sql` password hashes are bcrypt (`$2a$`) not plaintext
- Verify `.env` is in `.gitignore`

### 3d. Report format

For each finding, report:
```
SEC-N: <short description>
  File: <file>:<line>
  Category: <OWASP category or project invariant>
  Severity: CRITICAL / HIGH / MEDIUM / LOW
  Evidence: <the specific code and why it's a vulnerability>
  Remediation: <specific fix>
```

---

## Pass 4 — Architecture Boundary Check

Goal: verify hexagonal architecture rules are not violated.

Launch the `arch-guard` agent. It runs 7 automated grep-based checks:

1. Domain purity (no Spring/Lombok/JPA imports in `domain/`)
2. No `@Autowired` field injection
3. Outbound port interfaces live in `domain/` not `infrastructure/`
4. No Spring stereotype annotations in `domain/`
5. Web controllers don't import persistence classes
6. Stream usage audit (informational — streams are now allowed)
7. All `@Scheduled` cron expressions use `${...}` placeholders

Report the full results table from the agent.

---

## Pass 5 — Production Load Test

Goal: verify production performance under concurrent load.

**Only run when:**
- The user explicitly included `load` or `all` in the arguments
- The app is deployed and healthy (pre-check `https://app.bigskylabs.ai/login` returns 200)

### 5a. Pre-flight
- Check that k6 is installed (try `k6 version` then `"/c/Program Files/k6/k6.exe" version`)
- Verify `https://app.bigskylabs.ai/login` returns 200
- Warn: "Load test will hit production with 50 VUs for ~5 minutes. Proceed?"
- **Do NOT run during harvest window (04:00-04:30 UTC)**

### 5b. Ask for password
If the user didn't provide a password in the args, ask:
"What password should k6 use to log in to bigskylabs.ai?"

### 5c. Run k6
Delegate to the existing `/goloadtest` skill logic:
```bash
TIMESTAMP=$(date +%Y-%m-%d-%H%M%S) && \
mkdir -p logs/k6 && \
"/c/Program Files/k6/k6.exe" run \
  --out "json=logs/k6/run-${TIMESTAMP}.json" \
  --log-output "file=logs/k6/run-${TIMESTAMP}.log" \
  --env MAX_VUS=50 \
  --env K6_USERNAME=wgblackmonall@gmail.com \
  --env K6_PASSWORD=<password> \
  load-test/k6-smoke.js 2>&1 | tee "logs/k6/run-${TIMESTAMP}-summary.txt" | tail -55
```

### 5d. Report format
```
PERF-N: <metric that failed threshold>
  Metric: <name>
  Value: <observed>
  Threshold: <expected>
  Page: <which page/group if applicable>
```

---

## Consolidated Report

After all requested passes complete, present one unified report:

```
=== CLEANUP REPORT ===

Pass 1 — Duplicates:    N findings
Pass 2 — Bugs:          N findings
Pass 3 — Security:      N findings
Pass 4 — Architecture:  N findings (or PASS/FAIL)
Pass 5 — Load Test:     N findings (or SKIPPED)

--- CRITICAL (fix immediately) ---
<list any CRITICAL severity items from all passes>

--- HIGH (fix soon) ---
<list any HIGH severity items>

--- MEDIUM (fix when convenient) ---
<list any MEDIUM severity items>

--- LOW (informational) ---
<list any LOW severity items>

Total: N findings across M passes
```

Then ask:
**"Which items should I fix? You can say 'all CRITICAL', specific IDs like 'BUG-3, SEC-1',
or 'none' to just keep this as a reference."**

Wait for the user's response before making any code changes.

---

## Fixing (after user approval)

When the user selects items to fix:

1. Fix one item at a time
2. After each fix, run the relevant test counterpart (`mvn test -Dtest=...`)
3. Report what changed and whether tests pass
4. Move to the next approved item
5. After all fixes, re-run the pass that found them to confirm resolution

Do NOT:
- Fix items the user didn't approve
- Refactor adjacent code while fixing
- Introduce new abstractions beyond what the fix requires
- Auto-commit — leave that to the user or `/commit`
