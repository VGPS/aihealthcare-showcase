package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompilationReportPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link CompilationReportPort}.
 *
 * <p>Persists wiki compilation reports to the {@code compilation_reports} table.
 * Slug lists ({@code pagesCreated}, {@code pagesUpdated}) and warnings are
 * stored as pipe-delimited strings, matching the project's existing pattern
 * (see {@link EvaluationResultAdapter}).
 *
 * <p>Note: The {@code contradictionsFlagged} list from the domain record is
 * represented only as a count in the entity, since contradictions are persisted
 * separately in the {@code wiki_contradictions} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Slf4j
@Component
public class CompilationReportAdapter implements CompilationReportPort {

    private final CompilationReportRepository repository;

    public CompilationReportAdapter(CompilationReportRepository repository) {
        log.debug("CompilationReportAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(CompilationReport report) {
        log.debug("save() | articlesProcessed={}, pagesCreated={}, pagesUpdated={}",
                report.articlesProcessed(), report.pagesCreated().size(), report.pagesUpdated().size());

        CompilationReportEntity entity = toEntity(report);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<CompilationReport> findAll() {
        log.debug("findAll() | (no args)");

        List<CompilationReportEntity> entities = repository.findAllByOrderByRunStartedAtDesc();
        List<CompilationReport> results = new ArrayList<>();
        for (CompilationReportEntity entity : entities) {
            results.add(toDomain(entity));
        }

        log.debug("findAll() | return={} reports", results.size());
        return results;
    }

    private CompilationReportEntity toEntity(CompilationReport report) {
        log.debug("toEntity() | runStartedAt={}", report.runStartedAt());

        CompilationReportEntity entity = new CompilationReportEntity();
        entity.setRunStartedAt(report.runStartedAt());
        entity.setRunCompletedAt(report.runCompletedAt());
        entity.setArticlesProcessed(report.articlesProcessed());
        entity.setPagesCreated(joinPipeDelimited(report.pagesCreated()));
        entity.setPagesUpdated(joinPipeDelimited(report.pagesUpdated()));
        entity.setContradictionCount(report.contradictionsFlagged().size());
        entity.setWarnings(joinPipeDelimited(report.warnings()));

        log.debug("toEntity() | return=entity");
        return entity;
    }

    private CompilationReport toDomain(CompilationReportEntity entity) {
        log.debug("toDomain() | id={}", entity.getId());

        CompilationReport result = new CompilationReport(
                entity.getRunStartedAt(),
                entity.getRunCompletedAt(),
                entity.getArticlesProcessed(),
                splitPipeDelimited(entity.getPagesCreated()),
                splitPipeDelimited(entity.getPagesUpdated()),
                List.of(),  // contradictions stored separately; not re-joined here
                splitPipeDelimited(entity.getWarnings())
        );

        log.debug("toDomain() | return=report");
        return result;
    }

    private String joinPipeDelimited(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private List<String> splitPipeDelimited(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        String[] parts = value.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
