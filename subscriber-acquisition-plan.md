# Getting Your First Subscribers — A Step-by-Step Plan

*For an AI healthcare **research / data / education** app. Solo founder, new to marketing, a few hundred dollars/month to spend. Tiers: Free, Demo (all features free for one week), Subscriber ($19/mo).*

---

## The one thing to fix before anything else

Your instinct was to "harvest" employees from your company database via LinkedIn. Don't — and not just for etiquette reasons. LinkedIn's own rules now explicitly forbid using member data to identify sales/marketing prospects, create leads, or enrich a CRM, and scraping gets accounts banned. Cold-emailing harvested contacts also exposes you to CAN-SPAM (US) and GDPR (EU) liability, which is a bad place for a one-person company to be.

Here's the reframe that keeps everything you wanted: **your database of companies is a targeting asset, not a contact list.** You don't collect the people — you let ad platforms put you in front of them. Upload your company names to LinkedIn's *company-list targeting*, add a job-title filter, and LinkedIn shows your ad to employees at exactly those companies. Fully compliant, and it's Step 3 below.

---

## How the whole thing fits together

Think of it as a funnel. Your job in marketing is to feed the top and reduce leaks at each stage:

**Strangers → Visitors → Sign-ups (Free/Demo) → Activated users → Paying subscribers → Fans who refer others**

Everything in this plan maps to one of those stages. You don't need to be good at "marketing" in the abstract — you need to move people through those six steps.

---

## Step 1 — Pick one beachhead audience (don't boil the ocean)

You said you're not sure whether individuals or organizations are the customer. Here's the call I'd make for you: **start with individuals, self-serve.** A $19/month price with a self-signup demo is built for individuals; selling to a hospital or company is a months-long process that $19/month can't fund. Let team/organization interest emerge *bottom-up* — when three people at the same company subscribe, that's your signal to have an org conversation later.

Since the product is research/data/education (not live patient care), your realistic individual buyers are people like: academic and clinical **researchers**, **grad students / postdocs** in biomed and public health, **medical librarians**, **healthcare data analysts**, **med educators and students**, and **knowledge workers at biotech/pharma**.

**Your task:** pick *one* of those as your beachhead for the first 90 days — the one where your app's "aha" is most obvious. Write a single sentence: *"[App] helps [this specific person] do [specific job] faster/better."* If you can't say it in one sentence, you can't market it yet. This sentence becomes your homepage headline, your ad copy, and your elevator pitch.

Use your Postgres DB here too: run a quick query to see which sub-sector (e.g. digital health startups vs. research hospitals vs. pharma) has the most companies. Density = where demand likely clusters = a good place to aim.

---

## Step 2 — Make your three tiers work as a funnel

Right now you have two "free" things (a Free tier *and* a one-week Demo), which can confuse people. Give each a distinct job:

- **Free (forever, limited):** the top-of-funnel magnet. Its purpose is signups and word of mouth, not revenue. Keep enough value to be genuinely useful, but hold back the features that make someone say "I need the paid version."
- **Demo (one week, everything):** the conversion accelerator. Its purpose is to let someone feel the full product, then feel its absence. The week must include a scheduled "aha moment" — the first time they get real value — as early as possible (ideally within 10 minutes of signup). Near the end, remind them exactly what they'll lose.
- **Subscriber ($19/mo):** the destination.

**Two practical decisions:**

1. **No credit card required for the Demo.** For a beginner with a small budget, maximizing top-of-funnel volume matters more than trial-to-paid conversion rate. You'll convert fewer per trial, but you'll get far more trials and email addresses to nurture.
2. **Add an annual option** ($228/year = two months free). It improves cash flow and locks in users past the risky early-churn window. This is a small change with an outsized effect on the health of the business.

**Reality check on the math:** at $19/month, self-serve free-to-paid conversion for SaaS typically runs ~2–5%. So to get 50 paying subscribers you may need ~1,000–2,500 sign-ups. That's why the plan leans heavily on *volume* channels (content, community) rather than slow one-to-one outreach. The upside: if a subscriber stays ~18 months on average, each is worth ~$700 to you — which means you can comfortably spend $30–80 to acquire one and still win.

---

## Step 3 — Turn your company database into a targeting machine

This is the compliant, high-leverage replacement for "harvesting." Three uses, in order of value:

1. **LinkedIn company-list targeting (your best paid channel).** Export company names from Postgres to a CSV and upload it in LinkedIn Campaign Manager as a *company list*. Requirements: at least 300 companies must match (match rates for clean company lists run 90–98%, so upload comfortably more than 300). Then layer a **job-title / job-function filter** so your ad only reaches, say, "research" or "data" roles at those companies. You've now recreated your "harvest" goal — reaching employees at your target companies — entirely within the rules. Start with a small test budget (see Step 6).
2. **Content and message targeting.** Knowing your companies tells you the language, problems, and journals/tools your audience cares about. Write for them specifically (Step 4).
3. **Warm-intro cross-reference.** Look through your *own* existing LinkedIn connections for anyone already at these companies. Those are people you can legitimately message one-to-one, because there's a real relationship.

What *not* to do: don't scrape profiles, don't buy email lists, don't cold-email people you have no relationship with at scale.

---

## Step 4 — Choose your channels (ranked for your situation)

Do these roughly in order. Don't start all at once — a beginner spread across six channels does all of them badly.

**1. Educational content + SEO (your #1 long-term engine).** A research/education product is a perfect match for content marketing: the same explaining you'd do in a sales pitch *is* the content. Write practical articles answering the exact questions your beachhead audience googles ("how to [do the thing your app helps with]"). Each piece ends with a soft nudge to try the free tier. This compounds — it keeps pulling in signups months after you publish, at no per-visitor cost. Aim for one solid piece per week.

**2. Communities where your audience already gathers.** Find the 3–4 places your beachhead lives — subreddits, specialized Slack/Discord groups, academic forums, professional association mailing lists, "academic X/Twitter." Spend two weeks purely *helping* (answering questions, no pitching) to earn standing, then share your content and product where genuinely relevant. This is free and, for a niche B2B-ish audience, often the highest-converting channel.

**3. Your own LinkedIn presence (organic, not ads).** Post from your personal profile 2–3x/week: what you're learning building the product, useful findings for your audience, small wins. This builds credibility and warms up the exact people your ads (channel 4) will also touch. It costs only time.

**4. LinkedIn ads via the company list (Step 3).** Once 1–3 are producing, add paid to accelerate. Start with a single, tightly targeted campaign and a small daily cap. Treat the first month as *learning*, not scaling.

**5. Partnerships & newsletters.** Find newsletters, YouTubers, podcasters, or association bulletins your audience already reads, and offer a guest article, a webinar, or a modest sponsorship. One placement in the right niche newsletter can beat a month of ads.

**6. A referral nudge.** Once you have even 20 happy subscribers, ask them to refer a colleague (offer both sides a free month). Word of mouth in a tight professional niche travels fast.

---

## Step 5 — Pricing (you chose $19 flat — here's my read)

Keeping $19 flat is the right call for now, and I'd *not* do "name your price" yet. Name-your-price tends to anchor people low, complicates your messaging, and is hard to reason about before you even know what value customers place on the product. It's a tactic to revisit later (e.g. a limited launch promo), not a foundation.

Two additions worth making around the flat price:

- **Annual plan** ($228/yr), as in Step 2.
- **An academic / student discount** (say 40–50% off) given your research/education audience. This isn't just goodwill — it's a marketing hook that gets you into universities, unlocks student communities, and seeds future full-price users. Announce it as a program, not a coupon.

Leave the actual $19 number alone until you have ~50+ paying users; you won't have reliable pricing signal before then.

---

## Step 6 — Tools and what to measure (kept minimal)

**A starter toolkit within a few hundred a month:**

- Landing page / site: your existing app site, or Framer/Carrd if you need something fast.
- Email (for nurturing signups): MailerLite or ConvertKit — free tiers cover your early volume.
- Analytics: Plausible or GA4 (free) to see visitors → signups.
- Lightweight CRM / pipeline: a Notion or Airtable board, or HubSpot's free tier.
- Social scheduling: Buffer's free tier.
- Ads: put the *bulk* of your monthly budget into **one** LinkedIn company-list test (~$150–250/mo to start). LinkedIn is pricey per click, so keep the audience tight and expectations modest; it's a test, not a growth engine yet.

**Only five numbers to watch (don't over-instrument):**

1. Visitors per week
2. Sign-ups per week (Free + Demo)
3. Activation rate — % of signups who reach the "aha moment"
4. Demo → paid conversion %
5. Monthly recurring revenue (MRR) and churn %

If activation is low, your product onboarding is the problem, not your marketing — fix that before spending more on ads.

---

## Step 7 — A 90-day sequence so you're never guessing

**Weeks 1–2 — Foundation.** Write your one-sentence positioning (Step 1). Query the DB for your densest sub-sector. Give each tier its distinct job and add the no-card demo + annual option (Step 2). Set up analytics and an email tool. Write down your five metrics and today's baseline.

**Weeks 3–6 — Organic engine.** Publish one educational article per week (4 total). Join your 3–4 communities and spend the first two weeks purely helping, then start sharing. Begin posting on your personal LinkedIn 2–3x/week. Create one "lead magnet" (a free guide/checklist your audience wants) to capture emails. Goal: first trickle of signups and an email list forming.

**Weeks 7–10 — Add paid + partnerships.** Export your company CSV and launch one tightly targeted LinkedIn company-list campaign with a small daily budget (Step 3). Simultaneously pitch 5–10 niche newsletters/communities for a guest post, webinar, or small sponsorship. Keep the content cadence going. Watch cost-per-signup on the ad — if it's wildly above ~$30–80, pause and rethink the targeting or offer, don't just spend more.

**Weeks 11–12 — Read the data and concentrate.** Look at your five metrics. Cut the one or two channels doing nothing. Pour time/budget into the one or two that are working. Launch a simple referral offer to your happy users. Decide the academic-discount rollout.

By day 90 you won't have a finished growth machine, but you'll know *which* channel is yours to scale — which is the real goal of the first quarter.

---

## Compliance guardrails (read once, then relax)

- **Don't scrape LinkedIn or buy contact lists.** Use company-list *ad targeting* instead.
- **Any email you send** must identify who you are, include a real unsubscribe link, and honor it (CAN-SPAM). Sending to people who *opted in* (signed up, downloaded your lead magnet) is clean; cold-emailing strangers is where trouble lives.
- **If you knowingly market to people in the EU/UK**, you generally need a lawful basis and consent for outreach (GDPR) — another reason to prefer inbound and opt-in over cold contact.
- None of this constrains the plan above, because the plan is built on people coming to *you*.

---

## Do these three things this week

1. Write your one-sentence positioning and paste it at the top of your homepage.
2. Run the DB query to find your densest company sub-sector — that's your first target.
3. Publish (or outline) your first educational article and pick the 3–4 communities you'll join.

Everything else follows from those.

*Note: this is general marketing guidance, not legal advice. For the compliance specifics (CAN-SPAM, GDPR, healthcare data rules that may touch your app), confirm with a qualified attorney before you scale outreach.*
