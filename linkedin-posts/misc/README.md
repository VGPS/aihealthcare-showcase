# AIHealthcare — LinkedIn Feature Post Rotation

A six-week weekday rotation. One post per menu-item feature, 30 in total, in
`feature-posts.yml`. Post Monday–Friday, cycle repeats every six weeks with
fresh screenshots and fresh live numbers.

## Why a rotation and not a queue

A queue runs out. A rotation is a habit you can keep for a year, and the
second pass through a feature is better than the first — you have new data,
a better screenshot, and six weeks of comment feedback telling you which
angles land.

## Weekday themes

Themes exist so six weeks of posts don't read as one long product tour.
Somebody who sees every post gets variety; somebody who only ever sees
Wednesdays gets a coherent beat.

| Day | Theme | What it covers |
|-----|-------|----------------|
| Monday | **Signal** | What moved — regulatory, deals, trends, legal |
| Tuesday | **Ask it** | Prompt-driven features with a visible answer |
| Wednesday | **Company intelligence** | Who's who and how they connect |
| Thursday | **Knowledge & provenance** | The wiki layer and its receipts |
| Friday | **Craft** | How it's built, how you plug into it |

## The full schedule

| Week | Mon | Tue | Wed | Thu | Fri |
|------|-----|-----|-----|-----|-----|
| 1 | Regulatory Alerts | Multi-Model AI Search | Public Directory | Reversal Watch | Newsletter Generation |
| 2 | Deal Signals | Wiki Q&A | Sentiment & Risk | Knowledge Wiki | Pipeline Orchestrator |
| 3 | Trend Detection | Vendor Comparison | Company Relationships | Source Provenance | Developer API |
| 4 | Legal Timeline | Article Search | Company Profiles | What Changed Digest | Webhooks |
| 5 | Trend History | Research Runs | Watchlists | Topic-Grouped News | Tiers & Pricing |
| 6 | Deal Context | Framework Analysis | Company Discovery | Clinical Trials | Prompt Evaluation |

## Posting workflow

This mirrors the workflow already built into `/dashboard/linkedin` — body
first, links as comment #1 — because LinkedIn suppresses reach on posts with
outbound links in the body.

1. Open today's entry in `feature-posts.yml`.
2. Capture the screenshot per that entry's `screenshot` block. Save it to
   `sShots-LinkedIn/` as `<id>.png`.
3. **Check the `redact` line before you post the image.** Several entries have
   real secrets on screen — the developer portal shows API keys, the webhooks
   page shows endpoint URLs with tokens in them.
4. Decide between `hook` (evergreen, always true) and `live_variant` (needs a
   real number from the screenshot you just took). The live variant performs
   better. Never post a `{{PLACEHOLDER}}` unfilled.
5. Paste the `body` into LinkedIn. Attach the image with the entry's
   `alt_text` — LinkedIn's alt-text field is a real accessibility win and
   almost nobody fills it in.
6. Publish, then immediately paste `first_comment` as the first comment.
7. Add `hashtags` at the end of the body or in the first comment. Four is the
   working maximum; more reads as reach-chasing.

## What's in each entry

| Field | Purpose |
|-------|---------|
| `hook` | Evergreen opening line, true without any live data |
| `body` | Full post, link-free, under 3,000 characters |
| `live_variant` | Alternate opener using a real number you fill in |
| `first_comment` | The URLs, including the 7-day trial link |
| `hashtags` | Four, tuned per post |
| `screenshot.page` | Exactly where to go |
| `screenshot.prompt` | The prompt to type, where the page takes one |
| `screenshot.capture` | What must be in frame and where to crop |
| `screenshot.redact` | What to blur before posting |
| `screenshot.alt_text` | Paste into LinkedIn's alt-text field |

`{{site_base_url}}`, `{{trial_url}}`, `{{pricing_url}}`, `{{directory_url}}`
and `{{about_url}}` resolve from the `meta:` block at the top of the YAML —
change a URL in one place and every post follows.

## Verify before the first post

Two URLs in the library were inferred from template filenames rather than the
README's page table. Confirm them in `nav.html` and fix the YAML if they're
wrong:

- `clinical-trials` → assumed `/dashboard/clinical-trials`
- `webhooks` → assumed `/dashboard/webhooks`

Also confirm `{{trial_url}}` — the library uses `/register`, which the README
describes as self-registration for DEMO users with a 7-day trial. If you'd
rather send people to `/pricing` first, change it once in `meta:`.

## An honest note on the claims

Every factual statement in these posts traces to something the platform
actually does, per the README feature table and page inventory. Nothing
asserts a metric that isn't in the codebase.

The `live_variant` fields are the deliberate exception: they're templates for
numbers only you can supply, from the screen in front of you on the day. That
split is intentional — the evergreen text is safe to post unread, and the
parts that need a human are marked as needing one.

## Making it a feature instead of a file

The natural next step is to move this into the app: read the YAML into a
`FeaturePost` record, add a `FeaturePostRotationService` that resolves
day-of-week to today's entry, and render it as a second tab on
`/dashboard/linkedin` beside the existing daily-articles generator. Same
copy-to-clipboard flow, nothing to open a file for.

It's also a clean teaching slice for the course: config-driven content, a
domain record, one port, a service with a genuinely testable rule
(day-of-week → post), and a Thymeleaf view. No AI call anywhere in it, which
makes it a good contrast case against the slices that do call out.
