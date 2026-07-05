package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link LintReportPort}.
 *
 * <p>Persists wiki lint reports to the {@code lint_reports} table.
 * Slug lists and broken-ref entries are stored as pipe-delimited strings,
 * matching the project's existing pattern (see {@link CompilationReportAdapter}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Slf4j
@Component
public class LintReportAdapter implements LintReportPort {

    private final LintReportRepository repository;

    public LintReportAdapter(LintReportRepository repository) {
        log.debug("LintReportAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(LintReport report) {
        log.debug("save() | totalPagesChecked={}, orphans={}, brokenRefs={}, stale={}, missingProv={}",
                report.totalPagesChecked(), report.orphanedSlugs().size(),
                report.brokenRefs().size(), report.staleSlugs().size(),
                report.missingProvenance().size());

        LintReportEntity entity = toEntity(report);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<LintReport> findAll() {
        log.debug("findAll() | (no args)");

        List<LintReportEntity> entities = repository.findAllByOrderByRunStartedAtDesc();
        List<LintReport> results = new ArrayList<>();
        for (LintReportEntity entity : entities) {
            results.add(toDomain(entity));
        }

        log.debug("findAll() | return={} reports", results.size());
        return results;
    }

    @Override
    public LintReport findLatest() {
        log.debug("findLatest() | (no args)");

        LintReportEntity entity = repository.findFirstByOrderByRunStartedAtDesc();
        if (entity == null) {
            log.debug("findLatest() | return=null");
            return null;
        }

        LintReport result = toDomain(entity);
        log.debug("findLatest() | return=report (totalPages={})", result.totalPagesChecked());
        return result;
    }

    private LintReportEntity toEntity(LintReport report) {
        log.debug("toEntity() | runStartedAt={}", report.runStartedAt());

        LintReportEntity entity = new LintReportEntity();
        entity.setRunStartedAt(report.runStartedAt());
        entity.setRunCompletedAt(report.runCompletedAt());
        entity.setTotalPagesChecked(report.totalPagesChecked());
        entity.setOrphanedSlugs(joinPipeDelimited(report.orphanedSlugs()));
        entity.setBrokenRefs(joinPipeDelimited(report.brokenRefs()));
        entity.setStaleSlugs(joinPipeDelimited(report.staleSlugs()));
        entity.setMissingProvenance(joinPipeDelimited(report.missingProvenance()));
        entity.setWarnings(joinPipeDelimited(report.warnings()));

        log.debug("toEntity() | return=entity");
        return entity;
    }

    private LintReport toDomain(LintReportEntity entity) {
        log.debug("toDomain() | id={}", entity.getId());

        LintReport result = new LintReport(
                entity.getRunStartedAt(),
                entity.getRunCompletedAt(),
                entity.getTotalPagesChecked(),
                splitPipeDelimited(entity.getOrphanedSlugs()),
                splitPipeDelimited(entity.getBrokenRefs()),
                splitPipeDelimited(entity.getStaleSlugs()),
                splitPipeDelimited(entity.getMissingProvenance()),
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
