# AIHealthcare — Claude Code Project Memory

## What This Project Is
A Spring Boot application that scrapes AI-in-Healthcare articles from the web weekly,
summarizes them with Spring AI, and produces a formatted newsletter draft with attributed sources.

See @docs/architecture.md for full module layout and design decisions.
See @docs/CONVENTIONS.md for all coding rules (logging, loops, records, class headers).

---

## LLM Behavioral Guidelines
> Adapted from [Karpathy guidelines](CLAUDE_karpathy.md). These complement the project-specific conventions below.

### Think Before Coding
- State assumptions explicitly. If uncertain, ask before implementing.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.

### Simplicity First
- No features beyond what was asked. No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- If you write 200 lines and it could be 50, rewrite it.

welinkedInRules.md for all LinkedIn post and newsletter formatting rules,
including: NO API CHARGES constraint, Anti-Slop lint requirement, combined output
file format, First Comment link order, Data Provenance format, and known conflicts.

---

## What NOT to Do
- Do not add auth/security until explicitly requested.
- Do not modify `openapi.yaml` without confirming the change first.
- Do not place business logic in controllers or adapters.
- Do not use `@Autowired` field injection — constructor injection only.
- Do not put outbound port interfaces in `infrastructure` packages — they belong in `domain.port.outbound`.
- Do not use `com.aihealthcare.*` as the base package — all code uses `com.wgblackmon.aihealthcare.*`.
