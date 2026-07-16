---
name: substack-summary
description: Generate a Substack-ready markdown summary of the AIHealthcare project's current functionality, architecture, and progress metrics. Use when the user wants to document or publish project status.
---

# Substack Summary Skill

Generate a polished, Substack-ready markdown post summarizing the AIHealthcare project's current state. The output should be engaging for a technical audience interested in AI, healthcare, and software engineering.

## Steps

1. **Read current state** from `docs/architecture.md` — extract:
   - Completed slices (count and descriptions)
   - Test count
   - Tech stack details
   - UI pages and REST endpoints
   - Scheduler summary

2. **Read CLAUDE.md** — extract:
   - Current slice details
   - Domain types and infrastructure classes

3. **Run `mvn test -q 2>&1 | grep "Tests run:"` to get the live test count** (use the last summary line)

4. **Generate the markdown post** with this structure:

```markdown
# Building an AI Healthcare Newsletter Platform — Progress Update

## What This Project Does
[2-3 sentences: Spring Boot app that scrapes AI-in-healthcare articles, summarizes them with LLMs, and produces a formatted newsletter with attributed sources]

## Architecture at a Glance
- **Pattern**: Hexagonal / Ports-and-Adapters
- **Stack**: Spring Boot 3.x, Java 17, Spring AI 1.0 (Claude + OpenAI), PostgreSQL + pgvector
- **Testing**: [N] tests across [M] test classes, zero live AI calls in CI

## Key Capabilities
[Group features into 4-5 themed sections, each with 2-3 bullet points. Themes:]
- **Article Ingestion** — RSS feeds, web scraping, HuggingFace model discovery
- **AI Summarization** — newsletter generation, RAG-enhanced summaries, prompt evaluation
- **Research Pipeline** — Perplexity integration, multi-source synthesis, vendor comparison
- **Analytics & UI** — Thymeleaf dashboards, article listing, research run history
- **Delivery** — email subscribers, scheduled generation, NotebookLM export

## By the Numbers
| Metric | Value |
|--------|-------|
| Vertical slices completed | [N] |
| Automated tests | [N] |
| REST endpoints | [N] |
| Thymeleaf UI pages | [N] |
| Scheduled jobs | [N] |
| RSS/web feeds monitored | [N] |

## What's Next
[1-2 sentences about the next planned slice or area of focus, if known]

---
*Built with Spring Boot, Spring AI, and Claude. Code-assisted by Claude Code.*
```

5. **Output the markdown directly** to the conversation (do NOT write it to a file unless the user asks).

## Guidelines
- Keep it concise — Substack readers skim. Aim for ~500 words.
- Use bold and bullets liberally. Avoid long paragraphs.
- Emphasize what's interesting: the hexagonal architecture discipline, the test count, the breadth of integrations.
- Pull real numbers from the codebase — don't guess.
- Tone: professional but approachable. Think "engineering blog post" not "README".
