# LinkedIn Group Theme-Matching Table

Data source for `.claude/commands/goGroupPost.md`. Built from the audience descriptions in
`group-outreach-playbook_v2.md`. Scope: Tier 1 (14 core AI-healthcare groups) + Tier 2 active
(6 adjacent healthcare/tech groups — excludes the 2 Tier-2 groups flagged "Low" priority in the
playbook: "Healthcare & Technology Opportunities" and "Pharmaceutical & Medical Network"). Tier 3
mega-groups (Leadership Think Tank, AI Mindshare, Analytics/AI in Marketing, Generative AI, Java
Developers and Architects, Startup Specialists) are excluded from this rotation entirely — reshare
only, per the playbook.

Topic categories match the day-mapping already used in `.claude/commands/goDailyCheckup.md`
(Regulatory / Deal Signals / Trends / Wiki Reversal / Company Directory), plus a few categories
specific to group audiences that don't map 1:1 to the daily page-post rotation.

## Topic category legend

| Code | Meaning |
|------|---------|
| REGULATORY | FDA/CMS clearances, state AI-healthcare legislation |
| DEAL_SIGNALS | M&A, funding rounds, partnerships |
| TRENDS | Rising/fading keyword data |
| WIKI_REVERSAL | Wiki contradiction / reversal-watch stories |
| COMPANY_DIRECTORY | Directory spotlight, sector/consolidation data |
| SENTIMENT_RISK | Company sentiment & risk scoring |
| PLATFORM_SHOWCASE | Meta/engineering angle — "how I built this," architecture |
| CLINICAL_RESEARCH | Precision medicine, evidence-based practice, clinical trials |
| RCM_AGENTS | Revenue cycle management, AI agents, EHR/HIS workflow |
| DATA_SCIENCE | Technical ML/AI angle |
| PHARMA | Pharma-specific framing |
| ETHICS | AI ethics, disclosure, consent, accountability |

## Group table (20 active groups)

| # | Group | Tier | Audience | Primary tags |
|---|-------|------|----------|---------------|
| 1 | AI, Artificial Intelligence in Healthcare, Life Sciences, Medical and Pharmaceutical Innovation | 1 | Mixed: health execs, researchers, vendors | COMPANY_DIRECTORY, DEAL_SIGNALS, PLATFORM_SHOWCASE |
| 2 | Digital Health Technology - AI in healthcare, eHealth, mHealth | 1 | Digital health practitioners, innovators | TRENDS, COMPANY_DIRECTORY, REGULATORY |
| 3 | Healthcare and Medical Data Science with ML/DL AI | 1 | Data scientists, ML engineers in health | DATA_SCIENCE, PLATFORM_SHOWCASE, TRENDS |
| 4 | AI, Machine Learning and Deep Learning in Healthcare & Pharmaceuticals | 1 | Technical AI + pharma crossover | DATA_SCIENCE, PHARMA, DEAL_SIGNALS |
| 5 | AI For Healthcare | 1 | General AI healthcare audience | COMPANY_DIRECTORY, DEAL_SIGNALS, REGULATORY |
| 6 | AI in Healthcare and Pharma | 1 | Pharma-leaning AI audience | PHARMA, REGULATORY, DEAL_SIGNALS |
| 7 | AI.MED - Precision Medicine, Advanced Analytics | 1 | Clinical + precision medicine | CLINICAL_RESEARCH, WIKI_REVERSAL, SENTIMENT_RISK |
| 8 | AI in Evidence-Based Healthcare | 1 | Clinical researchers, EBM focus | WIKI_REVERSAL, CLINICAL_RESEARCH, SENTIMENT_RISK |
| 9 | US Healthcare Product Minds + AI | 1 | Product managers, US-focused | PLATFORM_SHOWCASE, COMPANY_DIRECTORY |
| 10 | AI from the Trenches: Book of Why (Healthcare & Life Sciences) | 1 | Thoughtful practitioners | PLATFORM_SHOWCASE, WIKI_REVERSAL |
| 11 | Healthcare \| Digital Health \| AI Agents \| RCM \| EHR | 1 | RCM + AI agents — very specific | RCM_AGENTS, COMPANY_DIRECTORY, DEAL_SIGNALS |
| 12 | Healthcare IT & AI Chat | 1 | Small, high-engagement, flexible | REGULATORY, TRENDS, COMPANY_DIRECTORY |
| 13 | AI to Transform Healthcare | 1 | Small, mission-driven | REGULATORY, ETHICS, PLATFORM_SHOWCASE |
| 14 | Healthcare AI & Ethics | 1 | Tiny but niche — ethics angle | ETHICS, REGULATORY, WIKI_REVERSAL |
| 15 | Regulatory Affairs Network: Pharma, Medical Devices, Biotech | 2 | Regulatory professionals | REGULATORY (strongest fit in the whole table) |
| 16 | Healthcare-IT/ EHR/ HIS | 2 | Health IT professionals | RCM_AGENTS, REGULATORY, COMPANY_DIRECTORY |
| 17 | Life Science Network | 2 | Broad life science | PHARMA, REGULATORY, DEAL_SIGNALS |
| 18 | mHealth + TeleHealth, Wearables, Remote Health, AI, Blockchain | 2 | Digital health / remote care | TRENDS, COMPANY_DIRECTORY |
| 19 | Healthcare Network: Digital Health, IOT Medical, Telemedicine | 2 | Broad digital health | TRENDS, COMPANY_DIRECTORY |
| 20 | Healthcare & HealthTECH: Innovation Lowcode & AI (OpenEHR/FHIR) | 2 | Technical health standards | RCM_AGENTS, DATA_SCIENCE |

## Reverse lookup — topic category → ranked groups

| Topic | Best-fit groups (ranked) |
|-------|---------------------------|
| REGULATORY | 15, 2, 5, 6, 12, 13, 14, 16, 17 |
| DEAL_SIGNALS | 1, 4, 5, 6, 11, 17 |
| TRENDS | 2, 3, 4, 12, 18, 19 |
| WIKI_REVERSAL | 7, 8, 10, 14 |
| COMPANY_DIRECTORY | 1, 2, 5, 9, 11, 16, 18, 19 |
| SENTIMENT_RISK | 7, 8 |
| PLATFORM_SHOWCASE | 1, 3, 9, 10, 13 |
| CLINICAL_RESEARCH | 7, 8 |
| RCM_AGENTS | 11, 16, 20 |
| DATA_SCIENCE | 3, 4, 20 |
| PHARMA | 4, 6, 17 |
| ETHICS | 13, 14 |

## Matching rule

For a given day's chosen topic (see `goDailyCheckup.md` Step 2), look up the two groups
scheduled for that weekday (`group-rotation-schedule.md`). If a scheduled group's primary tags
include today's topic category, write its post in that angle. If not, fall back to that group's
own strongest tag instead of forcing a mismatched pitch — e.g. if Wednesday's topic is a deal
signal but a scheduled group's only fit is DATA_SCIENCE, frame that group's post around the
technical/architecture angle instead, using the same underlying data as a supporting detail
rather than the headline.
