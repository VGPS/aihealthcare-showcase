# AI in Healthcare — Sale Listing Draft

> Draft for Acquire.com / Flippa. Read the "Honest Notes for Bill" section before you post this anywhere — it explains why $100K is an aggressive ask for a pre-revenue asset and how to defend it.

---

## Honest Notes for Bill (not for the listing — read this first)

Marketplace buyers price pre-revenue SaaS almost entirely on **replacement cost** and
**unreplicable assets**, not vibes. Your case for $100K rests on three things, in order
of how convincing they are to a buyer:

1. **The historical article archive is unreplicable.** You've been harvesting and
   retaining AI-healthcare news since early 2025 with zero deletion. A buyer cannot
   recreate that time machine by writing code — they can only buy it from you. Lead
   with this, not the tech stack.
2. **Replacement cost is genuinely high.** 3,000+ tests, hexagonal architecture,
   14+ feature slices, multi-LLM integration, Stripe billing, SAML2 SSO, an async
   enterprise-data-export job system. A contractor team would bill well into six
   figures to rebuild this from scratch. This is your second-strongest argument.
3. **Zero revenue is a real liability** buyers will discount hard for — expect most
   serious offers to land below $100K unless someone values the data/IP specifically
   (a healthcare analytics firm, a market-research shop, an AI-in-healthcare
   consultant, or a competitor wanting instant data history). That's a strategic
   buyer, not a typical Flippa flipper — which is why I'd pursue **Acquire.com +
   direct outreach in parallel**, not Flippa alone.

To defend $100K, consider offering 20–40 hours of paid transition support
(a week or two of your time at closing) — buyers heavily discount pre-revenue
technical assets when they're worried about "what happens when the builder
disappears." Offering continuity materially de-risks the deal for them.

Don't put exact revenue/subscriber numbers in the listing if they're zero — just say
"pre-revenue" plainly. Marketplace buyers immediately distrust listings that dodge
the question.

---

## LISTING DRAFT (copy below this line)

### Title
**AI in Healthcare — Production-Ready Market Intelligence Platform with Unreplicable Historical Data Archive**

### One-liner
A fully built, production-deployed Spring Boot platform that has been continuously
harvesting, archiving, and AI-summarizing AI-in-healthcare news, regulatory events,
and market signals since early 2025 — sold with its full codebase, infrastructure,
and the historical data archive itself.

### The Asset

This is not an idea or a prototype. It's a live, deployed application
(bigskylabs.ai) with:

- **A growing, continuously-harvested historical archive** of AI-in-healthcare
  news articles, FDA/CMS regulatory events, state legislation, company data, and
  deal signals — harvested daily since early 2025, now nearly 20 months of
  uninterrupted history. Retained permanently, never deleted. This dataset cannot
  be recreated after the fact; its value compounds with every day it isn't sold.
- **A production codebase** — Java 21 / Spring Boot, hexagonal (ports-and-adapters)
  architecture, 3,100+ automated tests across 357 test classes, CI pipeline on
  GitHub Actions.
- **A working subscription business model already wired** — 4-tier access control
  (free/subscriber/enterprise), Stripe billing integration, usage metering, and an
  enterprise data-export product (async job system with scheduled delivery, email
  attachments, signed download links).

### Why This Is Valuable

**1. The data moat.** AI regulation and the AI-healthcare vendor landscape are
moving fast enough that "what did the FDA say in March, and how does that compare
to what a company claimed in June" is a real, hard problem. This platform has an
LLM-compiled internal "wiki" layer that tracks exactly that — including automatic
contradiction detection when a new claim conflicts with an earlier one. No one can
buy their way into having a year+ of this history; they can only buy it from
someone who already built it.

**2. Multi-source, multi-model intelligence pipeline.** The platform ingests from
RSS/web scraping, FDA/CMS APIs, Perplexity Search, and HuggingFace, then runs
content through four interchangeable LLM providers (Claude, GPT, Gemini,
Perplexity) for synthesis, sentiment scoring, competitive framework analysis, and
deal-signal classification. Swapping or adding an AI provider requires no domain
changes — the adapter layer was built for exactly this.

**3. Enterprise-grade infrastructure already exists.** SAML2 SSO with JIT user
provisioning, role-based access control, an audited async data-export system with
a confined-filesystem security model and IP-pinned outbound HTTP guard, and a
scheduled push-delivery system with at-least-once delivery guarantees. This is the
kind of infrastructure that normally takes a team months to harden — it's done.

**4. 17+ shipped feature verticals**, including:
- Regulatory alert system (FDA 510(k)/De Novo, CMS rules)
- Custom watchlists with keyword/company/topic matching
- Company sentiment & risk scoring (LLM-classified)
- 6-dimension competitive framework analysis
- Keyword trend detection (rising/fading/new, with historical snapshots)
- Deal signal detection with LLM-confirmed M&A/funding classification
- Daily market digest (earnings, regulatory, funding, M&A) with price-reaction
  scoring against live market data
- State health-AI legislation registry (41+ enacted laws, 24 states) with
  automated source-freshness monitoring and new-bill discovery
- Public company directory with signal-based scoring and CSV export
- AI-powered multi-model search with citation tracking
- Document library (PDF/DOCX/TXT ingestion into a public wiki)
- Newsletter generation and delivery pipeline (the platform's original use case)

### Tech Stack

Java 21 (Corretto), Spring Boot, Spring AI, PostgreSQL + pgvector, Spring Security
(session + SAML2 SSO), Stripe, AWS SES, Thymeleaf, Maven, JUnit 5 + AssertJ +
Mockito, GitHub Actions CI. Deployed on AWS EC2 with systemd hardening
(`ProtectSystem=strict`, IMDSv2-only, network-egress restrictions on the
enterprise data path).

### Business Model (Already Built, Not Just Planned)

- 4-tier access: free preview → paid subscriber → enterprise self-service console
- Stripe subscription checkout + billing portal, already live in test mode
- Usage metering and feature gating enforced server-side, not just UI-hidden
- An enterprise "data export as a service" product: customers submit async jobs
  against curated data feeds, get CSV/JSON artifacts via email or signed download
  link, on a schedule they control (cron-based, per-customer)

### Status

- **Pre-revenue.** No paying subscribers yet — this is an asset/IP sale, not a
  cash-flow sale. Billing infrastructure is built and tested but not yet marketed
  to paying customers.
- Actively maintained; last commits within the past week.
- Single founder/developer; no employees, no outside investment, no existing
  contracts or encumbrances.

### What's Included

- Full source code and git history (private GitHub repo transfer)
- Production database (full historical article/event/data archive)
- Domain registration transfer assistance (bigskylabs.ai)
- AWS infrastructure documentation (EC2 setup, systemd units, environment config)
- [Optional — negotiate at closing] Transition support: founder available for
  paid consulting hours post-sale for onboarding/handoff

### What's Not Included

- No existing paying customers or revenue to transfer
- Third-party API keys (Anthropic, OpenAI, Perplexity, Stripe, AWS) are not
  transferable — buyer must provision their own

### Ideal Buyer

- A healthcare analytics, market-research, or competitive-intelligence firm
  wanting an instant head start on AI-in-healthcare vertical data
- An AI/LLM infrastructure buyer looking for a working multi-provider
  orchestration codebase to repurpose for another vertical
- A solo technical operator who wants a fully-built product to market
  aggressively rather than build from zero

### Asking Price

**$100,000**, open to offers. Given pre-revenue status, price reflects
replacement cost of the codebase/infrastructure plus the unreplicable value of
the historical data archive — not a revenue multiple. Happy to discuss structured
deals (partial upfront + earnout, or a transition-support retainer).

### Reason for Selling

Founder-built as a solo developer over ~20 months. The platform has reached a
point where the next 10x of value comes from distribution and sales — not more
engineering. The right buyer brings either an existing customer base to sell
into, complementary data assets, or the bandwidth to market aggressively. A
solo operator running a pipeline business while also doing sales and support is
not the optimal structure for what this has become.

---

*End of listing draft.*
