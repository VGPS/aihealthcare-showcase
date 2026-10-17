# Acquisition Outreach Targets — AI in Healthcare App Sale

> Maps directly to the `company_outreach` + `company_contacts` CRM schema already in the app.
> Add each company via the `/admin/outreach` page with purpose = "ACQUISITION".
> LinkedIn URLs marked [VERIFY] — confirm on LinkedIn before reaching out; I don't fabricate URLs.
> Emails marked [FIND] — use LinkedIn Sales Navigator, Hunter.io, or company contact page.
> Confidence = how likely this org is to actually consider a $100K acquisition at this size/stage.

---

## HOW TO USE THIS LIST

1. Enter each company in `/admin/outreach` with **purpose = ACQUISITION**, **status = NOT_CONTACTED**.
2. Add the contact(s) via the "Add Contact" form on the company detail page.
3. Use the **cold email template** at the bottom of this file as your opening message.
4. Priority order: Tier 1 → Tier 2 → Tier 3. Don't spray all at once — do Tier 1 first, learn what lands, then adjust before going wider.

---

## TIER 1 — Most Likely Buyer (small firms, founder makes the call, data-moat is real to them)

### chilmark-research
- **Company:** Chilmark Research
- **What they do:** Boutique health IT market research and advisory firm. Publishes reports on AI in healthcare specifically. Their entire business is tracking exactly what your app tracks.
- **Why they'd buy:** Instant historical archive to power their research; competitive intelligence pipeline they don't have to build; could white-label reports from your market digest.
- **Why they might not:** Small firm, may prefer to subscribe rather than acquire; John Moore is semi-retired.
- **Confidence:** HIGH
- **Outreach angle:** Lead with the historical archive, not features. "We've been retaining every FDA regulatory event and AI healthcare news article since early 2025 without deletion — I think that dataset might be useful to Chilmark's research practice."

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | John Moore |
| jobTitle | Founder / Managing Director |
| email | [FIND] |
| linkedinUrl | https://www.linkedin.com/in/jmoore2 (found via Crunchbase 2026-10-18, single-source, not independently cross-verified) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Founder; semi-retired but still active. Direct decision-maker for any acquisition. Also consider reaching Brian Eastwood (analyst) as a warm intro path. |

| Field | Value |
|-------|-------|
| fullName | Brian Eastwood |
| jobTitle | Research Analyst |
| email | [FIND] |
| linkedinUrl | [VERIFY — search "Brian Eastwood Chilmark Research"] |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Analyst; understands the domain deeply; better cold-intro path than going direct to John Moore as a first contact. |

---

### galen-growth
- **Company:** Galen Growth (HealthTech Alpha platform)
- **What they do:** Digital health market intelligence — tracks funding rounds, clinical evidence, vendor landscape. Direct comp to several of your features.
- **Why they'd buy:** They sell market intelligence subscriptions; your pipeline + historical data gives them an AI-in-healthcare vertical they can resell immediately.
- **Confidence:** HIGH
- **Outreach angle:** "You track digital health funding — we track the AI-in-healthcare subset in real time including deal signals and LLM-confirmed M&A classification. Potentially a good fit as a data layer or acquisition."

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Julien de Salaberry |
| jobTitle | Founder & CEO |
| email | [FIND] |
| linkedinUrl | https://ch.linkedin.com/in/desalaberry (verified 2026-10-18) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Founder-CEO; direct decision-maker. Basel, Switzerland-based. CORRECTION 2026-10-18: this entry previously listed Yoann Sapanel, who is NOT affiliated with Galen Growth leadership (he's Head of Health Innovation at NUS WisDM) — verified via web search and replaced with the actual Founder/CEO. |

---

### trilliant-health
- **Company:** Trilliant Health
- **What they do:** Healthcare market intelligence for health systems and health plans — market sizing, demand analytics, competitive positioning.
- **Why they'd buy:** They have distribution; adding an AI-in-healthcare regulatory/market-digest feed would extend their product surface without building.
- **Confidence:** MEDIUM-HIGH
- **Outreach angle:** Focus on the regulatory tracker + state legislation registry — those are hard data points they could surface to health-system clients worried about compliance timelines.

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Hal Andrews |
| jobTitle | CEO |
| email | [FIND] |
| linkedinUrl | [VERIFY — search "Hal Andrews Trilliant Health"] |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | CEO; Tennessee-based. For a $100K deal he'd make the call himself, but likely needs a product/data champion first — look for a VP Product or Head of Data as a secondary contact. |

---

### klas-research
- **Company:** KLAS Research
- **What they do:** Vendor performance ratings for health IT. Their entire model is tracking what healthcare vendors are doing — adjacent to your company-directory + sentiment scoring.
- **Why they'd buy:** Your sentiment/framework analysis + company discovery pipeline is exactly what they produce manually. Could augment their research operation.
- **Confidence:** MEDIUM
- **Outreach angle:** "We automatically classify vendor sentiment and score competitive positioning across 6 dimensions using LLM analysis of news coverage — I think there's overlap with what KLAS produces manually."

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Adam Gale |
| jobTitle | CEO |
| email | [FIND] |
| linkedinUrl | [VERIFY — search "Adam Gale KLAS Research"] |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | CEO; Utah-based family-owned company. Conservative culture — frame as "tool that helps your analysts" more than "replaces your process." |

---

## TIER 2 — Plausible but Harder (larger orgs, need an internal champion, may propose licensing not acquisition)

### definitive-healthcare
- **Company:** Definitive Healthcare
- **What they do:** Public company (DH on Nasdaq) selling healthcare commercial intelligence to pharma/medtech/payers. $100M+ ARR.
- **Why they'd buy:** They lack an AI-vendor-specific intelligence layer; your company directory + sentiment + deal signals could slot in as a product extension.
- **Why it's harder:** Public company — any acquisition needs corp dev approval, not just a product call. $100K is noise on their balance sheet but may not meet their deal-size threshold. More likely outcome is a data licensing discussion.
- **Confidence:** MEDIUM (as license), LOW (as acquisition) — **downgraded further, see correction below.**
- **Outreach angle:** Don't lead with "I want to sell my app." Lead with "I have a data feed tracking AI-healthcare vendor sentiment and deal signals — curious if that would be useful to your product team." Let them propose the structure.
- **CORRECTION 2026-10-18:** Definitive Healthcare is itself mid-acquisition — Advent International has made a take-private bid, and Krantz filed a Schedule 13D on 2026-09-02 disclosing a 17.6% stake while weighing a rollover. Bad timing to approach this company about anything right now; revisit after the deal resolves.

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Jason Krantz |
| jobTitle | Founder & Executive Chairman (CORRECTION 2026-10-18 — no longer CEO; Clay Ritchey became CEO 2026-09-08) |
| email | [FIND] |
| linkedinUrl | [VERIFY — search "Jason Krantz Definitive Healthcare"] |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Now Executive Chairman, not CEO — company mid take-private process with Advent International. Poor timing for outreach. If revisited later, find VP of Product or Head of Data Partnerships as internal champion, or wait to see who leads post-deal. |

---

### greenlight-guru
- **Company:** Greenlight Guru
- **What they do:** Quality management system (QMS) SaaS specifically for medtech companies, with a regulatory compliance layer.
- **Why they'd buy:** Your FDA 510(k)/De Novo regulatory tracker + state legislation registry is directly useful to their customer base (medtech companies navigating FDA clearance). Could be bundled as a "regulatory intelligence" add-on.
- **Confidence:** MEDIUM
- **Outreach angle:** "We track every FDA 510(k) clearance and De Novo grant in real time and detect regulatory pattern shifts — I think that's a natural fit for medtech companies already using Greenlight for compliance."

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | David DeRam |
| jobTitle | CEO (most sources) — one secondary source lists Executive Chairman; verify current title before outreach |
| email | [FIND] |
| linkedinUrl | https://www.linkedin.com/in/davidderam/ (verified 2026-10-18) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Founder-CEO; Indianapolis-based. More likely to propose a data partnership than an outright acquisition, but company is PE-backed (Battery Ventures) so has capital for small tuck-ins. |

---

### compliance-ai-archer
*(CORRECTION 2026-10-18 — slug was `compliance-ai-tr`; renamed because the Thomson Reuters premise below was wrong. CRM rows migrated to the new slug.)*
- **Company:** Compliance.ai (now part of Archer)
- **What they do:** Regulatory intelligence platform — tracks regulatory changes across financial services and healthcare. Acquired by **Archer** (GRC/risk-management software, formerly RSA Archer) on 2026-02-20 — **not Thomson Reuters**, which was a research error in the original draft of this doc.
- **Why they'd mention it:** Their healthcare regulatory product is the closest direct comp to your regulatory tracker. A $100K acquisition is a rounding error for Archer — but a product licensing deal for your healthcare-AI-specific data feed is plausible.
- **Confidence:** LOW (acquisition), MEDIUM (licensing)

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Kayvan Alikhani |
| jobTitle | Co-Founder & Chief Product/Strategy Officer, Compliance.ai; Global Head of Engineering - Emerging Solutions, Archer |
| email | [FIND] |
| linkedinUrl | https://www.linkedin.com/in/kayvanalikhani/ (verified 2026-10-18) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Led Compliance.ai before and after the Archer acquisition; now also leads Archer's new AI practice. Best path to a licensing conversation within Archer; don't pitch "acquisition" — pitch "data feed partnership." |

---

## TIER 3 — Long shot / relationship play (VC firms — not acquirers, but warm intro paths)

### rock-health
- **Company:** Rock Health
- **What they do:** Digital health VC fund; also publishes widely-cited annual market research reports on digital health funding.
- **Why reach out:** They PRODUCE market intelligence (funding reports). Not acquirers, but: (a) they might buy a subscription/data license for their research team, and (b) they are a warm intro path to portfolio companies who might be buyers.
- **Confidence:** LOW (acquisition), MEDIUM (intro path)

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Bill Evans |
| jobTitle | Founder & General Partner, Rock Health Capital (CORRECTION 2026-10-18 — not "Head of Research"; he ran Rock Health itself as CEO & Managing Director 2016-2020 before founding the venture fund) |
| email | [FIND] |
| linkedinUrl | https://www.linkedin.com/in/wevans (verified 2026-10-18) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | Fund GP, not an analyst — adjust pitch accordingly. Still plausible as a research-license or portfolio-intro path given Rock Health's publishing history, but frame around investment/portfolio relevance rather than "augment your research team." |

---

### flare-capital-partners
- **Company:** Flare Capital Partners
- **What they do:** Healthcare-focused VC; portfolio includes health data and analytics companies.
- **Why reach out:** Intro path to portfolio companies who might want your platform or data.
- **Confidence:** LOW (acquisition), MEDIUM (intro)

**Contacts:**
| Field | Value |
|-------|-------|
| fullName | Michael Greeley |
| jobTitle | Co-Founder / General Partner |
| email | [FIND] |
| linkedinUrl | https://www.linkedin.com/in/michaelagreeley/ (verified 2026-10-18) |
| source | MANUAL |
| status | NOT_CONTACTED |
| notes | GP; Boston-based. Don't pitch a sale — ask: "Do any of your portfolio companies track AI-healthcare regulatory events? I built a platform that does this automatically and I'm exploring options." |

---

## COLD EMAIL / LINKEDIN MESSAGE TEMPLATE

Use this for first contact. Keep it short — 5 sentences max. Paste into LinkedIn InMail or cold email. Customize the [HOOK] line per company.

---

**Subject:** AI healthcare regulatory data — quick question

Hi [First Name],

[HOOK — pick one that fits this specific company:]
- (Chilmark) "You cover AI in healthcare — we've been continuously harvesting and retaining every FDA regulatory event and AI-healthcare news article since early 2025 without deletion. I think that archive might be useful to your research practice."
- (Galen Growth) "You track digital health funding — we classify AI-healthcare deal signals in real time using LLM-confirmed M&A/funding detection. Possibly complementary to what you're building."
- (Trilliant) "We track every enacted state AI-healthcare law (41+ laws, 24 states) with automated source-freshness monitoring — I think that might be relevant to health-system clients navigating compliance timelines."
- (Greenlight Guru) "We track every FDA 510(k) clearance and De Novo grant in real time and detect regulatory pattern shifts — seems like a natural fit for medtech companies already on Greenlight for compliance."
- (Generic) "We built a platform that harvests AI-healthcare news, regulatory events, and deal signals daily and runs them through an LLM classification pipeline — the historical archive goes back to early 2025."

I'm exploring options for the platform — happy to share more if there's potential interest on your end. No deck, no pressure.

Best,
Bill Blackmon
bigskylabs.ai

---

*End of outreach targets file.*
