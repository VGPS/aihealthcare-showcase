package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled driver that runs the wiki linter at a configurable cadence.
 *
 * <p>Loads all wiki pages, runs the lint pass via {@link WikiLintService},
 * and persists the resulting {@link LintReport} for dashboard display.
 * Exceptions are caught so the scheduler thread survives failures.
 *
 * <p>Default schedule: 08:00 UTC daily (after compilation completes).
 * Configurable via {@code aihealthcare.wiki.lint-cron} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Slf4j
@Component
public class WikiLintScheduler {

    private final WikiLintService lintService;
    private final WikiQueryPort wikiQueryPort;
    private final LintReportPort lintReportPort;
    private final int stalenessThresholdDays;

    public WikiLintScheduler(WikiLintService lintService,
                              WikiQueryPort wikiQueryPort,
                              LintReportPort lintReportPort,
                              @Value("${aihealthcare.wiki.staleness-threshold-days:30}") int stalenessThresholdDays) {
        log.debug("WikiLintScheduler() | lintService={}, wikiQueryPort={}, lintReportPort={}, stalenessThresholdDays={}",
                lintService.getClass().getSimpleName(),
                wikiQueryPort.getClass().getSimpleName(),
                lintReportPort.getClass().getSimpleName(),
                stalenessThresholdDays);
        this.lintService = lintService;
        this.wikiQueryPort = wikiQueryPort;
        this.lintReportPort = lintReportPort;
        this.stalenessThresholdDays = stalenessThresholdDays;
    }

    /**
     * Runs the wiki lint pass on schedule.
     * Cron configured via {@code aihealthcare.wiki.lint-cron} (default: 08:00 UTC).
     */
    @Scheduled(cron = "${aihealthcare.wiki.lint-cron}", zone = "UTC")
    public void runScheduledLint() {
        log.debug("runScheduledLint() | starting scheduled wiki lint");
        try {
            List<WikiPage> allPages = wikiQueryPort.findRelevantPages("", 10000);
            log.info("runScheduledLint() | loaded {} wiki pages for linting", allPages.size());

            LintReport report = lintService.lint(allPages, stalenessThresholdDays);
            lintReportPort.save(report);

            int totalIssues = report.orphanedSlugs().size()
                    + report.brokenRefs().size()
                    + report.staleSlugs().size()
                    + report.missingProvenance().size();

            log.info("runScheduledLint() | lint complete — {} pages checked, {} issues found " +
                            "(orphans={}, brokenRefs={}, stale={}, missingProv={})",
                    report.totalPagesChecked(), totalIssues,
                    report.orphanedSlugs().size(), report.brokenRefs().size(),
                    report.staleSlugs().size(), report.missingProvenance().size());
        } catch (Exception e) {
            log.warn("runScheduledLint() | wiki lint failed — scheduler continues", e);
        }
        log.debug("runScheduledLint() | return=void");
    }
}
