# LinkedIn Post and Newsletter Rules — AIHealthcare / BigSkyLabs

> Referenced by CLAUDE.md. All LinkedIn drafting must follow these rules exactly.
> Last updated: 2026-11-15 | Extracted from CLAUDE.md LinkedIn Group Post Convention.

---

## API Cost Rule (HIGHEST PRIORITY)

**NO API CHARGES shall be incurred when generating LinkedIn post or newsletter content**
unless explicitly overridden by the user in the prompt. This means:

- Use `WebSearch` (Claude Code built-in tool) for current-week sourcing — no cost.
- Do NOT call Perplexity, Anthropic, OpenAI, or Gemini APIs to generate body content.
- The Anti-Slop lint check (see below) is deterministic/zero-LLM — always safe to run.
- Applying these rules to `/dashboard/social` is **deferred** — the agent prompt
  (`social-post-agent.txt`) cannot absorb full rule set without increasing per-generation
  token cost. Revisit only when user explicitly overrides the NO API CHARGES rule.

---

## Anti-Slop Lint Requirement

All generated Post and Newsletter body content **must be run through the SlopLinter
before the output file is written.** Use `lintWiki()` mode (7 rules) — not `lint()` —
because newsletter and post content uses prose/URL attribution, not `[S#]` citation tags.

**Pass criteria:** score >= 80 AND zero BLOCK findings.

**Known structural conflict — Rule 8 (em-dash-heavy):**
The required Data Provenance format `Source description — Anchor text (URL)` uses em-dashes
as structural separators. The linter counts all em-dashes, so newsletters will always
trigger a Rule 8 WARN. This is accepted and documented here. It does not constitute a
failure — Rule 8 is WARN severity, not BLOCK.

**Complete banned phrase list (Rule 1 — BLOCK):**
delve, delving, tapestry, navigate the complexities, in today's rapidly evolving,
ever-evolving, game-changer, game-changing, revolutionize, revolutionizing, transformative,
paradigm shift, unlock the potential, unleash, harness the power, it's important to note,
it is worth noting, it's worth mentioning, notably, importantly, in conclusion, in summary,
overall, ultimately, at the end of the day, plays a crucial role, plays a pivotal role,
crucial, pivotal, vital, robust, seamless, seamlessly, cutting-edge, state-of-the-art,
leverage, synergy, holistic, multifaceted, a testament to, stands as, serves as a,
not only, moreover, furthermore, additionally, embark, foster

---

## Output File Format

### Combined output files (new standard as of 2026-11-15)

All 3 options go in **one combined file** per content type:
- `linkedin-posts/text/YYYY-MM-DD-<slug>-newsletter.txt` — all 3 newsletter options
- `linkedin-posts/text/YYYY-MM-DD-<slug>-post.txt` — all 3 post options

Each combined file must begin with:
1. A header block naming the pipeline processes used to produce the content
2. A lint results summary showing PASS/WARN/BLOCK status for each option
3. Options clearly delimited with `=====` separators

Each option must be independently pasteable (under 8,000 characters for newsletters).

**Previous rule (superseded):** Each option in its own file (`-option-a.txt`, etc.).
The combined-file format replaces this.

---

## Feed Post Rules

### First Comment — required content and ORDER

1. LinkedIn Newsletter subscribe link FIRST (auto-linkified on its own line):
   `https://www.linkedin.com/newsletters/ai-in-healthcare-intel-7514083468184805377/`
2. Free app email newsletter sign-up link SECOND (on its own line):
   `https://app.bigskylabs.ai/register`

No exceptions. This applies to every feed post and every group post.

### Post Body rules

- **Never put a URL in the Post Body.** LinkedIn suppresses reach for posts containing URLs.
- **Include the positioning statement verbatim** somewhere in the Post Body:
  *"AI in healthcare is history being written right now. I'm building the archive. Everything updates daily."*
- **Include a pointer to the First Comment** near the end of the body, before closing
  question/hashtags: `👉 Link in the first comment.`
- If the post references a specific app feature or page, the link to that feature goes
  in the First Comment — not the body.

### Post template — 4-part structure

Every feed post draft must use this exact structure:

```
===== POST BODY =====
[content]

===== FIRST COMMENT =====
[LinkedIn Newsletter link first]
[App signup link second]

===== POSTING INSTRUCTIONS =====
[1-2 groups max per sitting, 5-10 min delay before First Comment]

===== STRATEGY NOTES =====
[Data Provenance with links, Group Relevance, viral potential notes]
```

### Data Provenance and Group Relevance

Every generated Post option must include both:
- **Data Provenance** — links to source articles/studies in the Strategy Notes section
  (not in the post body). Format: `Source description — URL`
- **Group Relevance** — one sentence describing the target professional audience.

### Publishing Cadence

- **The 3 drafted options are alternatives, not a sequence.** Pick ONE per publishing
  cycle. They are designed for rotation across weeks or A/B testing over time — not
  simultaneous posting.
- **Maximum 1 feed post per day.** LinkedIn's algorithm suppresses accounts that
  over-post. Multiple posts in one day compete for the same audience slot — reach is
  throttled, not multiplied. Best practice is 3–5 feed posts per week total.
- **Rotation strategy for a 3-option batch:** post Option A this week, Option B the
  following week if the story remains relevant, hold Option C as a reserve. Compare
  engagement across weeks to identify which angle performs best with your audience.

### Pacing (anti-spam discipline)

- Post to no more than **1–2 groups per sitting.**
- Rapid cross-posting across many groups triggered LinkedIn spam-detection (2026-10-29).
  Do not draft/post an 8-group burst in one sitting again.
- Wait **5–10 minutes** after publishing before pasting the First Comment.
  Do not paste it instantly.

### File naming

Feed post drafts: `<group_slug>_option<N>_<YYYY-MM-DD>.txt`
Combined post file: `YYYY-MM-DD-<slug>-post.txt`
Unposted drafts: `linkedin-posts/text/`
Posted drafts: move to `linkedin-posts/posted/` with `STATUS: POSTED`, strip Posting Instructions.

---

## Newsletter (Long-Form Article) Rules

### Publishing Cadence

- **The 3 drafted options are alternatives, not a sequence.** Pick ONE per publishing
  cycle. They are designed for rotation across weeks or A/B testing over time — not
  simultaneous posting.
- **Maximum 1 newsletter edition per day** — LinkedIn enforces a minimum interval between
  editions (generally 24 hours). Subscribers receive a notification for each edition;
  multiple editions in one day generates unsubscribes.
- **Best practice cadence: weekly or bi-weekly.** Use Option A one week, Option B the
  following week if the story remains relevant, hold Option C as a reserve.

### Format

- Plain `.txt` files — LinkedIn Newsletter composer is a WYSIWYG editor that does not
  render pasted HTML. Raw HTML pastes as literal text.
- Bold section headings and hyperlink Data Provenance sources **manually** in the LinkedIn
  editor toolbar after pasting.
- **Maximum 8,000 characters per option.** Verify with `wc -c` before pasting.
- Each newsletter option must be independently pasteable from the combined file.

### Links in newsletters

- **Links go directly in the newsletter body** — LinkedIn does not suppress long-form
  article reach for containing URLs.
- Do NOT use a "First Comment" section for links in newsletters.
- Do NOT include `👉 Links in the first comment.` in the newsletter body.
- Place these links at the **bottom** of the pasteable body, each on its own line,
  after the hashtags and the `---` separator:
  1. App link: `https://app.bigskylabs.ai/register`
  2. LinkedIn Newsletter subscribe link:
     `https://www.linkedin.com/newsletters/ai-in-healthcare-intel-7514083468184805377/`

### Hashtags

LinkedIn newsletters have two distinct hashtag mechanisms:

**1. Structured hashtag field (max 3)** — LinkedIn's dedicated tag field shown at publish
time. These carry the most algorithmic weight for discovery. Choose the 3 most important
tags for this field. Add these to the EDITING NOTES block so the publisher knows which
3 to enter.

**2. Body hashtags (3–5)** — Hashtags written directly in the article body are clickable
and serve as reader-facing signals, but carry less algorithmic weight than the structured
field. Add these at the bottom of the pasteable body, immediately above the `---` footer
separator.

Always include `#AIHealthcare` in both locations. Add others relevant to the specific content.

```
#AIHealthcare #HealthcareAI #PatientSafety #AILiability #HealthcareLaw

---

Free weekly AI Healthcare Intelligence digest: https://app.bigskylabs.ai/register
...
```

The EDITING NOTES block must specify the 3 structured field tags separately:

```
===== EDITING NOTES (NOT FOR PASTING) =====
Hyperlink each Data Provenance anchor text to its URL in the LinkedIn Newsletter
editor after pasting.

Structured hashtag field (enter at publish time, max 3): #AIHealthcare #AILiability #PatientSafety

Group Relevance: [audience note]
```

### Data Provenance — required format

Every newsletter option must include a Data Provenance section. Format each entry as:

```
Source description — Anchor text (URL)
```

The URL is visible in the `.txt` file so it can be hyperlinked manually in the LinkedIn
WYSIWYG editor. This applies to every newsletter option in every batch.
The hyperlink instruction belongs in the EDITING NOTES block (see below), not the pasteable body.

### EDITING NOTES block — not pasteable

Each newsletter option must end with a clearly-marked block containing editor instructions
and audience metadata. This block is **NOT** part of the pasteable newsletter content.

```
===== EDITING NOTES (NOT FOR PASTING) =====
Hyperlink each Data Provenance anchor text to its URL in the LinkedIn Newsletter
editor after pasting.

Group Relevance: [one sentence describing the target professional audience]
```

Group Relevance is strategy metadata for the publisher — it is never pasted into LinkedIn.

### File naming

Combined newsletter file: `YYYY-MM-DD-<slug>-newsletter.txt`

---

## Subscriber Email Rules

Subscriber emails are **lightly-formatted HTML files** (`.html` extension):
- Inline CSS only — no `<style>` blocks.
- Structure: dark navy header bar (`#1a1a2e`), Georgia serif body, anchor-text hyperlinks,
  positioning statement in a left-border blockquote, sign-off, footer with LinkedIn
  Newsletter subscribe link.
- Reference `linkedin-posts/text/2026-11-09-subscriber-email-app-invite.html` as the
  established template.

---

## Content Rules (all post and newsletter types)

- **Never put a link (URL) in a Feed Post Body.** Only in First Comment.
- **Positioning statement** must appear verbatim in every Post Body:
  *"AI in healthcare is history being written right now. I'm building the archive. Everything updates daily."*
- **Every generated option must include Data Provenance and Group Relevance.** No exceptions.
- **Newsletter articles:** all links in the body — no First Comment for links.
- **Feed posts:** all links in the First Comment — no links in the body.

---

## Pipeline Data Sources (for content generation)

When generating content, use these app pipeline sources in order of recency/relevance.
All are NO API COST (data already harvested by the app's scheduled pipelines):

1. Market Digest (`/dashboard/market`) — daily AI healthcare market news, ranked
2. Deal Signals (`/dashboard/deals`) — M&A, funding, partnership alerts
3. Regulatory Events (`/dashboard/regulatory`) — FDA/CMS actions
4. Trend Detection (`/dashboard/trends`) — rising/fading keyword signals
5. Wiki (`/wiki`) — compiled longitudinal context and contradiction tracking
6. Articles (`/dashboard/news`) — raw harvested articles by topic
7. AI Liability Registry (`/liability`) — structured incident tracker for AI healthcare lawsuits, FDA flags, and sanctions
8. `WebSearch` (Claude Code built-in) — current-week stories, NO API COST

Do NOT call Perplexity, Anthropic, OpenAI, or Gemini APIs for content generation
unless the user explicitly overrides the NO API CHARGES rule in the prompt.
