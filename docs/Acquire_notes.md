# Acquire.com Sale Notes — AI in Healthcare

> Created: 2026-10-28 | Author: Bill Blackmon

---

## Current Status

- Listed on Acquire.com, asking price TBD (currently set below true value — raise it)
- 4 NDAs signed as of 2026-10-28 (day 1)
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
