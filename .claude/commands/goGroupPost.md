# Go Group Post — Targeted Group Content Scheduler

Picks today's 2 scheduled LinkedIn groups from the rotation, matches each to the day's
strongest content angle, and generates a tailored, manually-postable text for each — distinct
from the main page post and distinct from each other (never post identical text to two groups
on the same day — see `group-outreach-playbook_v2.md`).

**Scope note:** this generates content only. It does NOT auto-publish to LinkedIn — there is no
LinkedIn API/OAuth integration in this app, and posting stays manual (copy/paste), same as the
existing `goDailyCheckup.md` workflow.

## Step 0 — Skip weekends

If today is Saturday or Sunday, stop and tell the user: the group rotation is weekday-only,
nothing is scheduled today.

## Step 1 — Determine today's week parity and scheduled groups

Read `linkedin-posts/misc/group-rotation-schedule.md`. Compute `weeksSinceAnchor` from Monday,
November 2, 2026 (the anchor). Even → Week A, odd → Week B. Look up today's weekday in that
week's table to get the 2 scheduled groups.

If today is before the anchor date (2026-11-02), tell the user the rotation hasn't started yet
and stop — don't invent an earlier schedule.

## Step 2 — Check the posting log for conflicts

Read `linkedin-posts/misc/group-posting-log.csv`. If either scheduled group has a row within the
last 10 days, STOP and flag it to the user — don't silently substitute a different group (see
"Overflow / conflict handling" in the schedule doc). Ask whether to skip that slot today or
proceed anyway.

## Step 3 — Get today's topic and core data

If `goDailyCheckup.md` has already run today in this session, reuse its chosen topic, verified
data points, and (if relevant) the live insights page URL — don't re-pull data that's already in
context. Otherwise, run goDailyCheckup's Steps 1–3 inline (data pull + topic selection) to get
today's strongest story before continuing.

## Step 4 — Match topic to each scheduled group

Read `linkedin-posts/misc/group-theme-matching.md`. For each of today's 2 scheduled groups,
check whether its primary tags include today's topic category.
- **Match:** write that group's post around today's topic, framed in the group's own voice
  (see Step 5).
- **No match:** don't force it. Fall back to that group's own strongest tag and pick an angle
  from that category instead — use today's data as supporting color if it still fits naturally,
  otherwise pull from the "Standing templates" list in `goDailyCheckup.md` or the group's own
  audience description for a relevant evergreen angle (e.g. a company-directory stat, a
  platform/architecture note, a prior week's trend that's still fresh).

## Step 5 — Write two distinct, group-voiced posts

Follow the tone and structure of the existing intro posts in `group-outreach-playbook_v2.md`
(conversational, data-forward, ends in a genuine discussion question inviting replies — not a
hard CTA). Guidelines:
- **Length:** 4-8 short paragraphs, similar to the playbook's intro post examples.
- **Links:** intro posts avoided links ("earn credibility first"). Since most Tier 1/2 groups
  already got an intro post by 2026-10-29, direct links (e.g. `/directory`, `/legislation`,
  the day's insights page) are fine in these targeted posts — but still only one link per post,
  placed near the end, same discipline as the main page post.
- **Newsletter link:** do NOT add the LinkedIn Newsletter subscribe line to group posts. That
  rule (`CLAUDE.md` → LinkedIn Group Post Convention) is specifically about the **First Comment**
  on page/group posts that already follow the infographic-post format with a First Comment
  section. Group posts here are single-block text (no separate first-comment step) — if you
  want to keep it, add one short line at the end instead: "📰 I also write a LinkedIn Newsletter
  — AI in Healthcare Intel — if you'd rather get this as a weekly digest."
- **Group-specific framing:** reuse the phrasing patterns already proven in
  `group-outreach-playbook_v2.md` for that specific group where available (e.g. the Regulatory
  Affairs Network post already has a working template — adapt it with fresh data rather than
  reinventing the angle).
- **Never identical text in both of today's posts**, even if both groups share a topic tag —
  vary the opening hook, the specific data cited, and the closing question.

## Step 6 — Save output

Save to `linkedin-posts/text/YYYY-MM-DD-groups-{group-1-slug}-{group-2-slug}.txt` with this
structure:

```
GROUP 1: {group name} (#{group number})
=========================================
{post text}

---

GROUP 2: {group name} (#{group number})
=========================================
{post text}

---

POSTING INSTRUCTIONS
======================
1. Post to Group 1 first, Group 2 at least a few minutes later (pacing — see
   feedback_linkedin_posting_pace memory: no back-to-back bursts).
2. No image required unless today's page-post infographic is directly relevant — if so,
   reference linkedin-posts/shots/YYYY-MM-DD-{topic-slug}.png.
3. This fills both of today's 2 scheduled group slots. Do not post to additional groups
   today outside the rotation.
```

## Step 7 — Log the posts

Append two rows to `linkedin-posts/misc/group-posting-log.csv`:
```
YYYY-MM-DD,{group_number},{group_name},{topic_category},linkedin-posts/text/YYYY-MM-DD-groups-{slug1}-{slug2}.txt
```
One row per group. This is what Step 2 checks on future runs — don't skip it.

## Step 8 — Present to user

Show both group posts as separate labeled code blocks, plus the posting instructions, same
presentation style as `goDailyCheckup.md` Step 7.

## Relationship to goDailyCheckup

These two commands are complementary, not redundant:
- `goDailyCheckup.md` → one post to Bill's own page (infographic + post + first comment),
  3-4x/week, skip if nothing has a hook.
- `goGroupPost.md` → two group posts/weekday, always runs on weekdays once the Nov 2 2026
  rotation starts (groups need steady cadence to build presence — unlike the page, "skip if
  weak" doesn't apply here, since Step 4's fallback always finds a usable angle per group).
Both can run the same morning; they should NOT post identical text anywhere (page post, page's
first comment, or either group post).
