package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Enterprise data source adapter for the internal article corpus.
 *
 * <p>Delegates to {@link ArticleIngestionPort} and maps {@link NewsArticle}
 * records into a tabular {@link DataSet}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-11
 */
@Slf4j
@Component
public class ArticleCorpusDataSourceAdapter implements EnterpriseDataSourcePort {

    static final String FEED_ID = "articles";

    private static final int SUMMARY_MAX = 300;

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("title", "Title", "STRING"),
            new DataColumn("url", "URL", "URI"),
            new DataColumn("publication", "Publication", "STRING"),
            new DataColumn("publishedAt", "Published", "DATE"),
            new DataColumn("summary", "Summary", "STRING")
    );

    private final ArticleIngestionPort articlePort;

    public ArticleCorpusDataSourceAdapter(ArticleIngestionPort articlePort) {
        this.articlePort = articlePort;
        log.debug("ArticleCorpusDataSourceAdapter()");
    }

    @Override
    public String feedId() { return FEED_ID; }

    @Override
    public DataSourceKind kind() { return DataSourceKind.INTERNAL_CORPUS; }

    @Override
    public DataFeed describe() {
        log.debug("describe()");
        DataFeed result = new DataFeed(
                FEED_ID, "Article Corpus",
                "Harvested AI-in-healthcare articles from RSS, web, and API sources.",
                DataSourceKind.INTERNAL_CORPUS,
                List.of(ExportFormat.CSV, ExportFormat.JSON),
                List.of(
                        new DataParameter("keyword", "Topic keyword", "STRING", false, null, List.of()),
                        new DataParameter("days", "Lookback days", "INTEGER", false, "30", List.of())
                ),
                100, 10000, "TIME_SERIES", true
        );
        log.debug("describe() | return={}", result.feedId());
        return result;
    }

    @Override
    public boolean supports(DataRequest request) {
        log.debug("supports() | feedId={}", request.feedId());
        boolean result = FEED_ID.equals(request.feedId());
        log.debug("supports() | return={}", result);
        return result;
    }

    @Override
    public DataSet fetch(DataRequest request, DataJobLog jobLog) {
        log.debug("fetch() | jobId={}, rowLimit={}", request.jobId(), request.rowLimit());
        jobLog.phase("FETCH_START", "feed=articles rowLimit=" + request.rowLimit());

        List<NewsArticle> articles = fetchArticles(request);
        int originalCount = articles.size();
        boolean truncated = articles.size() > request.rowLimit();
        if (truncated) {
            articles = articles.subList(0, request.rowLimit());
        }

        List<List<String>> rows = articles.stream()
                .map(this::toRow)
                .toList();

        jobLog.phase("FETCH_END", "rows=" + rows.size() + " truncated=" + truncated);

        DataSet result = new DataSet(
                COLUMNS, rows, null, List.of(), List.of(),
                truncated ? originalCount : 0
        );
        log.debug("fetch() | return={} rows", rows.size());
        return result;
    }

    private List<NewsArticle> fetchArticles(DataRequest request) {
        DataQueryPlan plan = request.plan();
        if (plan != null && plan.dateFrom() != null && plan.dateTo() != null) {
            Instant from = plan.dateFrom().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = plan.dateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return articlePort.fetchArticlesByDateRange(from, to);
        }
        if (plan != null && plan.keywords() != null && !plan.keywords().isEmpty()) {
            String topic = plan.keywords().get(0);
            return articlePort.fetchArticles(topic, request.rowLimit());
        }
        String keyword = request.parameters().getOrDefault("keyword", "");
        int days = 30;
        try {
            days = Integer.parseInt(request.parameters().getOrDefault("days", "30"));
        } catch (NumberFormatException ignored) {}
        if (!keyword.isBlank()) {
            return articlePort.fetchArticles(keyword, request.rowLimit());
        }
        return articlePort.fetchRecentArticles(days);
    }

    private List<String> toRow(NewsArticle a) {
        return List.of(
                safe(a.title()),
                a.url() != null ? a.url().toString() : "",
                safe(a.sourceName()),
                a.publishedAt() != null ? a.publishedAt().toString() : "",
                truncate(a.bodyText())
        );
    }

    private String truncate(String text) {
        if (text == null || text.isBlank()) return "";
        if (text.length() <= SUMMARY_MAX) return text;
        int lastEnd = Math.max(text.lastIndexOf('.', SUMMARY_MAX),
                Math.max(text.lastIndexOf('?', SUMMARY_MAX), text.lastIndexOf('!', SUMMARY_MAX)));
        if (lastEnd > 0) return text.substring(0, lastEnd + 1);
        return text.substring(0, SUMMARY_MAX);
    }

    private String safe(String v) {
        return v != null ? v : "";
    }
}
