# CI-34 — Article Quality Filter

## Problem

Newsletter digest email showed articles like:

```
Legal Brief: 16 litigation updates, 62 policy developments and 10 regulatory events
Litigation (16)
PERPLEXITY
PERPLEXITY
...
Policy (62)
PERPLEXITY
...
```

Root cause: harvesters (Perplexity API, some RSS feeds) store articles where the `title`
field is the source label ("PERPLEXITY", "RESEARCH", etc.) rather than a real headline,
and `bodyText` is null or blank. Neither `isNonsenseTitle()` in `DigestNewsletterRenderer`
nor `isDomainTitle()` in `LegalBriefSectionBuilder` catches these — both checks look for
URL/domain patterns, not source-label-style titles.

## Goal

Exclude zero-information articles (title = source label, no body) from:
1. Newsletter digest email (DigestNewsletterRenderer)
2. Legal & Regulatory Brief section (LegalBriefSectionBuilder)
3. Future harvests via storage gate (ArticleStorageAdapter)

---

## Usability Rules (ArticleQualityFilter.isUsable)

An article is **unusable** if any of these are true:

| Rule | Example caught |
|------|---------------|
| title is null or blank | `title=null` |
| title (trimmed, uppercase) is in LABEL_TITLES set | `title="PERPLEXITY"` |
| title equals sourceName (case-insensitive, trimmed) | `title="Healthcare Dive", sourceName="Healthcare Dive"` |
| title < 10 chars AND bodyText blank | `title="FDA"`, no body |

**LABEL_TITLES set** (source/tier names, not headlines):
`PERPLEXITY, RESEARCH, ACADEMIC, INDUSTRY, REGULATORY, COMPETITOR, HUGGINGFACE, PUBMED`

---

## Files

### Create
| File | Notes |
|------|-------|
| `domain/service/ArticleQualityFilter.java` | Pure Java, no Spring — `isUsable(NewsArticle)` |
| `domain/service/ArticleQualityFilterTest.java` | ~10 unit tests |

### Modify
| File | Change |
|------|--------|
| `domain/service/DigestNewsletterRenderer.java` | Inject filter; add `isUsable()` check in `filterAndDedup()` |
| `domain/service/LegalBriefSectionBuilder.java` | Inject filter; filter `legalArticles` + `policyArticles` before building items; update `litigationCount`/`policyCount` from filtered list |
| `infrastructure/persistence/ArticleStorageAdapter.java` | Inject filter; skip unusable articles in `save()` with `log.debug` |
| `infrastructure/config/AppConfig.java` | Add `@Bean articleQualityFilter()`; pass to 3 bean constructors |
| `DigestNewsletterRendererTest.java` | Update constructor calls; add test for PERPLEXITY article filtered |
| `LegalBriefSectionBuilderTest.java` | Update constructor calls; add test verifying junk articles excluded |
| `ArticleStorageAdapterTest.java` | Instantiate filter in `setUp()` |

---

## Key constructor changes

```java
// DigestNewsletterRenderer — 4th param
new DigestNewsletterRenderer(ingestionPort, scoringPort, bodyFormattingPort, articleQualityFilter)

// LegalBriefSectionBuilder — 3rd param
new LegalBriefSectionBuilder(ingestionPort, regulatoryUseCase, articleQualityFilter)

// ArticleStorageAdapter — 2nd param after repository
new ArticleStorageAdapter(repository, articleQualityFilter)
```

---

## Verification

```bash
mvn test -Dtest="ArticleQualityFilterTest,DigestNewsletterRendererTest,LegalBriefSectionBuilderTest,ArticleStorageAdapterTest"
```

Confirm:
- `ArticleQualityFilter.isUsable()` returns false for all 4 rejection criteria
- DigestNewsletterRenderer excludes "PERPLEXITY"-titled articles
- LegalBriefSectionBuilder excludes them from both legalArticles and policyArticles
- ArticleStorageAdapter skips them at save time with debug log

---

## Out of scope
- Retroactive DB cleanup (existing junk stays in DB but won't render)
- Search results filtering (newsletter rendering is the user-visible surface)
- Adding to NewsletterService AI summarization path (AI ignores empty titles naturally)
