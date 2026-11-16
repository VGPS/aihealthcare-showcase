# BigSkyLabs SEO Fixes — Claude Code Handoff

**Site:** https://app.bigskylabs.ai (AIHealthcare platform)
**Audit date:** 2026-09-30
**Stack assumptions:** Spring Boot + Thymeleaf + HTMX + Tailwind, Spring Security, PostgreSQL, hexagonal architecture (domain / application / infrastructure / web), deployed as a .war behind nginx 1.30.3 on AWS.
**How to use this file:** Paste it into a fresh Claude Code session (Plan Mode recommended). Claude Code should read the whole file, locate the relevant controllers/templates/config, propose a plan, then implement the tasks in priority order. Each task has acceptance criteria and a `curl` check. Section 5 lists work that only a human can do (Search Console, links).

---

## 1. Current state (observed 2026-09-30)

| Area | Finding |
|---|---|
| Google index | `site:app.bigskylabs.ai` returns about 10 results: mostly `/wiki/*` pages, `/pricing`, and 2 `/directory/*` pages. |
| Not indexed | Zero `/legislation/*` pages, zero `/insights/*` pages, homepage not shown in `site:` results. |
| Sitemap | 1,890 URLs (1,137 wiki, 611 directory, 72 trends, 59 legislation, 6 insights, 5 top-level pages). Every `<lastmod>` is `2026-09-30` (today). `/trends` itself is in the sitemap but 302-redirects to `http://…/trends/2026-09-30`. |
| Duplicates in Google | "Knowledge Wiki" is indexed 4 times, including `/wiki?query=liability&pageType=&page=1`. |
| Brand query | Google "bigskylabs" returns Big Sky Labradors (bigskylabs.com), not this site. |
| robots.txt | Fine. Public sections allowed, private sections disallowed, sitemap declared. |

Indexing is at <1% of the sitemap. The code-side causes are below.

---

## 2. Root causes found in the HTML/HTTP responses

1. **Wiki pages show almost no content to anonymous visitors (and Googlebot).** Example `/wiki/ai-healthcare-roi`: about 1,600 chars of visible text total, mostly nav. Body is title + tags + "Rev 2 … 0 sources" + "🔒 Subscribe to read full wiki analysis". Google will class 1,137 of these as thin/duplicate and leave them "Crawled – currently not indexed". The meta description is also boilerplate: `"<title> — AI healthcare wiki page with source provenance and evidence grading."`
2. **Soft-404s via redirects instead of real 404/410.**
   - `/directory/nope-xyz` → `302 http://app.bigskylabs.ai/directory`
   - `/wiki/nope-xyz` → `302 …/wiki`
   - `/legislation/nope-xyz` → `302 …/legislation`
   - `/nonexistent-xyz` → `302 …/login`
   - Removed company `/directory/omniloy` (still in Google and Perplexity indexes) → `302 …/directory`
   - Only `/insights/nope.html` correctly returns 404.
3. **Redirect `Location` headers use `http://`.** Spring builds absolute URLs from the internal (non-TLS) request, so it isn't reading nginx's forwarded protocol. Every redirect costs an extra http→https hop.
4. **Canonical on paginated/filter lists points to page 1.** `/wiki?page=2` and `/directory?page=2` both declare canonical = `/wiki` / `/directory`. Filter/search variants (`?query=`, `?pageType=`) aren't `noindex`, so Google indexes them.
5. **Weak titles / H1s.**
   - Legislation detail: `<title>Legislation Detail — AIHealthcare</title>` and `<h1>Legislation Detail</h1>` on every bill page (e.g. `/legislation/az-hb-2175`). The bill name is only in an H2.
   - Wiki detail: first `<h1>` is the brand "AIHealthcare Analytics"; the page title is in a second heading.
   - Homepage: `AIHealthcare — AI Healthcare Intelligence Platform — AIHealthcare` (brand repeated). Same on og:title/twitter:title.
   - `/developer` uses the site-wide default description.
6. **No structured data (JSON-LD)** anywhere.
7. **Sitemap `lastmod` is always "today"** for all 1,890 URLs, so Google learns to ignore it.
8. **`/insights/` index returns 404**, but robots.txt says `Allow: /insights/` and the 6 insight articles have no hub page linking them.
9. **og:image** is `/images/brand-icon.png` declared as 1920×1080, and `twitter:card` is `summary`. No proper social card image.
10. **Wiki detail uses the logged-in app layout for anonymous users** (nav shows Dashboard, Deal Signals, Watchlist, etc.), unlike directory/legislation pages, which use the public layout.
11. **Root domain:** `https://bigskylabs.ai/robots.txt` and `/sitemap.xml` return nginx 404. `www.bigskylabs.ai` doesn't resolve.

---

## 3. Tasks for Claude Code (priority order)

### T1 — Real 404 / 410 for missing and removed detail pages  (P0)

- In the directory, wiki, and legislation detail controllers, replace "not found → redirect to index" with `throw new ResponseStatusException(HttpStatus.NOT_FOUND)` (or the project's existing not-found exception + `@ControllerAdvice`; match the 404 error shape already used by `MarketDigestController`).
- If a record exists but is soft-deleted/merged/removed (e.g. `omniloy`), return **410 Gone**. If it was merged into another slug, return **301** to the new slug instead.
- Add a public, lightweight `templates/error/404.html` (and `410.html`) using the **public** layout: links to Directory, Wiki, Legislation, and a search box. Status code must remain 404/410, not 200.
- Make sure `/error` is `permitAll()` in the Spring Security config so anonymous users get the 404 page, not a login redirect.
- Optional (lower priority): unknown top-level paths currently hit `anyRequest().authenticated()` and redirect to `/login`. Acceptable for now. If cheap, return 404 for paths with no matching handler before the auth entry point runs.
- Remove deleted/removed entities from the sitemap (see T6).

Acceptance:
```bash
B=https://app.bigskylabs.ai
for p in /directory/nope-xyz /wiki/nope-xyz /legislation/nope-xyz; do curl -s -o /dev/null -w "$p %{http_code}\n" $B$p; done   # expect 404
curl -s -o /dev/null -w "%{http_code}\n" $B/directory/omniloy   # expect 410 (or 301 to a live slug)
```

### T2 — Fix http:// in redirect Location headers  (P0)

- `application.properties`: `server.forward-headers-strategy=native` (or `framework`).
- nginx server block (confirm these exist):
  ```nginx
  proxy_set_header Host              $host;
  proxy_set_header X-Forwarded-Proto $scheme;
  proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Host  $host;
  ```
- If deployed to external Tomcat as a .war, also make sure Tomcat's `RemoteIpValve` is configured, or rely on `framework` strategy.

Acceptance:
```bash
curl -sI https://app.bigskylabs.ai/dashboard | grep -i '^location'   # must start with https://
```

### T3 — Canonicals and noindex for list/filter pages  (P0)

- Paginated lists: canonical must be **self-referencing including `?page=N`** (e.g. `/wiki?page=2` → canonical `https://app.bigskylabs.ai/wiki?page=2`). Page 1 canonical stays `/wiki`, with no `?page=1`.
- Any request with search/filter params (`query`, `pageType`, `state`, `category`, `status`, `sector`, etc., for wiki, directory, legislation) → `<meta name="robots" content="noindex,follow">`, canonical = the unfiltered list URL.
- Implement once: a small helper (e.g. `SeoMeta` model attribute or a `@ControllerAdvice` that sets `canonicalUrl` and `robotsDirective`) used by the shared `<head>` fragment. The head fragment already contains a "Robots — noindex for auth-required pages" block; extend it.
- Canonical URLs must always be absolute `https://app.bigskylabs.ai/...`, never built from the request scheme.

Acceptance:
```bash
B=https://app.bigskylabs.ai
curl -s "$B/wiki?page=2"                     | grep -E 'rel="canonical"'          # …/wiki?page=2
curl -s "$B/wiki?query=liability&page=1"     | grep -E 'name="robots"'            # noindex,follow
curl -s "$B/directory?page=2"                | grep -E 'rel="canonical"'          # …/directory?page=2
```

### T4 — Public preview content on wiki pages (fix thin content)  (P0, biggest indexing lever)

The paywall is fine. The problem is that anonymous visitors (and Google) currently get zero substance.
- For anonymous users, render server-side (not via HTMX) on each `/wiki/{slug}`:
  - The page summary / first 1–2 paragraphs of the overview (about 150–300 words).
  - Tags, last-revised date, and the **count and names of source publications** (titles/publishers only, no full provenance). Currently it shows "0 sources", which looks broken. Verify the anonymous query path loads the source count.
  - "Related pages" links (3–5 internal links to other wiki/directory/legislation pages). This spreads crawl across the 1,137 pages.
  - The existing subscribe CTA for the rest.
- Use a real per-page meta description: first ~155 chars of the summary, not the boilerplate string.
- Add paywall structured data so Google doesn't treat preview vs. subscriber content as cloaking:
  ```json
  {
    "@context": "https://schema.org",
    "@type": "Article",
    "headline": "<page title>",
    "dateModified": "<ISO revision date>",
    "publisher": {"@type": "Organization", "name": "BigSkyLabs", "url": "https://bigskylabs.ai"},
    "isAccessibleForFree": false,
    "hasPart": {"@type": "WebPageElement", "isAccessibleForFree": false, "cssSelector": ".wiki-paywalled"}
  }
  ```
  Wrap the subscriber-only section (or its placeholder) in an element with class `wiki-paywalled`.
- Switch anonymous wiki detail pages to the **public layout** (same header as directory/legislation) so anonymous users don't see app-only nav items.
- Fix heading order: exactly one `<h1>` = the wiki page title. Brand goes in a non-heading element.

Acceptance:
```bash
curl -s https://app.bigskylabs.ai/wiki/ai-healthcare-roi | python3 -c "import sys,re;h=sys.stdin.read();b=re.sub(r'(?s)<(script|style)[^>]*>.*?</\1>','',h);t=re.sub(r'\s+',' ',re.sub(r'<[^>]+>',' ',b));print(len(t));print(re.findall(r'<h1[^>]*>([^<]*)',h))"
# expect text length well above 2,500 chars and a single h1 equal to the page title
```

### T5 — Titles, H1s, meta descriptions  (P1)

- Title pattern (keep under ~60 chars where possible; brand suffix once):
  - Home: `AI Healthcare Intelligence Platform | BigSkyLabs`
  - Legislation detail: `{StateName} {BillNumber}: {title} | Health-AI Law Registry` (e.g. `Arizona HB 2175: Prior authorization; claims | Health-AI Law Registry`). Truncate long bill titles sensibly.
  - Legislation H1: `{StateName} {BillNumber} — {title}`. Delete the static "Legislation Detail" H1.
  - Directory detail: `{Company} — AI Healthcare Company Profile | BigSkyLabs`
  - Wiki detail: `{Wiki title} | AI Healthcare Wiki`
  - Insights: keep current pattern.
- Remove the duplicated "— AIHealthcare" suffix. The layout appends a suffix, and some controllers pass a title that already contains it. Pick one place to append.
- `/developer`: write a specific description.
- Directory description is thin (`"Axle Health — AI healthcare company profile: AI Agents & Virtual Care."`). Use the first ~155 chars of the company's description paragraph.
- Legislation description is decent. Add status and effective date: `Arizona HB 2175 (enacted 2025, effective Jul 1, 2026): prior authorization and claims rules for AI in utilization review.`
- Make og:title / twitter:title follow the same values.

Acceptance: `curl` the home page, one legislation page, one directory page, and one wiki page, then check that the `<title>` is unique and descriptive and there's exactly one `<h1>`.

### T6 — Sitemap: truthful lastmod, split into index, only live URLs  (P1)

- Serve `/sitemap.xml` as a **sitemap index** pointing to:
  `/sitemap-pages.xml`, `/sitemap-wiki.xml`, `/sitemap-directory.xml`, `/sitemap-legislation.xml`, `/sitemap-insights.xml`.
  This lets Search Console report indexing per section.
- `<lastmod>` = the entity's real `updated_at` (wiki revision date, company updated date, law `updatedAt`/dataset version date, insight publish date). Top-level pages: the last deploy or content change date. Never "now" for everything.
- Drop `<changefreq>` and `<priority>` (Google ignores them). Optional.
- Include only URLs that return 200 and are indexable: no removed/merged entities, no filter URLs, no redirecting URLs. Remove `/trends` (302) and list the `/trends/{date}` pages in a `sitemap-trends.xml`. Also add `Allow: /trends/` to robots.txt for consistency with the other public sections.
- Cache the generated sitemap (e.g. `@Cacheable`, refreshed hourly) since it has about 1,900 rows.

Acceptance:
```bash
curl -s https://app.bigskylabs.ai/sitemap.xml | head -20                          # <sitemapindex>
curl -s https://app.bigskylabs.ai/sitemap-wiki.xml | grep -o '<lastmod>[^<]*' | sort | uniq -c | head   # many distinct dates
```

### T7 — Structured data (JSON-LD)  (P1)

Add one JSON-LD `<script type="application/ld+json">` block per page type, rendered from the model via the head fragment:
- Home: `Organization` (name BigSkyLabs, url https://bigskylabs.ai, logo, `sameAs`: LinkedIn company/profile URLs) + `WebSite` with `SearchAction` pointing at `/wiki?query={search_term_string}`.
- Directory detail: `Organization` for the company (name, url if known, description) + `BreadcrumbList`.
- Legislation detail: `Legislation` (schema.org type: `name`, `legislationIdentifier` = bill number, `legislationJurisdiction` = state, `legislationDate`, `legislationLegalForce`, `url` to official source) + `BreadcrumbList`.
- Wiki / Insights: `Article` (see T4) + `BreadcrumbList`.

Acceptance: validate 1 URL of each type at https://validator.schema.org/ (human step) and check that the page renders with no JS errors.

### T8 — Insights hub page  (P1)

- Create `GET /insights/` (and `/insights` → 301 to `/insights/`, or vice versa, pick one) that lists all insight articles with title, date, and 1–2 line summary. Use the public layout.
- Link it from the public header/footer and the homepage.
- Add it to `sitemap-pages.xml`.

Acceptance: `curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/insights/` → 200.

### T9 — Internal linking between sections  (P2)

Google finds deep pages mainly by following links, and currently there's only the sitemap.
- Legislation detail → "Companies affected / related" (directory) and "Related wiki pages" (match on category/tags).
- Directory detail → "Related wiki pages" and "Relevant state laws" (by sector/category).
- Homepage → "Latest wiki pages", "Recently added companies", "Newest laws", "Latest insights" (5 each, server-rendered).
- Legislation index → link per-state anchors or state pages (optional future: `/legislation/state/{code}`, a good landing page for "Texas AI healthcare law" searches).

### T10 — Social card and minor head fixes  (P2)

- Create a 1200×630 `/images/og-default.png` social card (brand name + tagline). Set correct `og:image:width/height`, and switch `twitter:card` to `summary_large_image`.
- Remove the unused/incorrect 1920×1080 declaration on `brand-icon.png`.
- Consider per-type OG images later.

### T11 — Root domain hygiene  (P2, infra)

- `bigskylabs.ai`: add `robots.txt` (`User-agent: *` / `Allow: /` / `Sitemap: https://app.bigskylabs.ai/sitemap.xml`).
- Add a `www.bigskylabs.ai` DNS record and 301 it to `https://bigskylabs.ai/`.
- Make sure the root landing page links to key public app pages: Directory, Legislation Registry, Wiki, Insights. It currently links only About/Directory/Pricing/Login/Register.
- Give the root landing page a canonical tag and meta description.

### T12 — Optional: IndexNow for Bing/Yandex  (P3)

- Generate an IndexNow key, serve it at `/{key}.txt`, and POST changed URLs to `https://api.indexnow.org/indexnow` when wiki/directory/legislation records are created or updated (async, batched). Google doesn't use IndexNow, but Bing does, and Bing also feeds several AI search tools.

---

## 4. Regression check script

Add as `scripts/seo-check.sh` and run after each deploy:

```bash
#!/usr/bin/env bash
set -u
B=${1:-https://app.bigskylabs.ai}
pass=0; fail=0
check(){ if eval "$2"; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1"; fail=$((fail+1)); fi; }
code(){ curl -s -o /dev/null -w "%{http_code}" "$B$1"; }

check "404 directory"   '[ "$(code /directory/nope-xyz)" = 404 ]'
check "404 wiki"        '[ "$(code /wiki/nope-xyz)" = 404 ]'
check "404 legislation" '[ "$(code /legislation/nope-xyz)" = 404 ]'
check "https redirects" 'curl -sI $B/dashboard | grep -i "^location" | grep -q "https://"'
check "page2 canonical" 'curl -s "$B/wiki?page=2" | grep -q "rel=\"canonical\" href=\"$B/wiki?page=2\""'
check "filter noindex"  'curl -s "$B/wiki?query=test" | grep -qi "name=\"robots\" content=\"noindex"'
check "insights hub"    '[ "$(code /insights/)" = 200 ]'
check "sitemap index"   'curl -s $B/sitemap.xml | grep -q "<sitemapindex"'
check "legis title"     '! curl -s $B/legislation/az-hb-2175 | grep -q "<title>Legislation Detail"'
check "home title"      '! curl -s $B/ | grep -q "AIHealthcare — AI Healthcare Intelligence Platform — AIHealthcare"'
check "json-ld home"    'curl -s $B/ | grep -q "application/ld+json"'
check "wiki not thin"   '[ $(curl -s $B/wiki/ai-healthcare-roi | sed -e "s/<[^>]*>//g" | tr -s " \n" | wc -c) -gt 2500 ]'
echo "pass=$pass fail=$fail"; [ $fail -eq 0 ]
```

Also add unit/MockMvc tests for: 404 on unknown slug for each detail controller, canonical/robots model attributes for paginated and filtered list requests, and sitemap lastmod coming from entity timestamps.

---

## 5. Human-only tasks (Bill, not Claude Code)

These need a Google login or DNS access, so they can't be done from code.

### 5a. Google Search Console setup (15 min)
1. Go to https://search.google.com/search-console, sign in with the Google account that owns GA4 property `G-ZDBXNCKJ54`.
2. Add property:
   - Best: **Domain property** `bigskylabs.ai` (covers app., www., root). Verify with the DNS TXT record Google gives you at your DNS host (Route 53 or registrar).
   - Fastest: **URL-prefix property** `https://app.bigskylabs.ai/`, verify via "Google Analytics" (works because GA4 is already on the page).
3. Sitemaps → submit `https://app.bigskylabs.ai/sitemap.xml` (after T6, the index file; Search Console will pick up the children).
4. URL Inspection → "Request indexing" for these (quota is roughly 10/day):
   `/`, `/legislation`, `/directory`, `/wiki`, `/insights/` (after T8), and the 6 `/insights/*.html` articles, then 3–5 of the strongest legislation pages (e.g. Texas, California, Colorado laws).
5. Check back in 5–7 days: Pages report → look at "Crawled – currently not indexed" and "Duplicate without user-selected canonical" counts per sitemap section.
6. Also add the site to **Bing Webmaster Tools** (https://www.bing.com/webmasters) using "Import from Google Search Console". It takes one click after GSC is verified.

### 5b. Links and brand signals (ongoing)
- LinkedIn personal profile: set the Website field to https://app.bigskylabs.ai and mention "BigSkyLabs AI Healthcare Intelligence" in the headline/About section.
- Create or complete a LinkedIn **Company Page** for BigSkyLabs with the site URL.
- One LinkedIn post per insight article linking directly to the `/insights/...` URL. The legislation registry ("58 AI-in-healthcare laws across 27 states, sourced") is the most linkable asset. Share it with health-policy and health-IT communities.
- Profiles that link back: Crunchbase, Product Hunt (launch when ready), GitHub profile README, and any newsletter/Substack you publish.
- To win the brand query against bigskylabs.com (the Labrador breeder), use "BigSkyLabs AI" or "BigSkyLabs Healthcare AI" consistently in titles, the Organization JSON-LD, and social profiles.

---

## 6. Suggested Claude Code session plan

1. Session A (P0): T2 → T1 → T3. Small, mostly config, controller, and head-fragment changes. Deploy, run `seo-check.sh`.
2. Session B (P0/P1): T4 + T5 (templates and controller model attributes). Deploy.
3. Session C (P1): T6 + T7 + T8. Deploy, then Bill resubmits the sitemap in Search Console.
4. Session D (P2/P3): T9 → T10 → T11 → T12.

Ready-to-paste kickoff prompt:

```
Read docs/seo/BigSkyLabs-SEO-Fixes-for-Claude-Code.md fully. This project uses
hexagonal architecture (domain/application/infrastructure/web) with Spring Boot,
Thymeleaf + HTMX, Spring Security, PostgreSQL. Start with Session A (T2, T1, T3).
First locate: the shared <head> Thymeleaf fragment (it contains the comments
"SEO meta description — controllers set pageDescription" and "Robots — noindex
for auth-required pages"), the Spring Security config, the sitemap controller,
and the directory/wiki/legislation detail + list controllers. Propose a plan
listing the files you'll change, then implement with MockMvc tests for each
acceptance criterion. Add scripts/seo-check.sh from Section 4. Don't change
robots.txt Disallow rules or any auth-protected behavior.
```
