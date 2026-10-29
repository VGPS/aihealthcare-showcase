# Acquire.com Sale Notes — AI in Healthcare

> Created: 2026-10-28 | Author: Bill Blackmon

---

## Current Status

- Listed on Acquire.com; raised asking price $12,500 → $25,000 on 2026-10-28
- 7 NDAs signed as of 2026-10-28 (up from 4 earlier same day) — strong demand signal
- Raising the price triggered a listing refresh and a visible jump in notice/views — not intentional, but a known marketplace mechanic (see Negotiating Notes)
- One NDA signer has requested a live walkthrough — call will be on Zoom
- Strategy: hold until end of year (Q4 budget cycle buyers, more competing inquiries)

---

## What the NDA Means

The platform auto-executes a mutual NDA as a friction gate. Someone who clicks through is signaling genuine intent, not just window-shopping. It gives them legal standing to receive financials, user counts, revenue data, and tech stack details. Mutual protection: they can't take architecture/customer list to a competitor; you can't disclose they're evaluating acquisitions.

---

## Buyer Types

### Funded Micro-PE
- Micro private equity operating at sub-$1M deal sizes that big PE firms ignore
- Raised a fund from outside investors (LPs) specifically to buy small software businesses
- Deploying someone else's capital — has a real deadline to deploy it
- **Pros:** Capital ready, experienced deal process, will close fast and cleanly
- **Cons:** Will lowball on price, likely to flip or cut costs, may use offshore dev team
- Least likely to want you long-term post-sale

### Solo Indie Acquirer (unfunded)
- Using personal savings or a loan
- Most likely to want you involved (30-90 day transition, ongoing retainer)
- More passionate about the product — better stewardship
- **Risk:** May back out if due diligence reveals anything unexpected, deal can drag

### Strategic Buyer (company in healthcare AI space)
- Pays the most — buying the asset AND eliminating a competitor or filling a product gap
- Slowest process: committees, legal teams, procurement
- Most likely to use the app as intended rather than flip it
- Could offer a full-time role or nothing depending on their intent

---

## Consulting / Staying On

Frequency: more often than you'd expect, especially solo acquirers buying a complex technical asset.

Typical arrangements:
- 30-90 day transition/handoff period (paid flat fee)
- Ongoing consulting retainer ($X/hour, capped monthly)
- "Key man" arrangement — stay on as sole developer 6-12 months

**For this app specifically:** Spring Boot hexagonal architecture, 3,200+ tests, pgvector, Spring AI, 15+ scheduled pipelines is not something a non-Java developer picks up in a week. Demand for transition support will be high.

**A retainer at $150-200/hour for 10 hrs/month = $18-24K/year** with zero commute, zero politics, work you know cold. Negotiate it as a separate contract — don't let them use it to reduce the purchase price.

**Watch for:** "we want you to stay on" + lower purchase price offer. That's a tactic. Sale price and consulting are separate contracts.

---

## Negotiating Notes

**Do NOT add "for sale on Acquire.com" to the app footer.** Signals distress to buyers, damages subscriber trust, flips negotiating leverage against you.

**Raise the asking price now.** 4 NDAs on day 1 = demand signal. List at true value — you can negotiate down, never up from an anchored number.

**Replacement cost framing:** no revenue argument → counter with cost to build (18+ months senior engineering, 3,100+ articles, vector store, 3,000+ tests, live infrastructure on EC2/RDS).

**Honest answer to "why are you selling?"** = retirement / pursuing other interests. Clean, credible, doesn't signal distress.

**Low-ballers:** expect 40-50% of ask, citing "no revenue." Standard. Hold firm.

**Year-end timing advantage:**
1. Companies with unspent capital need to deploy before Dec 31
2. More time = more competing inquiries = your only real leverage on price

**Listing-edit visibility bump:** editing a listing (price change, description update) commonly
re-triggers "recently updated" sorting and saved-search/watcher notifications on marketplace
platforms generally — this isn't a special trick, just how listing sites keep content fresh in
feeds. Confirmed on Acquire 2026-10-28 (price bump $12,500 → $25,000 → refresh → more notice).
Worth knowing as a lever: a deliberate, well-timed edit (price, a new metric, a milestone) can be
used intentionally later to re-surface the listing when inquiry volume slows down.

---

## Deal Process (Once Serious Buyer Emerges)

1. Share data room (Acquire has built-in from intake forms)
2. Serious buyers make written offer via Acquire deal flow tool
3. Counter-offers until agreement
4. LOI — locks in price + exclusivity (30-60 day due diligence window)
5. Escrow.com holds funds during due diligence
6. Hand over credentials/repo/AWS access **only after funds clear escrow**

**Never hand over:** source code, AWS credentials, database until funds are in escrow.

---

## LinkedIn Research Checklist (for each NDA signer)

- Current role: operator vs. investor vs. aggregator
- Prior acquisitions: search activity/posts for "acquired" or "excited to announce"
- Company size and backing: funded micro-PE vs. solo indie hacker
- Healthcare background: domain-knowledgeable buyer pays more
- Watch for: competitors doing competitive intelligence under NDA cover

---

## Competitive Intelligence Risk

The NDA protects you legally but doesn't stop behavior. Don't share anything in early conversations you wouldn't want a competitor to know. Treat early-stage conversations as public.

---

## Walkthrough Guidance (live screen-share calls)

Vet the person first using the LinkedIn Research Checklist above before scheduling. Then, on the call:

1. **Use Zoom** for the call and screen-share demo — free tier has no time cap for 1:1 calls and supports local recording (see point 6). Use a seeded demo/subscriber login for the app itself, never your real ADMIN credentials.
2. **Never show:** AWS console, EC2 terminal, `.env`, repo/IDE, database console. That's post-LOI/escrow territory, not walkthrough territory.
3. **Script it** — walk through the app as a user/admin would see it (dashboards, newsletter, AI search, deal signals). Keep control of the screen so nothing unintended is exposed.
4. **No source code or architecture deep-dive** — "Spring Boot, hexagonal architecture, 3,200+ tests, pgvector" is as far as it goes verbally.
5. **No roadmap/unreleased features** — don't hand a possible competitor your next six months of ideas.
6. **Record the call yourself** (with notice) for your own reference.
7. **Financials are fair game under the NDA** — that's what it's for — but keep it aggregate, no customer PII.
8. Keep it to 30-45 minutes with a plan going in.

## Interim Self-Serve Options (when unavailable for a live walkthrough)

When a live call can't happen for a few days, it's fine — and arguably lower-risk than a live screen-share — to point a serious, NDA'd buyer at the existing public self-serve assets instead of leaving them waiting:

- **7-Day Free Trial** (`/register`) — self-service DEMO-tier signup. No credentials change hands; DEMO tier has no ADMIN/pipeline-trigger access (those stay URL-gated to ADMIN in `SecurityConfig`), so there's nothing for them to see beyond the intended product surface.
- **Product Tour** (`/tour`) — pre-recorded video captured from a SUBSCRIBER-tier account (not ADMIN), already public-facing. Zero incremental exposure.
- **Showcase repo** (`aihealthcare-showcase` on GitHub) — already public (used for recruiting/portfolio purposes too), so sending the link isn't a new disclosure. It's a maintained mirror of the private repo with sale/outreach-strategy docs stripped — see `feedback_showcase_mirror` memory for the exclusion list. **Spot-check before sending** that the exclusion list is still being honored and that `data.sql`/credentials remain absent (flagged clean as of 2026-09-02, re-verify if it's been a while).

Caveats:
- Treat this as a bridge, not a substitute — confirm a specific date for the live call so momentum doesn't die.
- The showcase repo isn't "special NDA access" — it's the same thing anyone could find via GitHub search. Don't let it stand in for the financials/traction conversation the code alone can't show.

---

## NDA Auto-Reply Message (revised 2026-10-29)

**Send this immediately after accepting an NDA on Acquire.com:**

> Hi [Name],
>
> Thanks for signing — you now have full context to explore the platform on your own terms.
>
> A few ways to get started right now:
>
> - **2-minute product tour** (every dashboard, captured from a subscriber account): https://app.bigskylabs.ai/tour
> - **Live app** — the company directory and wiki are public, no login needed: https://bigskylabs.ai
> - **7-day free trial** (no card required) — full access to market digest, deal signals, regulatory alerts, and sentiment scoring: https://app.bigskylabs.ai/register
>
> The platform is built on Spring Boot / Java 21, pgvector for semantic search, and six LLM providers. There are several active inquiries at the moment.
>
> Happy to do a live 30-minute walkthrough — just reply here and we'll find a time.
>
> Bill

**Why this works better than the original:**
- Leads with self-serve — buyer can assess immediately without waiting for you
- Product tour video gives them 2 minutes of "show don't tell" before any conversation
- Single CTA (reply to schedule a call) vs. open-ended question that stalls
- "Several active inquiries" is factual social proof, not pressure — it signals demand without desperation
- Tech stack detail stays brief and accessible to non-engineers

**Follow-up protocol:**
- If no response after 5–7 days: send one manual follow-up ("Just wanted to make sure you saw my note — happy to schedule a walkthrough when timing works for you.")
- After that: assume not interested. Don't chase further.
- The buyer who ghosts after signing an NDA is typically a competitor doing intelligence — not a real buyer. Move on.
