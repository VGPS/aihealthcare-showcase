# AI Healthcare 90-Day Editorial Calendar & Strategy Specification

**Planning period:** October 1–December 29, 2026 (90 days)  
**Publishing rhythm:** One–two pieces per week (13 pieces total, healthcare AI only)  
**Target system:** AI in Healthcare application  
**Scope note:** Personal-finance topics (401k, credit scores, BNPL, student loans, HSA basics,
emergency funds, vesting) were present in the original Perplexity draft and have been removed.
All items in this calendar target the platform's B2B healthcare AI audience: compliance officers,
CMIOs, hospital CFOs, clinical operations teams, and medical device product managers.

---

## Search demand validation methodology

To maintain institutional-grade integrity and protect the application from hallucinated search figures,
search demand is validated across two distinct channels:

1. **Qualitative demand verification (Completed):** 20 targeted search-query clusters were executed
   across web and regulatory indexes. Active search intent was confirmed through regulator announcements,
   court proceedings, legislative committee analyses, and peer-reviewed clinical studies.
2. **Search-volume governance policy (Engineered):**
   - `estimated_monthly_searches` is set to `null` — never fabricated.
   - Every item includes `query_candidates` for first-party Search Console validation.
   - P0 items bypass the search-volume gate; event timing and primary-source verification drive priority.
   - P2 evergreen items require first-party data before large production investment.

---

## Thematic structure & editorial allocation

| Theme | Item count | Primary target audience | Core strategic value |
|---|---:|---|---|
| **Healthcare AI Governance, Transparency & Clinical Validation** | 6 | Compliance officers, CMIOs, medical device PMs, regulatory counsel | Positions the platform as the authoritative guide for compliant, evidence-first clinical AI deployments. |
| **Healthcare AI Economics, Prior Authorization & Workflow ROI** | 4 | Hospital CFOs, CIOs, clinical operations, revenue cycle teams | Solves the pilot-to-value gap by focusing on total cost of ownership, human-in-the-loop workflows, and measurable ROI. |
| **Healthcare Operations** | 2 | Payers, providers, CMIOs | Prior authorization timelines and ambient AI scribe adoption. |
| **Intersection: Medical Billing, Health Finances & AI Literacy** | 1 | Patients, caregivers, early adopters | Bridges healthcare and financial decision-making with the AI-era context specific to the app's audience. |

---

## Effort calibration model

- **S (Short): 2–4 hours.** Single authoritative source, tightly focused explainer (800–1,200 words).
- **M (Medium): 4–8 hours.** Multi-source analysis, comparison table, workflow map (1,200–2,000 words).
- **L (Large): 8–14 hours.** Deep-dive framework, original operational template (2,000+ words).

---

## 90-Day editorial master schedule (13 items)

| # | Window | Priority | Theme | Effort | Title & hook | Primary sources |
|---:|---|:---:|---|:---:|---|---|
| **1** | Oct 5–9 | **P0** | Healthcare Governance | **M** | **Texas TRAIGA and Healthcare AI: What the Patient Disclosure Rule Means**<br>*Hook:* "If a healthcare service uses AI, what must the patient be told—and by when?" | [Texas AG Consumer AI Rights](https://www.texasattorneygeneral.gov/consumer-protection/file-consumer-complaint/consumer-ai-rights); [Texas Legislature HB 149](https://capitol.texas.gov/tlodocs/89R/analysis/html/HB00149S.htm) |
| **2** | Oct 12–16 | **P0** | Healthcare Governance | **L** | **EU AI Act Healthcare Timeline: What Applies Now, What Moved, and What to Verify**<br>*Hook:* "The dangerous mistake is treating an old compliance deadline as current." | [European Commission AI Act](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai); [HealthSeed Timeline](https://www.healthseed.vc/insights/vital-signs-ai-healthcare-august-2026) |
| **3** | Oct 19–23 | **P0** | Healthcare Governance | **M** | **FDA PCCP Explained: How an AI Medical Device Can Plan for Future Model Changes**<br>*Hook:* "The model update is not the whole story; the regulator wants the change plan, validation method, and impact assessment." | [FDA PCCP Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/marketing-submission-recommendations-predetermined-change-control-plan-artificial-intelligence) |
| **4** | Oct 26–30 | **P0** | Healthcare Operations | **L** | **Ambient AI Scribes: Adoption Is Not the Same as Value**<br>*Hook:* "If the note gets faster but review work, consent concerns, or inequity increase, the business case is incomplete." | [Emory University Study](https://sph.emory.edu/news/new-study-finds-nearly-two-thirds-us-hospitals-using-epic-have-adopted-ambient-ai-disparities) |
| **5** | Nov 2–6 | **P0** | Healthcare Operations | **M** | **Prior Authorization in 2026: Where the 72-Hour and Seven-Day Clocks Fit**<br>*Hook:* "Faster decisions are useful only when the request is complete, the reason is visible, and the patient is not lost in the handoff." | [CMS Prior Authorization Blog](https://www.cms.gov/newsroom/blog/moving-prior-authorization-21st-century) |
| **6** | Nov 9–13 | **P1** | Healthcare Governance | **L** | **"HIPAA-Compliant" Is Not an AI Safety Strategy: Seven Controls to Inspect**<br>*Hook:* "A BAA and access control matter, but they do not prove that a clinical workflow is safe, accurate, or fit for purpose." | [AWS HIPAA-Ready GenAI Guide](https://aws.amazon.com/blogs/industries/building-a-hipaa-ready-generative-ai-architecture-for-healthcare-on-aws/) |
| **7** | Nov 16–20 | **P0** | Healthcare Operations | **M** | **AI in Prior Authorization: What Automation Can Assist With—and What Humans Must Own**<br>*Hook:* "Automation can organize evidence; it should not quietly become the accountable medical-necessity decision-maker." | [CMS Prior Authorization](https://www.cms.gov/newsroom/blog/moving-prior-authorization-21st-century); [AHA MACPAC Report](https://www.aha.org/news/headline/2026-05-12-macpac-calls-increased-transparency-ai-supported-prior-authorization) |
| **8** | Nov 23–27 | **P1** | Healthcare Economics | **L** | **The Total Cost of an AI Healthcare Pilot: License Price Is the Smallest Line Item**<br>*Hook:* "Count integration, training, review time, monitoring, exception handling, and change management before claiming ROI." | [Emory Adoption Findings](https://sph.emory.edu/news/new-study-finds-nearly-two-thirds-us-hospitals-using-epic-have-adopted-ambient-ai-disparities) |
| **9** | Nov 30–Dec 4 | **P0** | Healthcare Governance | **M** | **Colorado HB26-1139: What the 2027 Utilization-Review Requirements Imply for AI Teams**<br>*Hook:* "A utilization-review model must be tied to the individual's clinical circumstances, not only a population pattern." | [Colorado General Assembly HB26-1139](https://leg.colorado.gov/bills/HB26-1139) |
| **10** | Dec 7–11 | **P1** | Healthcare Governance | **L** | **Clinical AI Validation: Accuracy Is One Metric, Not the Finish Line**<br>*Hook:* "A model can be accurate in a test set and still fail in the handoff, the alert burden, or the patient population that matters." | [FDA AI Medical Devices Hub](https://www.fda.gov/medical-devices/digital-health-center-excellence/artificial-intelligence-enabled-medical-devices); [Emory Disparities Analysis](https://sph.emory.edu/news/new-study-finds-nearly-two-thirds-us-hospitals-using-epic-have-adopted-ambient-ai-disparities) |
| **11** | Dec 14–18 | **P1** | Healthcare Economics | **L** | **From AI Pilot to Value Case: The Evidence a Renewal Decision Should Require**<br>*Hook:* "Renewal should follow measured outcomes, full costs, and known failure modes—not enthusiasm at launch." | Evidence-first procurement methodologies |
| **12** | Dec 21–29 | **P0** | AI + Health Finances | **M** | **The 2026 Year-End Healthcare Benefits Checklist**<br>*Hook:* "Before the calendar turns, review your HSA balance, open-enrollment deadlines, prior-authorization status, and AI-tool disclosures in one sitting." | [CMS Prior Authorization](https://www.cms.gov/newsroom/blog/moving-prior-authorization-21st-century) |
| **13** | Dec 28–29 | **P1** | Healthcare Economics | **L** | **2026 Healthcare AI Pilot Postmortem: What Should Enter the 2027 Backlog?**<br>*Hook:* "A useful postmortem records not only wins, but the evidence gaps, hidden costs, and decisions that should stop." | [FDA PCCP Framework](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/marketing-submission-recommendations-predetermined-change-control-plan-artificial-intelligence); [CMS Interoperability Rules](https://www.cms.gov/newsroom/blog/moving-prior-authorization-21st-century) |

---

## Items removed from original Perplexity draft

The following 13 items were in the original "AI Healthcare + Finance" draft but are out of scope
for this B2B healthcare AI platform and have been removed:

| Original # | Title | Reason removed |
|---:|---|---|
| 4 | 2026 401(k) and IRA Limits | Consumer personal finance — wrong audience |
| 6 | Credit Score Improvement Without Folklore | Consumer personal finance — wrong audience |
| 8 | Student-Loan Repayment After July 1, 2026 | Consumer personal finance — wrong audience |
| 10 | Roth 401(k), Traditional 401(k), or IRA Decision Tree | Consumer personal finance — wrong audience |
| 14 | HSA Explained | Generic consumer explainer — HSA basics, not AI-in-healthcare |
| 18 | Emergency Funds When Savings Rates Move | Consumer personal finance — wrong audience |
| 20 | Buy Now, Pay Later in 2026 | Consumer personal finance — wrong audience |
| 22 | AI Search and Health-Insurance Scams | Consumer fraud — does not serve B2B platform audience |
| 23 | Employer Match and Vesting | Consumer personal finance — wrong audience |
| 24 | How to Use AI for Personal Finance | Consumer AI literacy — wrong audience |
| 2 | The Fake AI-Trading Bot Playbook | SEC enforcement consumer fraud — wrong audience |
| 12 | Open Banking After the CFPB Rule Stay | Fintech/consumer — wrong audience |
| 16 | When Healthcare Billing Meets AI (original AI-health-finances framing) | Reframed as item #12 healthcare benefits checklist |
