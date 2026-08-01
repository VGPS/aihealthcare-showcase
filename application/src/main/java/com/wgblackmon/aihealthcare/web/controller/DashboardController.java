package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.service.TrendDetectionService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the Daily Briefing dashboard and article-detail pages.
 *
 * <p>Serves {@code GET /dashboard} as a personalized briefing page with watchlist
 * alerts, top headlines, trending keywords, regulatory events, and legal developments.
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
 * @updated 2026-08-01
 */
@Slf4j
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private static final DateTimeFormatter NEWS_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter SHORT_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("America/New_York"));

    private final ArticleIngestionPort articleIngestionPort;
    private final NewsTopicProperties newsTopicProperties;
    private final TopicSummaryPort topicSummaryPort;
    private final SubscriberPort subscriberPort;
    private final TierGatingService tierGatingService;
    private final SearchArticlesUseCase searchUseCase;
    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final DetectTrendsUseCase detectTrendsUseCase;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final TrendDetectionService trendDetectionService;

    public DashboardController(ArticleIngestionPort articleIngestionPort,
                               NewsTopicProperties newsTopicProperties,
                               TopicSummaryPort topicSummaryPort,
                               SubscriberPort subscriberPort,
                               TierGatingService tierGatingService,
                               SearchArticlesUseCase searchUseCase,
                               WatchlistPort watchlistPort,
                               WatchlistMatchPort watchlistMatchPort,
                               DetectTrendsUseCase detectTrendsUseCase,
                               MonitorRegulatoryEventsUseCase regulatoryUseCase,
                               TrendDetectionService trendDetectionService) {
        this.articleIngestionPort    = articleIngestionPort;
        this.newsTopicProperties     = newsTopicProperties;
        this.topicSummaryPort        = topicSummaryPort;
        this.subscriberPort          = subscriberPort;
        this.tierGatingService       = tierGatingService;
        this.searchUseCase           = searchUseCase;
        this.watchlistPort           = watchlistPort;
        this.watchlistMatchPort      = watchlistMatchPort;
        this.detectTrendsUseCase     = detectTrendsUseCase;
        this.regulatoryUseCase       = regulatoryUseCase;
        this.trendDetectionService   = trendDetectionService;
    }

    /**
     * Renders the Daily Briefing dashboard — a personalized landing page pulling
     * together watchlist alerts, top headlines, trending keywords, regulatory
     * events, and legal developments.
     *
     * @param model     Thymeleaf model populated with briefing data
     * @param principal the authenticated user, or null for anonymous
     * @return Thymeleaf view name "dashboard"
     */
    @GetMapping
    public String dashboard(Model model, Principal principal,
                            @RequestParam(required = false) String q) {
        log.debug("dashboard() | principal={}, q={}", principal != null ? principal.getName() : "anonymous", q);

        String userEmail = principal != null ? principal.getName() : null;
        SubscriptionTier tier = resolveTier(principal);

        // --- Inline Search (if query provided) ---
        if (q != null && !q.isBlank()) {
            String trimmedQ = q.trim();
            ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                    trimmedQ, null, null, null, trimmedQ, null, null);
            List<NewsArticle> searchResults = searchUseCase.search(criteria);
            sortByPublishedAt(searchResults, false);
            // Limit to top 10 results
            List<NewsArticle> limitedResults = new ArrayList<>();
            for (int i = 0; i < searchResults.size() && i < 10; i++) {
                limitedResults.add(searchResults.get(i));
            }
            Map<String, String> searchDates = new HashMap<>();
            Map<String, String> searchTitles = new HashMap<>();
            Map<String, String> searchPubs = new HashMap<>();
            for (NewsArticle article : limitedResults) {
                if (article.publishedAt() != null) {
                    searchDates.put(article.articleId(), SHORT_DATE_FMT.format(article.publishedAt()));
                }
                String t = article.title();
                int dashIndex = t.lastIndexOf(" - ");
                if (dashIndex > 0) {
                    searchTitles.put(article.articleId(), t.substring(0, dashIndex).trim());
                    searchPubs.put(article.articleId(), t.substring(dashIndex + 3).trim());
                } else {
                    searchTitles.put(article.articleId(), t);
                }
            }
            model.addAttribute("searchQuery", trimmedQ);
            model.addAttribute("searchResults", limitedResults);
            model.addAttribute("searchResultCount", searchResults.size());
            model.addAttribute("searchDates", searchDates);
            model.addAttribute("searchTitles", searchTitles);
            model.addAttribute("searchPubs", searchPubs);
        } else {
            model.addAttribute("searchQuery", "");
            model.addAttribute("searchResults", List.of());
            model.addAttribute("searchResultCount", 0);
        }

        // --- Watchlist Alerts (only if user has items with matches) ---
        boolean hasWatchlist = false;
        List<WatchlistMatch> watchlistMatches = List.of();
        Map<String, String> watchlistLabels = new HashMap<>();
        if (userEmail != null) {
            List<WatchlistItem> items = watchlistPort.findByUser(userEmail);
            if (!items.isEmpty()) {
                watchlistMatches = watchlistMatchPort.findByUser(userEmail, 5);
                if (!watchlistMatches.isEmpty()) {
                    hasWatchlist = true;
                    for (WatchlistItem item : items) {
                        watchlistLabels.put(item.itemId(), item.label());
                    }
                }
            }
        }
        model.addAttribute("hasWatchlist", hasWatchlist);
        model.addAttribute("watchlistMatches", watchlistMatches);
        model.addAttribute("watchlistLabels", watchlistLabels);
        Map<String, String> matchDates = new HashMap<>();
        for (WatchlistMatch match : watchlistMatches) {
            if (match.matchedOn() != null) {
                matchDates.put(match.matchId(), SHORT_DATE_FMT.format(match.matchedOn()));
            }
        }
        model.addAttribute("matchDates", matchDates);

        // --- Today's Headlines (top 8 articles from last 7 days, highest source weight) ---
        List<NewsArticle> recentArticles = articleIngestionPort.fetchRecentArticles(7);
        // Sort by source weight descending, then by publishedAt descending
        sortByPublishedAt(recentArticles, false);
        List<NewsArticle> headlines = new ArrayList<>();
        java.util.Set<String> seenTitles = new java.util.HashSet<>();
        for (NewsArticle article : recentArticles) {
            String normalizedTitle = article.title() != null ? article.title().toLowerCase().trim() : "";
            if (!normalizedTitle.isEmpty() && seenTitles.add(normalizedTitle)) {
                headlines.add(article);
            }
            if (headlines.size() >= 8) {
                break;
            }
        }
        model.addAttribute("headlines", headlines);
        Map<String, String> headlineDates = new HashMap<>();
        Map<String, String> headlineTitles = new HashMap<>();
        Map<String, String> headlinePublications = new HashMap<>();
        Instant now = Instant.now();
        for (NewsArticle article : headlines) {
            if (article.publishedAt() != null) {
                if (article.publishedAt().isAfter(now)) {
                    headlineDates.put(article.articleId(),
                            "Print Issue Date: " + SHORT_DATE_FMT.format(article.publishedAt()));
                } else {
                    headlineDates.put(article.articleId(), SHORT_DATE_FMT.format(article.publishedAt()));
                }
            }
            String t = article.title();
            int dashIndex = t.lastIndexOf(" - ");
            if (dashIndex > 0) {
                headlineTitles.put(article.articleId(), t.substring(0, dashIndex).trim());
                headlinePublications.put(article.articleId(), t.substring(dashIndex + 3).trim());
            } else {
                headlineTitles.put(article.articleId(), t);
            }
        }
        model.addAttribute("headlineDates", headlineDates);
        model.addAttribute("headlineTitles", headlineTitles);
        model.addAttribute("headlinePublications", headlinePublications);

        // --- Trending Now (top 5 rising keywords from latest snapshot, or fallback from article topics) ---
        List<TrendSignal> risingTrends = new ArrayList<>();
        java.util.Optional<TrendSnapshot> snapshot = detectTrendsUseCase.getLatestSnapshot();
        if (snapshot.isPresent()) {
            List<TrendSignal> rising = snapshot.get().risingTopics();
            for (int i = 0; i < rising.size() && i < 5; i++) {
                risingTrends.add(rising.get(i));
            }
        }
        if (risingTrends.isEmpty()) {
            // Fallback: keyword frequency analysis via TrendDetectionService (7-day window)
            List<NewsArticle> trendArticles = articleIngestionPort.fetchRecentArticles(7);
            TrendSnapshot fallbackSnapshot = trendDetectionService.detectTrends(trendArticles, Instant.now());
            for (int i = 0; i < fallbackSnapshot.risingTopics().size() && risingTrends.size() < 5; i++) {
                risingTrends.add(fallbackSnapshot.risingTopics().get(i));
            }
        }
        model.addAttribute("risingTrends", risingTrends);

        // --- Regulatory Watch (latest 3 events) ---
        List<RegulatoryEvent> recentRegEvents = regulatoryUseCase.getRecentEvents(3);
        model.addAttribute("regulatoryEvents", recentRegEvents);
        Map<String, String> regDates = new HashMap<>();
        for (RegulatoryEvent event : recentRegEvents) {
            if (event.discoveredAt() != null) {
                regDates.put(event.eventId(), SHORT_DATE_FMT.format(event.discoveredAt()));
            }
        }
        model.addAttribute("regDates", regDates);

        // --- Legal Pulse (latest 3 legal articles) ---
        List<NewsArticle> legalArticles = articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 0);
        sortByPublishedAt(legalArticles, false);
        List<NewsArticle> legalPulse = new ArrayList<>();
        for (int i = 0; i < legalArticles.size() && legalPulse.size() < 3; i++) {
            legalPulse.add(legalArticles.get(i));
        }
        model.addAttribute("legalPulse", legalPulse);
        Map<String, String> legalDates = new HashMap<>();
        Map<String, String> legalTitles = new HashMap<>();
        for (NewsArticle article : legalPulse) {
            if (article.publishedAt() != null) {
                legalDates.put(article.articleId(), SHORT_DATE_FMT.format(article.publishedAt()));
            }
            String t = article.title();
            int dashIndex = t.lastIndexOf(" - ");
            if (dashIndex > 0) {
                legalTitles.put(article.articleId(), t.substring(0, dashIndex).trim());
            } else {
                legalTitles.put(article.articleId(), t);
            }
        }
        model.addAttribute("legalDates", legalDates);
        model.addAttribute("legalTitles", legalTitles);

        model.addAttribute("tier", tier);

        log.debug("dashboard() | return=dashboard (headlines={}, watchlist={}, trends={}, reg={}, legal={})",
                  headlines.size(), watchlistMatches.size(), risingTrends.size(),
                  recentRegEvents.size(), legalPulse.size());
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

        // Gate "New AI Healthcare Companies" topic to SUBSCRIBER tier
        if ("New AI Healthcare Companies".equals(topic) && !isAdmin(principal)) {
            SubscriptionTier tier = resolveTier(principal);
            if (tier != SubscriptionTier.SUBSCRIBER) {
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
     * @param sort   "asc" for oldest-first, "desc" (default) for newest-first
     * @param sortBy "date" (default), "title", or "publication"
     * @param model  Thymeleaf model
     * @return Thymeleaf view name "news-listing"
     */
    @GetMapping("/news")
    public String newsListing(
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(defaultValue = "date") String sortBy,
            Principal principal,
            Model model) {
        log.debug("newsListing() | sort={}, sortBy={}, principal={}", sort, sortBy, principal != null ? principal.getName() : "anonymous");

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
            // Extract titles, publications, and dates before sorting
            for (NewsArticle article : articles) {
                if (article.publishedAt() != null) {
                    articleDates.put(article.articleId(), NEWS_DATE_FMT.format(article.publishedAt()));
                }
                String t = article.title();
                int dashIndex = t.lastIndexOf(" - ");
                if (dashIndex > 0) {
                    articleTitles.put(article.articleId(), t.substring(0, dashIndex).trim());
                    articlePublications.put(article.articleId(), t.substring(dashIndex + 3).trim());
                } else {
                    articleTitles.put(article.articleId(), t);
                }
            }

            sortArticles(articles, sortBy, ascending, articleTitles, articlePublications);
            topicArticles.put(topic, articles);
            totalArticles += articles.size();
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
        model.addAttribute("sortBy", sortBy);
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
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        log.debug("search() | title={}, topic={}, author={}, sourceName={}, "
                + "bodyText={}, publishedFrom={}, publishedTo={}, sortDate={}, hxRequest={}",
                title, topic, author, sourceName, bodyText,
                publishedFrom, publishedTo, sortDate, hxRequest);

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

        boolean isHtmx = "true".equals(hxRequest);
        String viewName = isHtmx ? "fragments/search-results :: results" : "search";
        log.debug("search() | return={} (resultCount={}, htmx={})", viewName, sorted.size(), isHtmx);
        return viewName;
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
                    log.debug("resolveTier() | ADMIN role detected, return={}", SubscriptionTier.SUBSCRIBER);
                    return SubscriptionTier.SUBSCRIBER;
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

    /**
     * Sorts articles in-place by the given field.
     *
     * @param articles      mutable list to sort
     * @param sortBy        "date", "title", or "publication"
     * @param ascending     true for A-Z / oldest-first, false for Z-A / newest-first
     * @param titles        pre-computed display titles (without publication suffix)
     * @param publications  pre-computed publication names
     */
    private void sortArticles(List<NewsArticle> articles, String sortBy, boolean ascending,
                              Map<String, String> titles, Map<String, String> publications) {
        log.debug("sortArticles() | size={}, sortBy={}, ascending={}", articles.size(), sortBy, ascending);

        if ("title".equalsIgnoreCase(sortBy)) {
            int n = articles.size();
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - 1 - i; j++) {
                    String ta = titles.getOrDefault(articles.get(j).articleId(), "");
                    String tb = titles.getOrDefault(articles.get(j + 1).articleId(), "");
                    int cmp = ta.compareToIgnoreCase(tb);
                    boolean swap = ascending ? cmp > 0 : cmp < 0;
                    if (swap) {
                        NewsArticle tmp = articles.get(j);
                        articles.set(j, articles.get(j + 1));
                        articles.set(j + 1, tmp);
                    }
                }
            }
        } else if ("publication".equalsIgnoreCase(sortBy)) {
            int n = articles.size();
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - 1 - i; j++) {
                    String pa = publications.getOrDefault(articles.get(j).articleId(), "");
                    String pb = publications.getOrDefault(articles.get(j + 1).articleId(), "");
                    int cmp = pa.compareToIgnoreCase(pb);
                    boolean swap = ascending ? cmp > 0 : cmp < 0;
                    if (swap) {
                        NewsArticle tmp = articles.get(j);
                        articles.set(j, articles.get(j + 1));
                        articles.set(j + 1, tmp);
                    }
                }
            }
        } else {
            sortByPublishedAt(articles, ascending);
        }

        log.debug("sortArticles() | return=void");
    }
}
