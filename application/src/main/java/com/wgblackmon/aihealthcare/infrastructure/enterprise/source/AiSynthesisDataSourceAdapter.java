package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise data source adapter for LLM-synthesised market intelligence.
 *
 * <p>Delegates to {@link ConductAiSearchUseCase} and maps the
 * {@link AiSearchResult} into a {@link DataSet} with narrative, citations,
 * and source article rows.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class AiSynthesisDataSourceAdapter implements EnterpriseDataSourcePort {

    static final String FEED_ID = "ai-synthesis";

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("title", "Title", "STRING"),
            new DataColumn("url", "URL", "URI"),
            new DataColumn("publishedAt", "Published", "DATE")
    );

    private final ConductAiSearchUseCase aiSearchUseCase;

    public AiSynthesisDataSourceAdapter(ConductAiSearchUseCase aiSearchUseCase) {
        this.aiSearchUseCase = aiSearchUseCase;
        log.debug("AiSynthesisDataSourceAdapter()");
    }

    @Override
    public String feedId() { return FEED_ID; }

    @Override
    public DataSourceKind kind() { return DataSourceKind.LLM_SYNTHESIS; }

    @Override
    public DataFeed describe() {
        log.debug("describe()");
        DataFeed result = new DataFeed(
                FEED_ID, "AI Market Brief",
                "LLM-synthesised market intelligence on AI healthcare topics.",
                DataSourceKind.LLM_SYNTHESIS,
                List.of(ExportFormat.JSON, ExportFormat.CSV),
                List.of(
                        new DataParameter("topic", "Topic", "STRING", true, null, List.of())
                ),
                20, 100, "NONE", true
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
        String query = resolveQuery(request);
        jobLog.phase("FETCH_START", "feed=ai-synthesis query=" + query);

        AiSearchResult searchResult = aiSearchUseCase.search(query, request.rowLimit());

        StringBuilder narrative = new StringBuilder();
        for (AiSearchSynthesis synthesis : searchResult.syntheses()) {
            if (!narrative.isEmpty()) {
                narrative.append("\n\n---\n\n");
            }
            narrative.append("## ").append(synthesis.modelName()).append("\n\n");
            narrative.append(synthesis.summary());
            if (synthesis.keyFindings() != null && !synthesis.keyFindings().isEmpty()) {
                narrative.append("\n\n**Key findings:**\n");
                for (String finding : synthesis.keyFindings()) {
                    narrative.append("- ").append(finding).append("\n");
                }
            }
        }

        List<NewsArticle> articles = searchResult.articles();
        int originalCount = articles.size();
        boolean truncated = articles.size() > request.rowLimit();
        if (truncated) {
            articles = articles.subList(0, request.rowLimit());
        }

        List<List<String>> rows = articles.stream()
                .map(this::toRow)
                .toList();

        List<SourceCitation> citations = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            citations.add(new SourceCitation(
                    i + 1,
                    a.title(),
                    a.url() != null ? a.url().toString() : "",
                    a.publishedAt()
            ));
        }

        jobLog.phase("FETCH_END", "rows=" + rows.size() + " syntheses=" + searchResult.syntheses().size());

        DataSet result = new DataSet(
                COLUMNS, rows, narrative.toString(), citations, List.of(),
                truncated ? originalCount : 0
        );
        log.debug("fetch() | return={} rows, narrative={} chars", rows.size(), narrative.length());
        return result;
    }

    private String resolveQuery(DataRequest request) {
        DataQueryPlan plan = request.plan();
        if (plan != null && plan.keywords() != null && !plan.keywords().isEmpty()) {
            return String.join(" ", plan.keywords());
        }
        String topic = request.parameters().getOrDefault("topic", "");
        if (!topic.isBlank()) {
            return topic;
        }
        if (request.promptText() != null && !request.promptText().isBlank()) {
            return request.promptText();
        }
        return "AI in healthcare";
    }

    private List<String> toRow(NewsArticle a) {
        return List.of(
                safe(a.title()),
                a.url() != null ? a.url().toString() : "",
                a.publishedAt() != null ? a.publishedAt().toString() : ""
        );
    }

    private String safe(String v) {
        return v != null ? v : "";
    }
}
