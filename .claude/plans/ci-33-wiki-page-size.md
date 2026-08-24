# Slice CI-33 — Wiki Index Page Size Reduction

## Problem

`/wiki` avg 14.6s, p95 21.3s under 50 VUs.
Root cause: `PAGE_SIZE = 60` means Thymeleaf renders 60 wiki cards per request.
Each card contains: page-type badge, title, tags loop (th:each), revision number, timestamp.
60 cards × 50 concurrent VUs = 3,000 card renders simultaneously → CPU saturation on t3 EC2.

The wiki projection (`WikiPageIndexView`) already excludes `content_markdown` (the 1.3 KB TEXT column),
so the DB transfer is not the bottleneck — Thymeleaf rendering is.

## Solution

Reduce `PAGE_SIZE` from 60 → 20. Three times less rendering per request.
Expected gain: 14.6s → target ~4s avg (3× rendering reduction on a CPU-bound page).

Secondary fix: the wiki index template shows `page.sources().size() + ' sources'` for every card,
but `indexViewToPage()` passes `List.of()` for sources — so every card shows "0 sources".
Remove the misleading sources count from the index card. (The detail page still shows real sources.)

## Files to change

1. `web/controller/WikiController.java` — `PAGE_SIZE` 60 → 20
2. `templates/wiki-index.html` — remove `"X sources"` span (shows 0 always; detail page has real data)
3. `web/controller/WikiControllerTest.java` — update any test that asserts page count based on 60

## WikiController change

```java
// Before
private static final int PAGE_SIZE = 60;

// After
private static final int PAGE_SIZE = 20;
```

## Template change (wiki-index.html)

Remove line 99:
```html
<span ... th:text="${page.sources().size()} + ' sources'">0 sources</span>
```
Replace with nothing (or a placeholder if the surrounding layout requires it).

## Verification

```bash
mvn test -Dtest="WikiControllerTest"
```

## Expected load-test result

Before: avg 14.6s, p95 21.3s
After:  target avg ~4s, p95 ~8s (3× fewer cards rendered; not linear because CPU contention also drops)
