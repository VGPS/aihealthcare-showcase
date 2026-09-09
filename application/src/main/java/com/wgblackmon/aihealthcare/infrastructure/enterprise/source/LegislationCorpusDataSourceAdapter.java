package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enterprise data source adapter for the state health-AI legislation registry.
 *
 * <p>Delegates to {@link StateLawPort} and maps {@link StateLaw} records
 * into a tabular {@link DataSet}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class LegislationCorpusDataSourceAdapter implements EnterpriseDataSourcePort {

    static final String FEED_ID = "legislation";

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("id", "Law ID", "STRING"),
            new DataColumn("stateCode", "State", "STRING"),
            new DataColumn("stateName", "State Name", "STRING"),
            new DataColumn("billNumber", "Bill Number", "STRING"),
            new DataColumn("title", "Title", "STRING"),
            new DataColumn("yearEnacted", "Year Enacted", "INTEGER"),
            new DataColumn("effectiveDate", "Effective Date", "DATE"),
            new DataColumn("status", "Status", "STRING"),
            new DataColumn("categories", "Categories", "STRING"),
            new DataColumn("keyRequirements", "Key Requirements", "STRING"),
            new DataColumn("enforcement", "Enforcement", "STRING")
    );

    private final StateLawPort stateLawPort;

    public LegislationCorpusDataSourceAdapter(StateLawPort stateLawPort) {
        this.stateLawPort = stateLawPort;
        log.debug("LegislationCorpusDataSourceAdapter()");
    }

    @Override
    public String feedId() { return FEED_ID; }

    @Override
    public DataSourceKind kind() { return DataSourceKind.INTERNAL_CORPUS; }

    @Override
    public DataFeed describe() {
        log.debug("describe()");
        DataFeed result = new DataFeed(
                FEED_ID, "State Health-AI Legislation",
                "Enacted U.S. state laws regulating AI in healthcare.",
                DataSourceKind.INTERNAL_CORPUS,
                List.of(ExportFormat.CSV, ExportFormat.JSON),
                List.of(
                        new DataParameter("state", "State code", "ENUM", false, null,
                                List.of("AL","AK","AZ","AR","CA","CO","CT","DC","DE","FL","GA","HI",
                                        "ID","IL","IN","IA","KS","KY","LA","ME","MD","MA","MI","MN",
                                        "MS","MO","MT","NE","NV","NH","NJ","NM","NY","NC","ND","OH",
                                        "OK","OR","PA","RI","SC","SD","TN","TX","UT","VT","VA","WA",
                                        "WV","WI","WY")),
                        new DataParameter("category", "Law category", "ENUM", false, null,
                                List.of("PAYER_UTILIZATION_REVIEW","CLAIMS_DOWNCODING",
                                        "PROVIDER_CLINICAL_USE","PROVIDER_DISCLOSURE_CONSENT",
                                        "MENTAL_HEALTH_PSYCHOTHERAPY","CONSUMER_CHATBOTS",
                                        "COMPREHENSIVE_AI_ACT","OTHER"))
                ),
                50, 500, "CATEGORY_BAR", true
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
        jobLog.phase("FETCH_START", "feed=legislation rowLimit=" + request.rowLimit());

        List<StateLaw> laws = fetchLaws(request);
        int originalCount = laws.size();
        boolean truncated = laws.size() > request.rowLimit();
        if (truncated) {
            laws = laws.subList(0, request.rowLimit());
        }

        List<List<String>> rows = laws.stream()
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

    private List<StateLaw> fetchLaws(DataRequest request) {
        DataQueryPlan plan = request.plan();
        if (plan != null && plan.states() != null && !plan.states().isEmpty()) {
            try {
                StateCode code = StateCode.valueOf(plan.states().get(0));
                return stateLawPort.findByState(code);
            } catch (IllegalArgumentException ignored) {}
        }
        if (plan != null && plan.categories() != null && !plan.categories().isEmpty()) {
            try {
                LawCategory cat = LawCategory.valueOf(plan.categories().get(0));
                return stateLawPort.findByCategory(cat);
            } catch (IllegalArgumentException ignored) {}
        }
        if (plan != null && plan.keywords() != null && !plan.keywords().isEmpty()) {
            return stateLawPort.search(plan.keywords().get(0));
        }
        String stateParam = request.parameters().getOrDefault("state", "");
        if (!stateParam.isBlank()) {
            try {
                StateCode code = StateCode.valueOf(stateParam);
                return stateLawPort.findByState(code);
            } catch (IllegalArgumentException ignored) {}
        }
        String catParam = request.parameters().getOrDefault("category", "");
        if (!catParam.isBlank()) {
            try {
                LawCategory cat = LawCategory.valueOf(catParam);
                return stateLawPort.findByCategory(cat);
            } catch (IllegalArgumentException ignored) {}
        }
        return stateLawPort.findAll();
    }

    private List<String> toRow(StateLaw law) {
        return List.of(
                safe(law.id()),
                law.stateCode() != null ? law.stateCode().name() : "",
                safe(law.stateName()),
                safe(law.billNumber()),
                safe(law.title()),
                String.valueOf(law.yearEnacted()),
                safe(law.effectiveDate()),
                law.status() != null ? law.status().name() : "",
                law.categories() != null
                        ? law.categories().stream().map(Enum::name).collect(Collectors.joining("|"))
                        : "",
                safe(law.keyRequirements()),
                safe(law.enforcement())
        );
    }

    private String safe(String v) {
        return v != null ? v : "";
    }
}
