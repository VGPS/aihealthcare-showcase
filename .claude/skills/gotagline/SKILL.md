# Brand Tagline: "AI did what to whom. When, where, and why."

Apply or update the BigSkyLabs brand tagline across the app and emails. The tagline
is: **"AI did what to whom. When, where, and why."**

## Where the tagline lives

The tagline appears in exactly four places. When updating, change all four:

### 1. Market Digest page header (`market-digest.html`)
In the `<header>` block, as an italic `<em>` line replacing the old subtitle:
```html
<p class="text-primary-200 text-sm mt-0.5">
    <em>AI did what to whom. When, where, and why.</em>
    <span th:if="${digestDate}" th:text="' · ' + ${digestDate}"></span>
</p>
```

### 2. Footer fragment (`fragments/footer.html`)
Below the "AIHealthcare" brand name, above the description paragraph:
```html
<p class="text-gray-500 italic text-xs mb-1">AI did what to whom. When, where, and why.</p>
```
This makes it visible on every page.

### 3. Daily digest email (`DigestNewsletterRenderer.java`)
In `wrapInEmailLayout()`, as a styled `<p>` between the subject `<h1>` and the
description paragraph:
```java
sb.append("  <p style=\"margin:8px 0 0; font-size:0.8em; color:#94a3b8; font-style:italic;\">AI did what to whom. When, where, and why.</p>\n");
```

### 4. Market alert email (`SesMarketDigestNotifier.java`)
In `buildHtml()`, between the "AI Healthcare Market Alert" `<h2>` and the date `<p>`:
```java
sb.append("<p style=\"color:#94a3b8; font-style:italic; font-size:0.9em; margin:0 0 8px;\">AI did what to whom. When, where, and why.</p>");
```

## Design principles (do NOT violate)

- The tagline appears **where the content delivers on the promise** — digest page and emails.
- The footer placement is the only "every page" instance, and it's understated (italic, xs, gray).
- Do NOT put the tagline in the main nav bar or page headers (except Market Digest).
- Do NOT put it on pages where it has no contextual relevance (login, pricing, profile).
- The tagline is also the **LinkedIn series branding** — every weekly LinkedIn post uses
  "AI Did What to Whom, When, Where, and Why" as the recurring franchise title.
- Email subject lines can use the Five Ws pattern: "[Company] did [what] to [whom] — and N more moves this week"

## LinkedIn infographic integration

When generating weekly LinkedIn posts (via `/goDailyCheckup`), the Five Ws framework
is the organizing structure:

- Infographic title: "AI Did What to Whom, When, Where, and Why"
- Grid columns: Who / Did What to Whom / Why It Matters / Deal Size / The Signal
- Lead with the most contrarian angle (cognitive dissonance > data dump)
- Support with 4-5 events from Market Digest + Legislation Registry + Deal Signals
- Hero stats in upper right (dollar amount, law count, FDA clearances)
- Red callout card for "The Contrarian Signal"
- Quote card with the thesis statement

Reference infographic: `linkedin-posts/2026-09-14-five-ws-healthcare-ai.html`

## Testing after changes

Run selective tests for the two Java files:
```bash
mvn test -Dtest="DigestNewsletterRendererTest,SesMarketDigestNotifierTest"
```
Templates (HTML) have no direct test counterparts — verify visually after `/golocal`.

## Files involved

| File | Location |
|------|----------|
| Market Digest template | `application/src/main/resources/templates/market-digest.html` |
| Footer fragment | `application/src/main/resources/templates/fragments/footer.html` |
| Digest email renderer | `application/src/main/java/.../domain/service/DigestNewsletterRenderer.java` |
| Market alert notifier | `application/src/main/java/.../infrastructure/marketanalysis/notification/SesMarketDigestNotifier.java` |
| Reference infographic | `linkedin-posts/2026-09-14-five-ws-healthcare-ai.html` |
