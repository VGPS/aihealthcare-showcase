package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the analytics dashboard and article-detail pages.
 *
 * <p>Serves {@code GET /dashboard} by fetching all three analytics aggregates
 * from {@link GetAnalyticsUseCase} and adding them to the Thymeleaf model.
 *
 * <p>Serves {@code GET /dashboard/articles?topic=...&sort=asc|desc} by fetching
 * up to 500 articles for the given topic via {@link ArticleIngestionPort}, sorting
 * them by {@code publishedAt} in the requested direction (default: descending),
 * and rendering the {@code articles} view.  Null {@code publishedAt} values sort
 * to the end in both directions.
 *
 * <p>Serves {@code GET /dashboard/news} by fetching articles for all configured
 * topic names from {@link NewsTopicProperties} and grouping them into an ordered
 * map for the {@code news-listing} template.
 *
 * @author  Bill Blackmon
 * @version 1.4
 * @since   2026-05-04
 * @updated 2026-07-05
 */
@Slf4j
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private static final DateTimeFormatter NEWS_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(ZoneId.of("America/New_York"));

    private final GetAnalyticsUseCase analyticsUseCase;
    private final ArticleIngestionPort articleIngestionPort;
    private final NewsTopicProperties newsTopicProperties;
    private final TopicSummaryPort topicSummaryPort;
    private final SubscriberPort subscriberPort;
    private final TierGatingService tierGatingService;
    private final SearchArticlesUseCase searchUseCase;

    public DashboardController(GetAnalyticsUseCase analyticsUseCase,
                               ArticleIngestionPort articleIngestionPort,
                               NewsTopicProperties newsTopicProperties,
                               TopicSummaryPort topicSummaryPort,
                               SubscriberPort subscriberPort,
                               TierGatingService tierGatingService,
                               SearchArticlesUseCase searchUseCase) {
        this.analyticsUseCase        = analyticsUseCase;
        this.articleIngestionPort    = articleIngestionPort;
        this.newsTopicProperties     = newsTopicProperties;
        this.topicSummaryPort        = topicSummaryPort;
        this.subscriberPort          = subscriberPort;
        this.tierGatingService       = tierGatingService;
        this.searchUseCase           = searchUseCase;
    }

    /**
     * Renders the main analytics dashboard page.
     *
     * @param model Thymeleaf model populated with analytics data
     * @return Thymeleaf view name "dashboard"
     */
    @GetMapping
    public String dashboard(Model model) {
        log.debug("dashboard()");

        IngestionAnalytics ingestion = analyticsUseCase.getIngestionAnalytics();
        model.addAttribute("ingestion", ingestion);

        // Chart data: articles per day (last 30 days)
        List<CountByLabel> dailyCounts = analyticsUseCase.getDailyArticleCounts(30);
        List<String> chartLabels = new ArrayList<>();
        List<Long> chartData = new ArrayList<>();
        for (CountByLabel entry : dailyCounts) {
            chartLabels.add(entry.label());
            chartData.add(entry.count());
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);

        // Chart data: topic distribution (top 10)
        List<CountByLabel> topicCounts = analyticsUseCase.getTopicDistribution(10);
        List<String> topicLabels = new ArrayList<>();
        List<Long> topicData = new ArrayList<>();
        for (CountByLabel entry : topicCounts) {
            String topicName = entry.label();
            if (topicName.length() > 25) {
                topicName = topicName.substring(0, 22) + "...";
            }
            topicLabels.add(topicName);
            topicData.add(entry.count());
        }
        model.addAttribute("topicChartLabels", topicLabels);
        model.addAttribute("topicChartData", topicData);

        log.debug("dashboard() | return=dashboard");
        return "dashboard";
    }

    /**
     * Renders the article-detail page for a single topic/feed.
     *
     * <p>Fetches up to {@value ARTICLE_DETAIL_LIMIT} articles for the given
     * {@code topic} and sorts them by {@code publishedAt}.  Articles with a
     * null {@code publishedAt} appear last regardless of sort direction.
     *
     * @param topic  exact topic label to filter by (URL-decoded by Spring MVC)
     * @param sort   "asc" for oldest-first, "desc" (default) for newest-first
     * @param model  Thymeleaf model
     * @return Thymeleaf view name "articles"
     */
    @GetMapping("/articles")
    public String articles(
            @RequestParam String topic,
            @RequestParam(defaultValue = "desc") String sort,
            Principal principal,
            Model model) {
        log.debug("articles() | topic={}, sort={}", topic, sort);

        // Gate "New AI Healthcare Companies" topic to MEMBER tier
        if ("New AI Healthcare Companies".equals(topic) && !isAdmin(principal)) {
            SubscriptionTier tier = resolveTier(principal);
            if (tier != SubscriptionTier.MEMBER) {
                log.debug("articles() | access denied for FREE tier user on topic={}", topic);
                model.addAttribute("topic", topic);
                model.addAttribute("accessDenied", true);
                model.addAttribute("articles", List.of());
                model.addAttribute("sort", sort);
                return "articles";
            }
        }

        List<NewsArticle> fetched = new ArrayList<>(
                articleIngestionPort.fetchAllByTopic(topic));

        // Sort newest-first so dedup keeps the most recent
        sortByPublishedAt(fetched, false);

        // Deduplicate by title — keep only the first (most recent) occurrence
        List<NewsArticle> deduped = new ArrayList<>();
        java.util.Set<String> seenTitles = new java.util.HashSet<>();
        for (NewsArticle article : fetched) {
            String normalizedTitle = article.title() != null ? article.title().toLowerCase().trim() : "";
            if (!normalizedTitle.isEmpty() && seenTitles.add(normalizedTitle)) {
                deduped.add(article);
            }
        }
        log.debug("articles() | deduped from {} to {} articles", fetched.size(), deduped.size());

        // Re-sort in the user's requested direction
        sortByPublishedAt(deduped, "asc".equalsIgnoreCase(sort));

        // Format body text: strip HTML, collapse whitespace, truncate to ~200 chars
        Map<String, String> articleSummaries = new HashMap<>();
        for (NewsArticle article : deduped) {
            if (article.bodyText() != null && !article.bodyText().isBlank()) {
                String clean = article.bodyText()
                        .replaceAll("<[^>]+>", " ")
                        .replaceAll("&nbsp;", " ")
                        .replaceAll("&amp;", "&")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (clean.length() > 200) {
                    clean = clean.substring(0, 200) + "...";
                }
                articleSummaries.put(article.articleId(), clean);
            }
        }

        model.addAttribute("articles", deduped);
        model.addAttribute("articleSummaries", articleSummaries);
        model.addAttribute("topic", topic);
        model.addAttribute("sort", sort);

        log.debug("articles() | return=articles (count={})", deduped.size());
        return "articles";
    }

    /**
     * Renders the news listing page with all articles grouped by topic.
     *
     * <p>Iterates through the configured topic names from {@link NewsTopicProperties},
     * fetches all articles for each topic, sorts them newest-first, and builds an
     * ordered map of topic → articles for the template.
     *
     * @param sort  "asc" for oldest-first, "desc" (default) for newest-first
     * @param model Thymeleaf model
     * @return Thymeleaf view name "news-listing"
     */
    @GetMapping("/news")
    public String newsListing(
            @RequestParam(defaultValue = "desc") String sort,
            Principal principal,
            Model model) {
        log.debug("newsListing() | sort={}, principal={}", sort, principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        int archiveDays = tierGatingService.archiveDaysFor(tier);
        log.debug("newsListing() | resolved tier={}, archiveDays={}", tier, archiveDays);

        List<String> topicNames = newsTopicProperties.getTopics();
        boolean ascending = "asc".equalsIgnoreCase(sort);

        Map<String, List<NewsArticle>> topicArticles = new LinkedHashMap<>();
        Map<String, String> articleDates = new HashMap<>();
        Map<String, String> articleTitles = new HashMap<>();
        Map<String, String> articlePublications = new HashMap<>();
        int totalArticles = 0;

        for (String topic : topicNames) {
            List<NewsArticle> fetched = articleIngestionPort.fetchByTopicWithArchiveLimit(topic, archiveDays);
            List<NewsArticle> articles = new ArrayList<>();
            for (NewsArticle a : fetched) {
                if (!"Anthropic Healthcare AI".equals(a.title())) {
                    articles.add(a);
                }
            }
            sortByPublishedAt(articles, ascending);
            topicArticles.put(topic, articles);
            totalArticles += articles.size();

            for (NewsArticle article : articles) {
                if (article.publishedAt() != null) {
                    articleDates.put(article.articleId(), NEWS_DATE_FMT.format(article.publishedAt()));
                }
                String title = article.title();
                int dashIndex = title.lastIndexOf(" - ");
                if (dashIndex > 0) {
                    articleTitles.put(article.articleId(), title.substring(0, dashIndex).trim());
                    articlePublications.put(article.articleId(), title.substring(dashIndex + 3).trim());
                } else {
                    articleTitles.put(article.articleId(), title);
                }
            }
        }

        Map<String, String> topicSummaries = new HashMap<>();
        for (String topic : topicNames) {
            List<NewsArticle> articles = topicArticles.get(topic);
            if (articles != null && articles.size() > 1) {
                Optional<com.wgblackmon.aihealthcare.domain.model.TopicSummary> summary =
                        topicSummaryPort.findByTopic(topic);
                if (summary.isPresent()) {
                    topicSummaries.put(topic, summary.get().summaryText());
                }
            }
        }

        model.addAttribute("topicArticles", topicArticles);
        model.addAttribute("topicNames", topicNames);
        model.addAttribute("articleDates", articleDates);
        model.addAttribute("articleTitles", articleTitles);
        model.addAttribute("articlePublications", articlePublications);
        model.addAttribute("topicSummaries", topicSummaries);
        model.addAttribute("sort", sort);
        model.addAttribute("totalArticles", totalArticles);
        model.addAttribute("archiveLimited", archiveDays > 0);
        model.addAttribute("archiveDays", archiveDays);

        log.debug("newsListing() | return=news-listing (topics={}, totalArticles={})",
                  topicNames.size(), totalArticles);
        return "news-listing";
    }

    /**
     * Renders the article search page with optional filter criteria.
     *
     * <p>All parameters are optional. When submitted, the form re-renders with
     * matching results and the criteria echoed back into the form fields.
     *
     * @param title         substring match against title
     * @param topic         substring match against topic
     * @param author        substring match against author
     * @param sourceName    substring match against source name
     * @param bodyText      substring match against body text
     * @param publishedFrom lower bound on publishedAt (yyyy-MM-ddTHH:mm format)
     * @param publishedTo   upper bound on publishedAt
     * @param model         Thymeleaf model
     * @return Thymeleaf view name "search"
     */
    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String bodyText,
            @RequestParam(required = false) String publishedFrom,
            @RequestParam(required = false) String publishedTo,
            @RequestParam(defaultValue = "desc") String sortDate,
            Model model) {
        log.debug("search() | title={}, topic={}, author={}, sourceName={}, "
                + "bodyText={}, publishedFrom={}, publishedTo={}, sortDate={}",
                title, topic, author, sourceName, bodyText,
                publishedFrom, publishedTo, sortDate);

        Instant pubFrom = parseDateTime(publishedFrom);
        Instant pubTo = parseDateTime(publishedTo);

        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                blankToNull(title), blankToNull(topic), blankToNull(author),
                blankToNull(sourceName), blankToNull(bodyText),
                pubFrom, pubTo);

        List<NewsArticle> articles = searchUseCase.search(criteria);

        List<NewsArticle> sorted = new ArrayList<>(articles);
        sortByPublishedAt(sorted, "asc".equalsIgnoreCase(sortDate));

        model.addAttribute("articles", sorted);
        model.addAttribute("resultCount", sorted.size());
        model.addAttribute("searched", !criteria.isEmpty());
        model.addAttribute("titleParam", title);
        model.addAttribute("topicParam", topic);
        model.addAttribute("authorParam", author);
        model.addAttribute("sourceNameParam", sourceName);
        model.addAttribute("bodyTextParam", bodyText);
        model.addAttribute("publishedFromParam", publishedFrom);
        model.addAttribute("publishedToParam", publishedTo);
        model.addAttribute("sortDate", sortDate);

        log.debug("search() | return=search (resultCount={})", sorted.size());
        return "search";
    }

    /**
     * Parses a datetime-local string ({@code yyyy-MM-ddTHH:mm}) to an {@link Instant}
     * at UTC. Returns {@code null} if the input is null or blank.
     *
     * @param dateTimeStr the datetime string from an HTML datetime-local input
     * @return the corresponding UTC instant, or null
     */
    /** Returns null when the input is null or blank, otherwise returns the trimmed string. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Instant parseDateTime(String dateTimeStr) {
        log.debug("parseDateTime() | dateTimeStr={}", dateTimeStr);
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            log.debug("parseDateTime() | return=null");
            return null;
        }
        Instant result = LocalDateTime.parse(dateTimeStr).toInstant(java.time.ZoneOffset.UTC);
        log.debug("parseDateTime() | return={}", result);
        return result;
    }

    /**
     * Resolves the subscription tier for the currently authenticated user.
     * Returns {@link SubscriptionTier#FREE} if the user is anonymous or has
     * no subscriber record.
     *
     * @param principal the Spring Security principal; may be {@code null} for anonymous access
     * @return the subscriber's tier, defaulting to FREE
     */
    private boolean isAdmin(Principal principal) {
        log.debug("isAdmin() | principal={}", principal != null ? principal.getName() : "null");

        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    log.debug("isAdmin() | return=true");
                    return true;
                }
            }
        }

        log.debug("isAdmin() | return=false");
        return false;
    }

    private SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }

        // ADMIN users get full access — bypass subscriber lookup
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    log.debug("resolveTier() | ADMIN role detected, return={}", SubscriptionTier.MEMBER);
                    return SubscriptionTier.MEMBER;
                }
            }
        }

        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);

        log.debug("resolveTier() | return={}", result);
        return result;
    }

    /**
     * Sorts the list in-place by {@code publishedAt}.
     * Null timestamps sort to the end in both ascending and descending directions.
     *
     * @param articles  mutable list to sort in-place
     * @param ascending true for oldest-first, false for newest-first
     */
    private void sortByPublishedAt(List<NewsArticle> articles, boolean ascending) {
        log.debug("sortByPublishedAt() | size={}, ascending={}", articles.size(), ascending);

        int n = articles.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                NewsArticle a = articles.get(j);
                NewsArticle b = articles.get(j + 1);
                Instant ta = a.publishedAt();
                Instant tb = b.publishedAt();

                // Nulls always go to the end
                boolean swap;
                if (ta == null && tb == null) {
                    swap = false;
                } else if (ta == null) {
                    swap = true;   // null a after non-null b
                } else if (tb == null) {
                    swap = false;  // non-null a before null b
                } else {
                    swap = ascending ? ta.isAfter(tb) : ta.isBefore(tb);
                }

                if (swap) {
                    articles.set(j, b);
                    articles.set(j + 1, a);
                }
            }
        }

        log.debug("sortByPublishedAt() | return=void");
    }
}
