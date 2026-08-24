# Slice CI-32 — Deals Page Pagination

## Problem

`/dashboard/deals` avg 4.9s, p95 7.8s under 50 VUs after the llmAnalysis projection fix.
Root cause: SUBSCRIBER/ADMIN users load 100 deal signals per request. Thymeleaf renders
100 table rows × conditional CSS class logic per row × 50 VUs simultaneously = CPU saturation.

## Solution

Server-side pagination at 25 rows/page (SUBSCRIBER/ADMIN).
- FREE users: 10 rows, no pagination (unchanged).
- SUBSCRIBER/DEMO/ADMIN: 25 rows/page, `?page=` query param, prev/next nav.
- "Peek-ahead by 1": fetch PAGE_SIZE + 1 rows; if result > PAGE_SIZE → hasNext=true, trim to PAGE_SIZE.
  Avoids a COUNT(*) query entirely.

Expected gain: 100 rows → 25 rows = 4× less Thymeleaf rendering per request.

## Signature changes (surgical — no domain record changes)

| Layer | Before | After |
|-------|--------|-------|
| `DealSignalPort` | `findRecent(int limit)` | `findRecent(int pageSize, int page)` |
| `DealSignalPort` | `findByType(String type, int limit)` | `findByType(String type, int pageSize, int page)` |
| `DetectDealSignalsUseCase` | `getRecentSignals(int limit)` | `getRecentSignals(int pageSize, int page)` |
| `DetectDealSignalsUseCase` | `getSignalsByType(type, int limit)` | `getSignalsByType(type, int pageSize, int page)` |
| `DealSignalDetectionService` | delegates limit | delegates pageSize + page |
| `DealSignalAdapter` | `PageRequest.of(0, limit)` | `PageRequest.of(page, pageSize)` |
| `DealSignalController` | FULL_LIMIT=100 | PAGE_SIZE=25, ?page=0 param, peek-ahead |

## Files to change

1. `domain/port/outbound/DealSignalPort.java` — new signatures for findRecent + findByType
2. `domain/port/inbound/DetectDealSignalsUseCase.java` — new signatures for getRecentSignals + getSignalsByType
3. `domain/service/DealSignalDetectionService.java` — delegate with page param
4. `infrastructure/persistence/DealSignalAdapter.java` — PageRequest.of(page, pageSize)
5. `web/controller/DealSignalController.java` — PAGE_SIZE=25, ?page param, peek-ahead, hasNext/hasPrev model attrs
6. `templates/deals.html` — prev/next pagination nav
7. `web/controller/DealSignalControllerTest.java` — update stubs + add pagination tests
8. `infrastructure/persistence/DealSignalAdapterTest.java` — update to new signatures

## Controller logic (pseudo-code)

```java
private static final int PAGE_SIZE = 25;
private static final int FREE_LIMIT = 10;

GET /dashboard/deals?type=&page=0
  boolean fullAccess = isFullAccess(user);
  int page = fullAccess ? Math.max(0, pageParam) : 0;
  int fetchSize = fullAccess ? PAGE_SIZE + 1 : FREE_LIMIT;

  List<DealSignal> signals =
    filterType != null
      ? useCase.getSignalsByType(type, fetchSize, page)
      : useCase.getRecentSignals(fetchSize, page);

  boolean hasNext = fullAccess && signals.size() > PAGE_SIZE;
  if (hasNext) signals = signals.subList(0, PAGE_SIZE);

  model.addAttribute("signals", signals);
  model.addAttribute("currentPage", page);
  model.addAttribute("hasPrev", page > 0);
  model.addAttribute("hasNext", hasNext);
  model.addAttribute("filterType", filterType);
```

## Template additions (deals.html)

Add below the signals table, before closing container div:

```html
<div th:if="${fullAccess}" class="flex justify-between items-center mt-4 pt-4 border-t border-gray-100">
  <a th:if="${hasPrev}"
     th:href="@{/dashboard/deals(page=${currentPage - 1}, type=${filterType})}"
     class="px-4 py-2 text-sm bg-white border border-gray-300 rounded-lg hover:bg-gray-50">← Previous</a>
  <span th:unless="${hasPrev}" class="px-4 py-2 text-sm text-gray-300">← Previous</span>
  <span class="text-sm text-gray-500">Page <span th:text="${currentPage + 1}">1</span></span>
  <a th:if="${hasNext}"
     th:href="@{/dashboard/deals(page=${currentPage + 1}, type=${filterType})}"
     class="px-4 py-2 text-sm bg-white border border-gray-300 rounded-lg hover:bg-gray-50">Next →</a>
  <span th:unless="${hasNext}" class="px-4 py-2 text-sm text-gray-300">Next →</span>
</div>
```

## Tests to add (DealSignalControllerTest)

- `dealsPage_paginationAttrsPresent_page0()` — model has currentPage=0, hasPrev=false
- `dealsPage_hasNextTrue_when26SignalsReturned()` — 26 results → hasNext=true, signals trimmed to 25
- `dealsPage_page1_hasPrevTrue()` — page=1 → hasPrev=true

## Verification

```bash
mvn test -Dtest="DealSignalControllerTest,DealSignalAdapterTest,DealSignalDetectionServiceTest"
```
