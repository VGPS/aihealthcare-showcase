package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
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
 * @updated 2026-09-12
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
        entity.setOrphanedSlugs(PipeDelimitedUtils.join(report.orphanedSlugs()));
        entity.setBrokenRefs(PipeDelimitedUtils.join(report.brokenRefs()));
        entity.setStaleSlugs(PipeDelimitedUtils.join(report.staleSlugs()));
        entity.setMissingProvenance(PipeDelimitedUtils.join(report.missingProvenance()));
        entity.setWarnings(PipeDelimitedUtils.join(report.warnings()));

        log.debug("toEntity() | return=entity");
        return entity;
    }

    private LintReport toDomain(LintReportEntity entity) {
        log.debug("toDomain() | id={}", entity.getId());

        LintReport result = new LintReport(
                entity.getRunStartedAt(),
                entity.getRunCompletedAt(),
                entity.getTotalPagesChecked(),
                PipeDelimitedUtils.split(entity.getOrphanedSlugs()),
                PipeDelimitedUtils.split(entity.getBrokenRefs()),
                PipeDelimitedUtils.split(entity.getStaleSlugs()),
                PipeDelimitedUtils.split(entity.getMissingProvenance()),
                PipeDelimitedUtils.split(entity.getWarnings())
        );

        log.debug("toDomain() | return=report");
        return result;
    }

}
