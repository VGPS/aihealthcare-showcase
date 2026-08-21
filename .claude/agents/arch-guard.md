---
name: arch-guard
description: "Hexagonal architecture boundary guard. Runs 7 grep-based checks: domain purity (no Spring/Lombok/JPA imports), no @Autowired field injection, outbound port interfaces must live in domain not infrastructure, no Spring stereotypes in domain, web controllers must not import persistence classes, no Java Streams in production code, all cron expressions must use ${...} placeholders. Returns a PASS/FAIL table with file:line violations."
tools:
  - Bash
  - Grep
  - Glob
  - Read
---

# Architecture Guard

Verify the hexagonal architecture boundaries of the AIHealthcare Spring Boot project.

Project root: `C:\workspaces\SpringAIClaude\AIHealthcare`
Base source path: `application/src/main/java/com/wgblackmon/aihealthcare`

Run all 7 checks in sequence from the project root. For each check, print the check name, then either `PASS` or every violating `file:line` match. After all checks, print the summary table.

---

## Check 1 — Domain purity: no framework imports in domain/

The `domain/` package must have zero Spring, Lombok, JPA, or Jackson imports.

```bash
grep -rn \
  "import org\.springframework\|import lombok\|import jakarta\.persistence\|import javax\.persistence\|import com\.fasterxml" \
  application/src/main/java/com/wgblackmon/aihealthcare/domain/ 2>/dev/null | \
  grep -v "^[^:]*:[^:]*:[[:space:]]*\*"
```

PASS if output is empty. Every match is a violation.

---

## Check 2 — No @Autowired field injection

Constructor injection only. `@Autowired` on a constructor (redundant but harmless in Spring 4.3+) and `@Autowired(required = false)` on constructor parameters are both acceptable. The violation is `@Autowired` on its own line immediately followed by a `private` field declaration.

```bash
grep -rn -A1 "^[[:space:]]*@Autowired$" \
  application/src/main/java/com/wgblackmon/aihealthcare/ 2>/dev/null | \
  grep "[[:space:]]private\b"
```

PASS if output is empty. Any match is a field injection violation.

---

## Check 3 — Outbound port interfaces must not live in infrastructure/

Port interfaces belong in `domain/port/outbound/`. None should exist in `infrastructure/`.

```bash
grep -rln "^public interface.*Port\b" \
  application/src/main/java/com/wgblackmon/aihealthcare/infrastructure/ 2>/dev/null
```

PASS if output is empty. Any matching file is a violation.

---

## Check 4 — No Spring stereotype annotations in domain/

Domain classes must not carry any Spring annotations. Filter Javadoc comment lines (those whose content column starts with ` * `) to avoid false positives from `{@code @Bean}` references.

```bash
grep -rn \
  "@Service\b\|@Component\b\|@Repository\b\|@Controller\b\|@RestController\b\|@Scheduled\b\|@Bean\b\|@Configuration\b" \
  application/src/main/java/com/wgblackmon/aihealthcare/domain/ 2>/dev/null | \
  grep -v "^[^:]*:[^:]*:[[:space:]]*\*"
```

PASS if output is empty.

---

## Check 5 — Web controllers must not import infrastructure persistence classes

Controllers must call application-layer use cases only — never repositories or JPA entities directly.

```bash
grep -rn \
  "import com\.wgblackmon\.aihealthcare\.infrastructure\.persistence" \
  application/src/main/java/com/wgblackmon/aihealthcare/web/ 2>/dev/null
```

PASS if output is empty. Known architectural debt: WikiController, WatchlistController, LegalTimelineController, SentimentDashboardController, WebMonitoringController, WikiCompilationController, AdminWikiGapController, WikiGapAnalysisController, CompanyProfileController bypass the port layer and access JPA directly. Each requires a dedicated refactor slice to fix.

---

## Check 6 — No Java Streams in production code

Project convention: use traditional for loops for all iteration. Streams are prohibited in production source.

```bash
grep -rn "\.stream()\|\.parallelStream()" \
  application/src/main/java/com/wgblackmon/aihealthcare/ 2>/dev/null | \
  grep -v "/test/"
```

PASS if output is empty. Test source violations are informational only (note them but do not FAIL).

---

## Check 7 — All @Scheduled cron expressions must use ${...} placeholders

No hardcoded cron strings. All schedules must reference application.yml via `${aihealthcare...}`. Filter Javadoc comment lines to avoid false positives from `{@code @Scheduled}` references.

```bash
grep -rn "@Scheduled" \
  application/src/main/java/com/wgblackmon/aihealthcare/ 2>/dev/null | \
  grep -v '\${' | \
  grep -v "^[^:]*:[^:]*:[[:space:]]*\*"
```

PASS if output is empty. Any remaining match is a hardcoded schedule.

---

## Summary table

After all checks, print this table filled in with results:

```
=== Architecture Guard Results ===

Check                                  | Result | Violations
---------------------------------------|--------|----------
1. Domain purity (no framework imports)| PASS   | 0
2. No @Autowired field injection        | PASS   | 0
3. Port interfaces in domain only       | PASS   | 0
4. No Spring annotations in domain      | PASS   | 0
5. Controller layer isolation           | DEBT   | 9 controllers (see note in check 5)
6. No Streams in production code        | PASS   | 0
7. Externalized cron expressions        | PASS   | 0

Overall: CLEAN  (or: N VIOLATIONS — see details above)
```

For any FAIL row, list the full `file:line: content` under the table.
