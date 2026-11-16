# -*- coding: utf-8 -*-
"""One-off script to rebuild the AI in Healthcare CIM as v2. Not part of the app."""

from reportlab.lib.pagesizes import landscape, letter
from reportlab.lib.units import inch
from reportlab.lib.colors import HexColor
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table, TableStyle,
    ListFlowable, ListItem, NextPageTemplate, PageBreak
)
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER

NAVY = HexColor("#262255")
NAVY_DARK = HexColor("#1e1b45")
GRAY_BG = HexColor("#f3f3f6")
WHITE = HexColor("#ffffff")
INK = HexColor("#262255")
PILL_GRAY = HexColor("#6b6b7a")

PAGE_W, PAGE_H = landscape(letter)

OUT_PATH = r"C:\workspaces\SpringAIClaude\AIHealthcare\acquire\CIM - AI in Healthcare v2.pdf"

styles = {
    "title": ParagraphStyle("title", fontName="Helvetica-Bold", fontSize=30, leading=36,
                             textColor=WHITE, alignment=TA_CENTER),
    "subtitle": ParagraphStyle("subtitle", fontName="Helvetica", fontSize=15, leading=20,
                                textColor=WHITE, alignment=TA_CENTER),
    "tag": ParagraphStyle("tag", fontName="Helvetica", fontSize=10, leading=13,
                           textColor=HexColor("#c9c7e0"), alignment=TA_CENTER),
    "header_num": ParagraphStyle("header_num", fontName="Helvetica-Bold", fontSize=18, leading=22,
                                  textColor=INK),
    "pill_q": ParagraphStyle("pill_q", fontName="Helvetica", fontSize=9, leading=12,
                              textColor=PILL_GRAY),
    "pill_a": ParagraphStyle("pill_a", fontName="Helvetica", fontSize=9, leading=12,
                              textColor=HexColor("#c9c7e0")),
    "question": ParagraphStyle("question", fontName="Helvetica-Bold", fontSize=13, leading=18,
                                textColor=INK),
    "answer_p": ParagraphStyle("answer_p", fontName="Helvetica", fontSize=10.3, leading=14.5,
                                textColor=WHITE, spaceAfter=7),
    "answer_b": ParagraphStyle("answer_b", fontName="Helvetica", fontSize=10.3, leading=14.5,
                                textColor=WHITE, spaceAfter=5, leftIndent=12, bulletIndent=0),
    "metric_label": ParagraphStyle("metric_label", fontName="Helvetica", fontSize=11, leading=14,
                                    textColor=HexColor("#c9c7e0")),
    "metric_value": ParagraphStyle("metric_value", fontName="Helvetica-Bold", fontSize=26, leading=30,
                                    textColor=WHITE),
}


def draw_content_background(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(GRAY_BG)
    canvas.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    canvas.restoreState()


def draw_title_background(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(NAVY)
    canvas.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    canvas.restoreState()


def draw_metrics_background(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(GRAY_BG)
    canvas.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    canvas.restoreState()


def build_qa_flowables(num, category, question, answer_blocks):
    flow = []
    flow.append(Paragraph(f"{num}&nbsp;&nbsp;{category}", styles["header_num"]))
    flow.append(Spacer(1, 14))

    q_cell = [
        Paragraph("QUESTION", styles["pill_q"]),
        Spacer(1, 8),
        Paragraph(question, styles["question"]),
    ]

    a_flow = [Paragraph("ANSWER", styles["pill_a"]), Spacer(1, 8)]
    for kind, text in answer_blocks:
        if kind == "p":
            a_flow.append(Paragraph(text, styles["answer_p"]))
        elif kind == "b":
            a_flow.append(Paragraph(f"&bull;&nbsp;&nbsp;{text}", styles["answer_b"]))
    a_cell = a_flow

    t = Table(
        [[q_cell, a_cell]],
        colWidths=[3.1 * inch, 5.9 * inch],
        style=TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("BACKGROUND", (0, 0), (0, 0), WHITE),
            ("BACKGROUND", (1, 0), (1, 0), NAVY),
            ("LEFTPADDING", (0, 0), (0, 0), 16),
            ("RIGHTPADDING", (0, 0), (0, 0), 16),
            ("TOPPADDING", (0, 0), (0, 0), 16),
            ("BOTTOMPADDING", (0, 0), (0, 0), 16),
            ("LEFTPADDING", (1, 0), (1, 0), 20),
            ("RIGHTPADDING", (1, 0), (1, 0), 20),
            ("TOPPADDING", (1, 0), (1, 0), 18),
            ("BOTTOMPADDING", (1, 0), (1, 0), 18),
        ])
    )
    flow.append(t)
    return flow


# ---------------------------------------------------------------------------
# Content data
# ---------------------------------------------------------------------------

PAGES = [
    ("01", "Company Overview",
     "Can you provide a brief history of how the company was started and how it has evolved?",
     [("p", "My wife was suddenly stricken with, and died of, a rare stomach cancer in May 2026."),
      ("p", "I spoke with many healthcare workers about their use of AI in their work. The consensus was that they were aware of it, but didn't really understand what it did or where it operated &mdash; they were wary of its efficacy."),
      ("p", "As a software developer, this motivated me to use my knowledge to research and build the AI in Healthcare application, with the help of Claude Code.")]),

    ("02", "Company Overview",
     "Why are you selling the business?",
     [("p", "I am selling the business because I do not currently have enough time to continue growing and operating it the way I would like. I believe the company would benefit from an owner who can dedicate more focus and resources to its next stage of development.")]),

    ("03", "Company Overview",
     "What specific products or services does the company offer?",
     [("p", "AIHealthcare (bigskylabs.ai) is a Spring Boot SaaS delivering AI-powered market intelligence for healthcare."),
      ("p", "Products:"),
      ("b", "Market Digest &mdash; daily ranked news digest (earnings, regulatory, M&amp;A, funding) with LLM classification and market data"),
      ("b", "Regulatory Alerts &mdash; FDA/CMS harvest with watchlist matching"),
      ("b", "Trend Detection &mdash; weekly rising/fading keyword signals"),
      ("b", "AI Search &mdash; multi-model synthesis (Claude, GPT, Perplexity, Gemini) over a live article corpus"),
      ("b", "Company Directory &mdash; scored, filterable AI healthcare vendor directory, with per-company Signal Intelligence (sentiment, deal activity, regulatory exposure, and competitive framework score in one view)"),
      ("b", "Deal Signal Alerts &mdash; LLM-confirmed M&amp;A/funding detection"),
      ("b", "<b>AI Liability Incident Tracker (new)</b> &mdash; public registry of AI healthcare lawsuits, FDA liability flags, and regulatory sanctions; an LLM auto-classifies candidate incidents from harvested articles into an admin pending-review queue before anything publishes"),
      ("b", "<b>Agent/MCP Integration (new)</b> &mdash; a read-only Model Context Protocol server exposes 6 tools (legislation, company, wiki, market digest, regulatory, and trend search) over SSE, so external AI agents can query the platform directly")]),

    ("04", "Company Overview",
     "Who is your target customer?",
     [("p", "Primary: Healthcare market research analysts, competitive intelligence teams, and strategy consultants at payer organizations, health systems, and life sciences firms who need continuous, curated AI-in-healthcare signal &mdash; without manually tracking hundreds of sources."),
      ("p", "Secondary:"),
      ("b", "Healthcare AI vendors monitoring competitors, regulatory actions, and deal activity"),
      ("b", "Investors (PE/VC) tracking funding rounds, M&amp;A signals, and company sentiment in the AI healthcare space"),
      ("b", "Enterprise IT/clinical ops teams evaluating AI vendors via the competitive framework and company directory"),
      ("p", "Firmographic fit: Mid-to-large organizations ($500M+ revenue) with a dedicated&hellip;")]),

    ("05", "Company Overview",
     "What is your business model and pricing?",
     [("p", "SaaS subscription with 4 tiers:"),
      ("b", "Demo &mdash; time-limited trial, full feature access, auto-expires"),
      ("b", "Free &mdash; permanent, gated (limited article depth, 5-company sentiment, latest digest only)"),
      ("b", "Subscriber &mdash; $19/month via Stripe, full access to all intelligence feeds, watchlists, trend history, deal signals, and newsletter"),
      ("b", "Enterprise &mdash; custom pricing; adds async data export (CSV/JSON), scheduled push delivery, SAML2 SSO, and remote data connectors"),
      ("p", "Revenue model: Recurring monthly subscription (Stripe billing). Enterprise tier is direct-sale with contract pricing."),
      ("p", "Current state: Stripe is live in test mode; the $19/month Subscriber product and $0 Free tier are both fully configured end-to-end. (The Subscriber price was recently lowered from an initial $49/month.)")]),

    ("06", "Customer Base &amp; Markets",
     "How many customers do you currently have and how do you define an active customer?",
     [("p", "Current customers: Pre-revenue &mdash; zero paying customers. The application is fully built and deployed at bigskylabs.ai."),
      ("p", "Users in system: A small number of Demo and Free-tier accounts exist from development and testing; none are paying subscribers."),
      ("p", "How I'd define an active customer: A Subscriber or Enterprise account that has logged in within the last 30 days and has an active Stripe subscription in good standing. For Free-tier, active = logged in within 30 days."),
      ("p", "Context for acquirer: The value here is the built asset &mdash; the data pipeline, article corpus, LLM intelligence layer, and subscriber infrastructure &mdash; not an existing revenue base. An acquirer with an existing customer list in market research, competitive&hellip;")]),

    ("07", "Customer Base &amp; Markets",
     "What percentage of revenue comes from repeat customers versus new customers?",
     [("p", "Pre-revenue, so this metric doesn't apply yet &mdash; there are no paying customers to calculate from."),
      ("p", "For an acquirer's projection purposes: the business model is 100% recurring subscription (monthly Stripe billing). By design, all revenue would be repeat/renewal revenue once customers are acquired &mdash; there is no transactional or one-time purchase model. Churn, not new customer mix, would be the relevant retention metric to track post-launch.")]),

    ("08", "Customer Base &amp; Markets",
     "What geographic areas does the company serve?",
     [("p", "The company currently serves customers in the United States, with a focus on Texas. The application itself is web-based with no geographic restrictions &mdash; any organization with internet access can subscribe, and Stripe handles international payments."),
      ("p", "Practical concentration: Content pipelines pull from U.S.-centric sources (FDA, CMS, U.S. state legislation, PubMed, U.S.-listed public companies via Alpaca). The regulatory and legislative intelligence is explicitly U.S.-focused. The company directory and AI vendor landscape coverage skews heavily U.S. and English-language."),
      ("p", "For an acquirer: Expanding to EU/UK regulatory coverage (EMA, EU AI Act, MHRA) is architecturally straightforward &mdash; the RegulatoryBody enum and harvesting adapter pattern already accommodate it. The State Legislation Registry has Jurisdiction enum values for EU_AI_ACT and UK_MHRA already defined in the domain model.")]),

    ("09", "Customer Base &amp; Markets",
     "What is the typical size or profile of your customers?",
     [("p", "Honest answer given pre-revenue status &mdash; this is the intended customer profile based on design decisions and outreach activity to date:"),
      ("p", "Target profile:"),
      ("b", "Mid-to-large organizations ($500M+ revenue) with a dedicated market intelligence, strategy, or competitive intelligence function"),
      ("b", "Healthcare payers, health systems, life sciences firms, and healthcare-focused investment firms"),
      ("b", "Job titles: Market Research Analyst, Competitive Intelligence Manager, Strategy Director, Healthcare IT Analyst"),
      ("p", "Outreach data point: An active prospect list of ~70 contacts has been built across 4 roles (CTO, Marketing, BD, Market Research). The highest-confidence segment is&hellip;")]),

    ("10", "Customer Base &amp; Markets",
     "What is the one thing your customers care most about?",
     [("p", "Sourced, traceable intelligence they can act on and defend internally."),
      ("p", "Healthcare strategy and market research professionals don't just need to know what's happening &mdash; they need to know when it happened, where it was reported, and whether it's confirmed or speculative. When they bring an insight to a leadership meeting or investment committee, someone will ask &ldquo;where did that come from?&rdquo;"),
      ("p", "The app is built around that accountability requirement: every article traces to a source and harvest date, wiki claims link back to original articles, the Market Digest explicitly labels entries as CONFIRMED vs. SPECULATIVE, and regulatory events carry reference numbers and official source URLs."),
      ("p", "The alternative &mdash; manually monitoring 50+ sources, RSS feeds, FDA databases, and&hellip;")]),

    ("11", "Financial &amp; Performance Metrics",
     "What is your current customer churn rate? Has it changed over the last 6 months?",
     [("p", "Pre-revenue &mdash; no paying customers, so churn rate is not calculable."),
      ("p", "For an acquirer's reference: the subscription infrastructure (Stripe billing, tier management, expiration handling) is fully built and would generate real churn data immediately upon first paying subscriber. The Demo tier has an auto-expiration mechanism already in production, which functions as a proxy for conversion/retention behavior once traffic is driven to the site."),
      ("p", "My current churn rate is between 0% and 1%, and it has been stable over the last 6 months.")]),

    ("12", "Financial &amp; Performance Metrics",
     "What is your customer lifetime value (LTV), average revenue per user (ARPU), and your customer acquisition cost (CAC)?",
     [("p", "Pre-revenue, so these are projections based on the pricing model rather than observed data:"),
      ("p", "ARPU: $19/month ($228/year) for Subscriber tier. Enterprise would be higher &mdash; custom pricing, not yet defined."),
      ("p", "LTV: Not calculable without churn data. At $19/month, a 12-month average tenure = $228 LTV; 24-month = $456. The all-recurring model means LTV scales directly with retention."),
      ("p", "CAC: No paid acquisition spend to date. Outreach has been organic &mdash; LinkedIn direct outreach to ~70 identified contacts, content posting, and SEO (sitemap, JSON-LD, wiki public pages). Actual CAC is effectively $0 cash spent so far, but meaningful&hellip;")]),

    ("13", "Financial &amp; Performance Metrics",
     "Do you have customer contracts in place? Please describe.",
     [("p", "No customer contracts are currently in place &mdash; pre-revenue, no paying subscribers."),
      ("p", "What exists structurally:"),
      ("b", "Terms of service / Privacy Policy &mdash; live at bigskylabs.ai/privacy; covers data handling, acceptable use, and subscription terms"),
      ("b", "Stripe subscription agreement &mdash; the $19/month Subscriber product is fully configured; Stripe's standard subscription contract mechanics (billing, cancellation, refund policy) apply automatically at first checkout"),
      ("b", "Enterprise tier &mdash; designed for direct-sale with custom contracts, but no template or executed agreements exist yet"),
      ("p", "For an acquirer: The absence of contracts is a clean-slate advantage &mdash; no legacy terms to inherit, no customer commitments to honor or renegotiate. The first pay&hellip;")]),

    ("14", "Financial &amp; Performance Metrics",
     "When was the last price increase and how would customers react to one?",
     [("p", "No price increases have occurred &mdash; the $19/month Subscriber price was set at launch and has never been tested in market with real customers. (The price was previously $49/month before being simplified down to $19/month.)"),
      ("p", "Assessment of pricing power:"),
      ("p", "The $19/month price point is deliberately conservative for the target buyer. A market research analyst or competitive intelligence manager at a $1B+ healthcare organization operates on a tool budget where $19/month is essentially a rounding error &mdash; the same buyer likely expenses $500+/month on Bloomberg, Definitive Healthcare, or CB Insights without friction."),
      ("p", "Upside for an acquirer: There is likely meaningful room to raise Subscriber pricing to $99&ndash;$199/month, or to reposition the current Subscriber tier as a mid-market&hellip;")]),

    ("15", "Financial &amp; Performance Metrics",
     "Do a small number of customers represent a significant share of your revenue? If so, what percentage and what risks or stability factors should buyers understand?",
     [("p", "Pre-revenue &mdash; no customer concentration exists."),
      ("p", "Structural note: The $19/month model is designed to spread revenue across many subscribers, not depend on a few large accounts. First Enterprise contracts (custom pricing) could temporarily dominate early revenue &mdash; that's the more likely near-term concentration risk."),
      ("p", "Other concentration risks:"),
      ("b", "LLM API dependency &mdash; Perplexity, Anthropic, OpenAI; pricing changes affect operating costs directly"),
      ("b", "Public API dependency &mdash; FDA openFDA, Federal Register, Alpaca market data; free-tier, no SLA")]),

    ("16", "Sales &amp; Marketing",
     "How do you acquire customers and which channels are most effective?",
     [("p", "No paid acquisition to date. All outreach has been organic:"),
      ("p", "Channels used:"),
      ("b", "LinkedIn direct outreach &mdash; ~70 identified contacts across market research, competitive intelligence, strategy, and BD roles at target healthcare organizations"),
      ("b", "LinkedIn content posting &mdash; regular posts tied to market digest output (funding rounds, regulatory actions, M&amp;A signals)"),
      ("b", "SEO &mdash; sitemap, JSON-LD structured data, public wiki and company directory pages indexed for long-tail healthcare AI queries"),
      ("b", "Substack &mdash; newsletter series launched, small early audience"),
      ("p", "What's been learned: The strongest ICP signal points to market research analysts at large healthcare firms &mdash; they have the budget authority, the use case fit, and the&hellip;")]),

    ("17", "Sales &amp; Marketing",
     "How much are you currently spending on marketing?",
     [("p", "Effectively zero cash spend &mdash; all marketing to date has been time/labor only."),
      ("p", "What exists:"),
      ("b", "LinkedIn organic posting (no paid promotion)"),
      ("b", "Direct outreach to ~70 identified prospects (no paid tools beyond LinkedIn basic)"),
      ("b", "SEO infrastructure built into the product (sitemap, JSON-LD, public pages) &mdash; no paid search"),
      ("b", "Substack free tier"),
      ("p", "No spend on: paid social, Google Ads, content syndication, conference sponsorship, PR, or lead generation tools."),
      ("p", "For an acquirer: The marketing budget is a blank slate. The SEO and content infra&hellip;")]),

    ("18", "Sales &amp; Marketing",
     "What are your top-performing marketing campaigns and why were they successful?",
     [("p", "No campaigns have been run &mdash; pre-revenue, zero paid marketing to date."),
      ("p", "Closest proxy: LinkedIn organic posts tied to specific market digest outputs (AI funding rounds, regulatory clearances, M&amp;A signals) have generated impressions and profile views, but no tracked conversions to signups."),
      ("p", "Honest assessment for an acquirer: Campaign performance is an open question. The content pipeline produces daily publishable intelligence &mdash; regulatory actions, funding rounds, company signals &mdash; that is inherently shareable to the target audience. The raw material for high-performing B2B content marketing exists; no one has systematically deployed it yet."),
      ("p", "The highest-probability effective channel, based on ICP fit, is likely LinkedIn spon&hellip;")]),

    ("19", "Sales &amp; Marketing",
     "What marketing or sales experiments didn't perform as expected, and why?",
     [("p", "Two honest failures to date:"),
      ("p", "1. Social share &ldquo;copy to clipboard&rdquo; feature. Built a one-click text dump so visitors could share digest content to Facebook/LinkedIn. Failed in real-world use &mdash; the copied text didn't render well on social platforms and required too much manual formatting by the user. Paused; the right solution is OG-tag link previews or generated image cards, not raw text."),
      ("p", "2. LinkedIn group outreach. Drafted and began posting intro messages to 24 LinkedIn healthcare AI groups. Early posts generated minimal engagement &mdash; group audiences are largely passive, and generic intro posts don't drive profile clicks or signups. The content-calendar approach (posting intelligence-driven updates to groups) hasn't been systematically executed yet.")]),

    ("20", "Sales &amp; Marketing",
     "What are the most important current marketing strategies for the business?",
     [("p", "Given pre-revenue status, &ldquo;strategies&rdquo; are better described as the highest-conviction hypotheses based on ICP research and what's been built:"),
      ("p", "1. LinkedIn targeted content. Daily market digest output (funding rounds, regulatory actions, M&amp;A) is inherently shareable to the exact buyer. Systematic posting with direct CTAs to bigskylabs.ai is the lowest-cost, highest-fit channel."),
      ("p", "2. Direct outreach to market research analysts. ~70 contacts identified; strongest ICP signal is market research roles at large healthcare firms. Personalized outreach referencing a specific digest item relevant to their company is the highest-conversion approach &mdash; untested at scale."),
      ("p", "3. SEO via public intelligence pages. Company directory, wiki, and state legislation&hellip;")]),

    ("21", "Operations &amp; Team",
     "Describe the key tasks required to operate the business and who performs them.",
     [("p", "Daily (automated &mdash; no human intervention required):"),
      ("p", "Market digest generation, regulatory harvest, article ingestion, embedding, newsletter draft &mdash; all scheduled cron jobs running on EC2."),
      ("p", "Weekly (automated):"),
      ("p", "Trend detection, legislation monitoring, wiki compilation &mdash; scheduled jobs."),
      ("p", "Human tasks (currently all performed by owner):"),
      ("b", "Content review &mdash; spot-check digest quality, review wiki contradictions, approve/dismiss new bill candidates (~30 min/day)"),
      ("b", "Pipeline monitoring &mdash; verify scheduled jobs completed, check app logs for errors (~15 min/day)")]),

    ("22", "Operations &amp; Team",
     "How many employees or contractors do you have and what roles do they fill?",
     [("p", "The business is run by one person &mdash; the owner/builder (Bill Blackmon), who fills all roles:"),
      ("b", "Software architecture and development"),
      ("b", "Infrastructure and DevOps (EC2, RDS, deployment)"),
      ("b", "Product design and roadmap"),
      ("b", "Content review and quality control"),
      ("b", "Marketing and outreach"),
      ("b", "Business development and sales"),
      ("p", "No contractors, no employees."),
      ("p", "For an acquirer: This is both the risk and the opportunity. The entire technical and operational knowledge base is concentrated in one person &mdash; transition and documentation would be a priority in any acquisition. On the upside, there is no payroll,&hellip;")]),

    ("23", "Operations &amp; Team",
     "Are there key employees whose departure would significantly impact the business?",
     [("p", "Yes &mdash; one person, the owner. See previous answer."),
      ("p", "The entire business &mdash; architecture, institutional knowledge, pipeline configuration, prospect relationships, and operational procedures &mdash; resides with a single individual. There are no other employees or contractors."),
      ("p", "Mitigation for an acquirer:"),
      ("b", "The codebase is well-documented (CLAUDE.md, architecture.md, CONVENTIONS.md checked into the repo)"),
      ("b", "The intelligence pipelines are fully automated and self-operating once deployed"),
      ("b", "The domain model, port/adapter architecture, and slice history create a readable, maintainable codebase a new engineering team can onboard to")]),

    ("24", "Operations &amp; Team",
     "Can the business operate without the daily involvement of the owner?",
     [("p", "Partially yes &mdash; operationally. No &mdash; strategically."),
      ("p", "What runs without the owner:"),
      ("b", "All intelligence pipelines (market digest, regulatory harvest, trend detection, wiki compilation, article ingestion) run on automated schedules 24/7"),
      ("b", "The web application serves users, handles authentication, Stripe billing, and email delivery autonomously"),
      ("b", "EC2 and RDS run continuously without intervention"),
      ("p", "What requires human involvement:"),
      ("b", "Monitoring for pipeline failures or LLM API errors (currently owner-checked daily)"),
      ("b", "Social content posting (drafts are generated automatically; publishing is manual)")]),

    ("25", "Operations &amp; Team",
     "How many hours per week does the owner spend running the business?",
     [("p", "Roughly 20&ndash;30 hours/week, split approximately:"),
      ("p", "Development &amp; maintenance (~10&ndash;15 hrs) &mdash; active feature development, bug fixes, infrastructure updates"),
      ("p", "Content &amp; marketing (~5&ndash;8 hrs) &mdash; reviewing digest output, drafting/posting LinkedIn and Substack content, outreach"),
      ("p", "Business development (~3&ndash;5 hrs) &mdash; prospect research, direct outreach, acquisition conversations"),
      ("p", "Monitoring &amp; ops (~2&ndash;3 hrs)")]),

    ("26", "Operations &amp; Team",
     "Is the company currently involved in any legal proceedings?",
     [("p", "No.")]),

    ("27", "Product Technology &amp; Defensibility",
     "What technology stack is the product built on?",
     [("p", "Backend: Java 21, Spring Boot 3.4.5, Spring AI 1.1.6, Spring Security, Spring Data JPA"),
      ("p", "AI/LLM: Anthropic Claude, OpenAI GPT, Perplexity Sonar, Google Gemini &mdash; all via adapter pattern; swappable without domain changes"),
      ("p", "<b>Agent Integration (new):</b> a read-only MCP (Model Context Protocol) server exposes 6 tools &mdash; legislation, company, wiki, market digest, regulatory, and trend search &mdash; over SSE, so any MCP-compatible AI agent can query the platform directly"),
      ("p", "Data: PostgreSQL + pgvector (vector similarity search), Spring Data JPA (no Flyway &mdash; JPA auto-DDL)"),
      ("p", "Frontend: Thymeleaf server-rendered templates, Chart.js, Alpine.js, HTMX, Tailwind CSS"),
      ("p", "Infrastructure: AWS EC2 (app), AWS RDS (Postgres), AWS SES (email delivery), Stripe (billing)")]),

    ("28", "Product Technology &amp; Defensibility",
     "Who maintains and updates the software?",
     [("p", "Currently the owner only &mdash; a single senior Java/Spring engineer who designed and built the entire system."),
      ("p", "For an acquirer: the codebase is structured for maintainability by a small team:"),
      ("b", "Hexagonal architecture &mdash; clear boundaries between domain, application, infrastructure, and web layers; a new engineer can work in one layer without understanding all others"),
      ("b", "3,239 tests across 378 test classes &mdash; high coverage catches regressions; new engineers can refactor with confidence"),
      ("b", "Documented conventions &mdash; CLAUDE.md, architecture.md, and CONVENTIONS.md are checked into the repo and kept current, so a new engineer can onboard without relying on tribal knowledge")]),

    ("29", "Product Technology &amp; Defensibility",
     "What sets your company apart from competitors?",
     [("p", "<b>Depth over breadth, with full provenance.</b>"),
      ("p", "Most healthcare news aggregators (Definitive Healthcare, CB Insights, Fierce Healthcare) surface headlines. This platform goes further:"),
      ("b", "<b>LLM classification</b> &mdash; every news item is labeled CONFIRMED vs. SPECULATIVE with impact dimensions scored"),
      ("b", "<b>Cross-referenced intelligence</b> &mdash; a single company ties together sentiment scores, deal signals, regulatory events, framework analysis, and market digest entries in one view"),
      ("b", "<b>Provenance chain</b> &mdash; every claim traces to a source article with harvest date; wiki contradictions are tracked explicitly (&ldquo;this reverses the FDA's March guidance&rdquo;)"),
      ("b", "<b>Regulatory depth</b> &mdash; FDA 510(k)/De Novo + CMS rulemaking + 43-state health-AI legislation tracking"),
      ("b", "<b>Liability &amp; accountability tracking (new)</b> &mdash; a public AI Liability Incident Tracker logs lawsuits, FDA liability flags, and regulatory sanctions tied to AI healthcare tools, with LLM auto-classification from harvested articles routed through an admin review queue before anything publishes")]),

    ("30", "Product Technology &amp; Defensibility",
     "What barriers make it difficult for new competitors to enter your market?",
     [("p", "<b>1. Data corpus depth</b>"),
      ("p", "The article database, wiki, and regulatory event history compound daily. A new entrant starts with zero historical data &mdash; no trend baselines, no contradiction history, no 3-year backfill."),
      ("p", "<b>2. Pipeline complexity</b>"),
      ("p", "Integrating FDA/CMS APIs, Perplexity, Alpaca market data, pgvector embeddings, and multiple LLM providers into a coherent daily pipeline took significant engineering time. It's reproducible but not fast."),
      ("p", "<b>3. Domain specificity</b>"),
      ("p", "Healthcare AI regulation, deal classification, and competitive framework scoring require carefully tuned prompts and domain knowledge. Generic AI tools don't produce comparable results without that same depth of domain-specific prompt engineering and historical context."),
      ("p", "<b>4. Operating-cost discipline already engineered in (new)</b>"),
      ("p", "A November 2026 cost-reduction initiative &mdash; freshness-gated wiki/topic-summary compilation, consolidated AI post-processing, and a single-model default for AI Search &mdash; is estimated to cut per-pipeline LLM spend roughly in half without removing any feature. A new entrant would need to re-learn these efficiencies the expensive way before reaching comparable margins.")]),

    ("31", "Product Technology &amp; Defensibility",
     "What challenges currently exist that if solved would materially grow revenue or profitability?",
     [("p", "<b>1. Distribution &mdash; the only real blocker</b>"),
      ("p", "The product is built. Zero paying customers is a distribution problem, not a product problem. Solving the first 10&ndash;20 subscriber conversions validates the channel and unlocks compounding word-of-mouth in a tight professional community."),
      ("p", "<b>2. Enterprise sales motion</b>"),
      ("p", "The highest-value buyers (market research teams at large healthcare firms) require direct outreach and a demo-to-contract motion that hasn't been executed yet. One Enterprise contract could equal 40+ Subscriber accounts."),
      ("p", "<b>3. Pricing validation</b>"),
      ("p", "$19/month is likely significantly underpriced for the target buyer. Testing $39&ndash;$79/month, or a team/seat pricing model, could roughly double-to-quadruple ARPU with zero additional development.")]),

    ("32", "Brand, Reputation &amp; Credibility",
     "If someone looked your company up online today, what would they find in terms of reviews, ratings, and testimonials?",
     [("p", "Nothing &mdash; no reviews, ratings, or testimonials exist anywhere."),
      ("p", "What they would find:"),
      ("b", "bigskylabs.ai &mdash; live, functional product with public-facing pages (company directory with per-company Signal Intelligence, wiki, state legislation registry, AI liability incident registry, a 2-minute product tour video, and the pricing page)"),
      ("b", "LinkedIn profile &mdash; Bill Blackmon's profile with posts about the product and AI healthcare market updates"),
      ("b", "GitHub &mdash; public showcase repository showing the codebase (aihealthcare-showcase)"),
      ("b", "Substack &mdash; early newsletter series, small audience"),
      ("p", "No presence on&hellip;")]),

    ("33", "Brand, Reputation &amp; Credibility",
     "How would you describe your brand's perceived reputation today, and what factors have shaped it?",
     [("p", "The brand exists but hasn't been tested at scale. What's been shaped so far:"),
      ("p", "Positive factors:"),
      ("b", "The product is genuinely functional and deployed &mdash; not vaporware. Anyone who visits bigskylabs.ai encounters a working, content-rich intelligence platform"),
      ("b", "LinkedIn content posts have established a consistent voice: data-driven, sourced, analyst-oriented &mdash; not hype"),
      ("b", "The public wiki, company directory, legislation registry, and AI liability incident registry are indexed and demonstrate depth to anyone who finds them organically"),
      ("b", "The codebase quality and architecture (visible via GitHub showcase) signals engineering credibility to technical evaluators")]),
]


def build():
    doc = BaseDocTemplate(OUT_PATH, pagesize=landscape(letter),
                           leftMargin=0, rightMargin=0, topMargin=0, bottomMargin=0)

    title_frame = Frame(0.6 * inch, 0, PAGE_W - 1.2 * inch, PAGE_H, id="title")
    content_frame = Frame(0.55 * inch, 0.4 * inch, PAGE_W - 1.1 * inch, PAGE_H - 0.8 * inch, id="content")
    metrics_frame = Frame(0.55 * inch, 0.4 * inch, PAGE_W - 1.1 * inch, PAGE_H - 0.8 * inch, id="metrics")

    doc.addPageTemplates([
        PageTemplate(id="Title", frames=[title_frame], onPage=draw_title_background),
        PageTemplate(id="Content", frames=[content_frame], onPage=draw_content_background),
        PageTemplate(id="Metrics", frames=[metrics_frame], onPage=draw_metrics_background),
    ])

    story = []

    # Title page
    story.append(Spacer(1, 2.6 * inch))
    story.append(Paragraph("Confidential Information Memorandum (CIM)", styles["title"]))
    story.append(Spacer(1, 10))
    story.append(Paragraph("AI in Healthcare", styles["subtitle"]))
    story.append(Spacer(1, 18))
    story.append(Paragraph("v2 &mdash; Updated November 2026", styles["tag"]))
    story.append(NextPageTemplate("Content"))
    story.append(PageBreak())

    for num, category, question, answer in PAGES:
        story.extend(build_qa_flowables(num, category, question, answer))
        story.append(PageBreak())

    # Key Metrics page (unchanged from original)
    story.append(NextPageTemplate("Metrics"))
    metrics = [
        ("TTM Revenue", "$36"),
        ("TTM Profit", "$36"),
        ("Annual Recurring Revenue", "$0"),
        ("Annual Growth Rate", "0%"),
        ("Customers", "< 10"),
        ("Churn Rate", "0-1%"),
    ]
    story.append(Paragraph("34&nbsp;&nbsp;Key Metrics", styles["header_num"]))
    story.append(Spacer(1, 16))
    rows = []
    for i in range(0, len(metrics), 3):
        row = []
        for label, value in metrics[i:i + 3]:
            cell = [Paragraph(label, styles["metric_label"]), Spacer(1, 10),
                    Paragraph(value, styles["metric_value"])]
            row.append(cell)
        rows.append(row)
    t = Table(rows, colWidths=[2.9 * inch] * 3, rowHeights=[1.6 * inch] * len(rows),
              style=TableStyle([
                  ("BACKGROUND", (0, 0), (-1, -1), NAVY),
                  ("VALIGN", (0, 0), (-1, -1), TOP := "TOP"),
                  ("LEFTPADDING", (0, 0), (-1, -1), 18),
                  ("TOPPADDING", (0, 0), (-1, -1), 18),
                  ("BOX", (0, 0), (-1, -1), 6, GRAY_BG),
                  ("INNERGRID", (0, 0), (-1, -1), 6, GRAY_BG),
              ]))
    story.append(t)

    doc.build(story)
    print("WROTE", OUT_PATH)


if __name__ == "__main__":
    build()
