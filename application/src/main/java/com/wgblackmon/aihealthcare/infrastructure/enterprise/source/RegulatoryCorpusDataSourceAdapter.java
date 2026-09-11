package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enterprise data source adapter for the regulatory events registry
 * (FDA 510(k), De Novo, CMS rules, etc.).
 *
 * <p>Delegates to {@link RegulatoryEventPort} and maps
 * {@link RegulatoryEvent} records into a tabular {@link DataSet}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class RegulatoryCorpusDataSourceAdapter implements EnterpriseDataSourcePort {

    static final String FEED_ID = "regulatory";

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("title", "Title", "STRING"),
            new DataColumn("eventType", "Type", "STRING"),
            new DataColumn("summary", "Summary", "STRING"),
            new DataColumn("referenceNumber", "Reference #", "STRING"),
            new DataColumn("applicantName", "Applicant", "STRING"),
            new DataColumn("deviceName", "Device", "STRING"),
            new DataColumn("sourceUrl", "Source URL", "URI"),
            new DataColumn("publishedAt", "Published", "DATE")
    );

    private final RegulatoryEventPort regulatoryPort;

    public RegulatoryCorpusDataSourceAdapter(RegulatoryEventPort regulatoryPort) {
        this.regulatoryPort = regulatoryPort;
        log.debug("RegulatoryCorpusDataSourceAdapter()");
    }

    @Override
    public String feedId() { return FEED_ID; }

    @Override
    public DataSourceKind kind() { return DataSourceKind.INTERNAL_CORPUS; }

    @Override
    public DataFeed describe() {
        log.debug("describe()");
        DataFeed result = new DataFeed(
                FEED_ID, "Regulatory Events",
                "FDA and CMS regulatory events affecting AI in healthcare.",
                DataSourceKind.INTERNAL_CORPUS,
                List.of(ExportFormat.CSV, ExportFormat.JSON),
                List.of(
                        new DataParameter("body", "Regulatory body", "ENUM", false, null,
                                List.of("FDA", "CMS", "ONC", "OTHER")),
                        new DataParameter("keyword", "Keyword filter", "STRING", false, null, List.of())
                ),
                100, 5000, "CATEGORY_BAR", true
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
        jobLog.phase("FETCH_START", "feed=regulatory rowLimit=" + request.rowLimit());

        List<RegulatoryEvent> events = fetchEvents(request);
        int originalCount = events.size();
        boolean truncated = events.size() > request.rowLimit();
        if (truncated) {
            events = events.subList(0, request.rowLimit());
        }

        List<List<String>> rows = events.stream()
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

    private List<RegulatoryEvent> fetchEvents(DataRequest request) {
        DataQueryPlan plan = request.plan();
        if (plan != null && plan.keywords() != null && !plan.keywords().isEmpty()) {
            return regulatoryPort.findByKeyword(plan.keywords().get(0), request.rowLimit());
        }
        if (plan != null && plan.categories() != null && !plan.categories().isEmpty()) {
            try {
                RegulatoryBody body = RegulatoryBody.valueOf(plan.categories().get(0));
                return regulatoryPort.findByBody(body, request.rowLimit());
            } catch (IllegalArgumentException ignored) {}
        }
        String bodyParam = request.parameters().getOrDefault("body", "");
        if (!bodyParam.isBlank()) {
            try {
                RegulatoryBody body = RegulatoryBody.valueOf(bodyParam);
                return regulatoryPort.findByBody(body, request.rowLimit());
            } catch (IllegalArgumentException ignored) {}
        }
        String keywordParam = request.parameters().getOrDefault("keyword", "");
        if (!keywordParam.isBlank()) {
            return regulatoryPort.findByKeyword(keywordParam, request.rowLimit());
        }
        return regulatoryPort.findRecent(request.rowLimit());
    }

    private List<String> toRow(RegulatoryEvent e) {
        return List.of(
                safe(e.title()),
                e.eventType() != null ? e.eventType().name() : "",
                safe(e.summary()),
                safe(e.referenceNumber()),
                safe(e.applicantName()),
                safe(e.deviceName()),
                safe(e.sourceUrl()),
                e.publishedAt() != null ? e.publishedAt().toString() : ""
        );
    }

    private String safe(String v) {
        return v != null ? v : "";
    }
}
