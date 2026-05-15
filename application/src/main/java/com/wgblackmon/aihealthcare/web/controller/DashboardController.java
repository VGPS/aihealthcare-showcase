package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * @version 1.2
 * @since   2026-05-04
 * @updated 2026-05-15
 */
@Slf4j
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final GetAnalyticsUseCase analyticsUseCase;
    private final ArticleIngestionPort articleIngestionPort;
    private final NewsTopicProperties newsTopicProperties;

    public DashboardController(GetAnalyticsUseCase analyticsUseCase,
                               ArticleIngestionPort articleIngestionPort,
                               NewsTopicProperties newsTopicProperties) {
        this.analyticsUseCase     = analyticsUseCase;
        this.articleIngestionPort = articleIngestionPort;
        this.newsTopicProperties  = newsTopicProperties;
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
        RunAnalytics runs = analyticsUseCase.getRunAnalytics();
        EvaluationAnalytics evaluations = analyticsUseCase.getEvaluationAnalytics();

        String mostRecentRunDisplay = runs.mostRecentRunAt() != null
                ? DISPLAY_FMT.format(runs.mostRecentRunAt()) + " UTC"
                : null;

        model.addAttribute("ingestion", ingestion);
        model.addAttribute("runs", runs);
        model.addAttribute("evaluations", evaluations);
        model.addAttribute("mostRecentRunDisplay", mostRecentRunDisplay);

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
            Model model) {
        log.debug("articles() | topic={}, sort={}", topic, sort);

        List<NewsArticle> articles = new ArrayList<>(
                articleIngestionPort.fetchAllByTopic(topic));

        sortByPublishedAt(articles, "asc".equalsIgnoreCase(sort));

        model.addAttribute("articles", articles);
        model.addAttribute("topic", topic);
        model.addAttribute("sort", sort);

        log.debug("articles() | return=articles (count={})", articles.size());
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
            Model model) {
        log.debug("newsListing() | sort={}", sort);

        List<String> topicNames = newsTopicProperties.getTopics();
        boolean ascending = "asc".equalsIgnoreCase(sort);

        Map<String, List<NewsArticle>> topicArticles = new LinkedHashMap<>();
        int totalArticles = 0;

        for (String topic : topicNames) {
            List<NewsArticle> articles = new ArrayList<>(
                    articleIngestionPort.fetchAllByTopic(topic));
            sortByPublishedAt(articles, ascending);
            topicArticles.put(topic, articles);
            totalArticles += articles.size();
        }

        model.addAttribute("topicArticles", topicArticles);
        model.addAttribute("topicNames", topicNames);
        model.addAttribute("sort", sort);
        model.addAttribute("totalArticles", totalArticles);

        log.debug("newsListing() | return=news-listing (topics={}, totalArticles={})",
                  topicNames.size(), totalArticles);
        return "news-listing";
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
