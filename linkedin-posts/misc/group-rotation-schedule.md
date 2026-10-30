# LinkedIn Group Posting Rotation — 2 Groups/Weekday, 2-Week Cycle

Covers the 20 groups listed in `group-theme-matching.md` (Tier 1 + active Tier 2). Each group
gets posted to exactly once every 2 weeks (10 business days) — well clear of the playbook's
"wait 3+ days between posts in the same group" minimum. Weekends are skipped — the rotation is
weekday-only, matching the existing `goDailyCheckup.md` cadence.

**Anchor:** Week A begins Monday, November 2, 2026. Parity alternates weekly from that date —
`weeksSinceAnchor = floor(daysSince(2026-11-02 Monday) / 7)`; even → Week A, odd → Week B.
(Nothing is scheduled the week of 2026-10-30 since that's before the anchor — resume normal
daily-page-post-only posting through Oct 31, start group rotation Nov 2.)

Group numbers reference the table in `group-theme-matching.md`.

## Week A

| Weekday | Groups | Typical topic fit |
|---------|--------|---------------------|
| Monday | #15 Regulatory Affairs Network, #14 Healthcare AI & Ethics | REGULATORY |
| Tuesday | #1 AI/Healthcare/Life Sciences/Pharma Innovation, #11 Healthcare \| Digital Health \| AI Agents \| RCM \| EHR | DEAL_SIGNALS |
| Wednesday | #2 Digital Health Technology, #3 Healthcare and Medical Data Science ML/DL | TRENDS |
| Thursday | #6 AI in Healthcare and Pharma, #17 Life Science Network | DEAL_SIGNALS / PHARMA |
| Friday | #5 AI For Healthcare, #9 US Healthcare Product Minds + AI | COMPANY_DIRECTORY |

## Week B

| Weekday | Groups | Typical topic fit |
|---------|--------|---------------------|
| Monday | #16 Healthcare-IT/EHR/HIS, #13 AI to Transform Healthcare | REGULATORY |
| Tuesday | #4 AI, ML and DL in Healthcare & Pharmaceuticals, #18 mHealth + TeleHealth | DEAL_SIGNALS / TRENDS |
| Wednesday | #20 Healthcare & HealthTECH (OpenEHR/FHIR), #19 Healthcare Network (Digital Health/IOT) | TRENDS / DATA_SCIENCE |
| Thursday | #10 AI from the Trenches, #12 Healthcare IT & AI Chat | PLATFORM_SHOWCASE |
| Friday | #7 AI.MED Precision Medicine, #8 AI in Evidence-Based Healthcare | WIKI_REVERSAL |

## Overflow / conflict handling

If `group-posting-log.csv` shows either scheduled group was posted to in the last 10 days
(shouldn't happen under normal rotation, but can if a day was run twice or the schedule was
edited), skip that slot for today and flag it to the user rather than double-posting — don't
silently substitute a different group without asking, since substitutions break the fairness
of the rotation.
