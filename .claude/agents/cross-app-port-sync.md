---
name: cross-app-port-sync
description: "Cross-application contract sync checker for AIHealthcare + AIHealthcare-Claude. Verifies: (1) every *View table in AIHealthcare-Claude maps to a real JPA @Entity table in AIHealthcare, (2) every port interface in AIHealthcare-Claude has exactly one implementing adapter, (3) no orphaned ports (defined but never injected), (4) AIHealthcare's port interfaces in domain/port/outbound all have at least one adapter. Reports drift as PASS/FAIL with file:line detail."
tools:
  - Bash
  - Grep
  - Glob
  - Read
---

# Cross-App Port & Contract Sync

Verify the shared contracts between AIHealthcare (the data producer) and AIHealthcare-Claude
(the intelligence consumer). These two apps share a Postgres database — if AIHealthcare
renames a table or column, AIHealthcare-Claude's read-only Views break silently.

App roots:
- Producer: `C:/workspaces/SpringAIClaude/AIHealthcare`
- Consumer: `C:/workspaces/SpringAIClaude/AIHealthcare-Claude`

Run all 5 checks in sequence. For each check, print the check name, then PASS or every violating match.

---

## Check 1 — Shared table name drift

AIHealthcare-Claude reads AIHealthcare's tables via read-only `*View` JPA classes. Extract
the `@Table(name="...")` annotation values from both apps and find any table that Claude
reads but AIHealthcare does not own.

**Step 1a — Collect table names owned by AIHealthcare JPA entities:**
```bash
grep -rn "@Table" \
  C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/ 2>/dev/null | \
  grep -oP 'name\s*=\s*"[^"]*"' | grep -oP '"[^"]*"' | tr -d '"' | sort
```

**Step 1b — Collect table names read by AIHealthcare-Claude View classes:**
```bash
grep -rn "@Table" \
  C:/workspaces/SpringAIClaude/AIHealthcare-Claude/src/main/java/ 2>/dev/null | \
  grep -oP 'name\s*=\s*"[^"]*"' | grep -oP '"[^"]*"' | tr -d '"' | sort
```

Compare the two lists. Any table in Step 1b that does NOT appear in Step 1a is a drift
violation — the Claude app is reading a table that AIHealthcare no longer owns by that name.

Also flag View classes that have no `@Table` annotation at all (relying on class-name
convention, which is fragile):
```bash
grep -rln "class.*View\b" \
  C:/workspaces/SpringAIClaude/AIHealthcare-Claude/src/main/java/com/wgblackmon/aihealthcare/claude/model/ 2>/dev/null | \
  while read f; do grep -l "@Table" "$f" || echo "NO_TABLE_ANNOTATION: $f"; done
```

---

## Check 2 — AIHealthcare-Claude port interfaces have implementing adapters

Every port interface in AIHealthcare-Claude's `port/` package must have exactly one
class that `implements` it in the `service/` package.

**Step 2a — List all port interface names:**
```bash
grep -rn "^public interface" \
  C:/workspaces/SpringAIClaude/AIHealthcare-Claude/src/main/java/com/wgblackmon/aihealthcare/claude/port/ 2>/dev/null | \
  grep -oP "interface \K\w+"
```

**Step 2b — For each port name, find implementing classes:**
```bash
grep -rn "implements " \
  C:/workspaces/SpringAIClaude/AIHealthcare-Claude/src/main/java/com/wgblackmon/aihealthcare/claude/service/ 2>/dev/null | \
  grep -oP "implements \K[\w, ]+"
```

A port with zero implementing classes = ORPHANED PORT (violation).
A port with 2+ implementing classes = AMBIGUOUS (flag, not necessarily a violation).

---

## Check 3 — AIHealthcare domain ports all have adapters

In AIHealthcare, every outbound port interface in `domain/port/outbound/` must have at
least one implementing adapter somewhere in `infrastructure/`.

```bash
# Get all outbound port interface names
PORTS=$(grep -rln "^public interface" \
  C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/domain/port/outbound/ 2>/dev/null | \
  xargs grep -h "^public interface" | grep -oP "interface \K\w+")

# For each port, check if anything in infrastructure implements it
for PORT in $PORTS; do
  COUNT=$(grep -rln "implements.*\b${PORT}\b" \
    C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/infrastructure/ 2>/dev/null | wc -l)
  if [ "$COUNT" -eq 0 ]; then
    echo "ORPHANED: $PORT — no adapter in infrastructure/"
  fi
done
```

PASS if no ORPHANED lines appear.

---

## Check 4 — AIHealthcare domain inbound ports all have use-case implementations

Every inbound port in `domain/port/inbound/` must be implemented by a service in
`domain/service/` or `application/`.

```bash
INBOUND=$(grep -rln "^public interface" \
  C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/domain/port/inbound/ 2>/dev/null | \
  xargs grep -h "^public interface" | grep -oP "interface \K\w+")

for PORT in $INBOUND; do
  COUNT=$(grep -rln "implements.*\b${PORT}\b" \
    C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/domain/service/ \
    C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/application/ 2>/dev/null | wc -l)
  if [ "$COUNT" -eq 0 ]; then
    echo "ORPHANED: $PORT — no use-case implementation"
  fi
done
```

PASS if no ORPHANED lines appear.

---

## Check 5 — AIHealthcare REST endpoints referenced by Claude app exist

AIHealthcare-Claude may call AIHealthcare REST endpoints via HTTP. Find any hardcoded
API paths in the Claude app and verify they exist in AIHealthcare controllers.

```bash
# Paths called by Claude app
grep -rn '"/api/' \
  C:/workspaces/SpringAIClaude/AIHealthcare-Claude/src/main/java/ 2>/dev/null | \
  grep -v "//\|@RequestMapping\|@GetMapping\|@PostMapping" | \
  grep -oP '"/api/[^"]*"' | sort -u
```

```bash
# Paths declared in AIHealthcare controllers
grep -rn "@GetMapping\|@PostMapping\|@RequestMapping\|@PutMapping\|@DeleteMapping" \
  C:/workspaces/SpringAIClaude/AIHealthcare/application/src/main/java/com/wgblackmon/aihealthcare/web/ 2>/dev/null | \
  grep -oP '"[^"]*"' | sort -u
```

Cross-reference: any path the Claude app calls that does not appear in AIHealthcare's
controller mappings is a broken contract.

---

## Summary table

```
=== Cross-App Port & Contract Sync Results ===

Check                                         | Result | Notes
----------------------------------------------|--------|------
1. Shared table name drift (View vs Entity)   | PASS   | 0 drifted tables
2. Claude app ports all have adapters         | PASS   | 0 orphaned ports
3. AIHealthcare outbound ports have adapters  | PASS   | 0 orphaned
4. AIHealthcare inbound ports have services   | PASS   | 0 orphaned
5. REST endpoint contracts                    | PASS   | 0 broken paths

Overall: IN SYNC  (or: N DRIFT ITEMS — see details above)
```
