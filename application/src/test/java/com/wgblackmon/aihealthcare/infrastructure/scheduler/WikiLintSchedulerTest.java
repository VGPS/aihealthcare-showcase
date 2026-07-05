package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WikiLintScheduler}.
 *
 * <p>Verifies that the scheduler loads pages, delegates to the lint service,
 * and persists the resulting report. Also verifies graceful exception handling.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
class WikiLintSchedulerTest {

    private WikiLintService lintService;
    private WikiQueryPort wikiQueryPort;
    private LintReportPort lintReportPort;
    private WikiLintScheduler scheduler;

    @BeforeEach
    void setUp() {
        lintService = mock(WikiLintService.class);
        wikiQueryPort = mock(WikiQueryPort.class);
        lintReportPort = mock(LintReportPort.class);
        scheduler = new WikiLintScheduler(lintService, wikiQueryPort, lintReportPort, 30);
    }

    @Test
    void runScheduledLint_loadsPages_runsLint_savesReport() {
        WikiPage page = new WikiPage("test-page", "Test Page", WikiPageType.ENTITY,
                List.of("tag"), "content", List.of(), List.of(),
                Instant.now(), null, 1);
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of(page));

        LintReport report = new LintReport(
                Instant.now(), Instant.now(), 1,
                List.of("test-page"), List.of(), List.of(), List.of(), List.of());
        when(lintService.lint(anyList(), eq(30))).thenReturn(report);

        scheduler.runScheduledLint();

        verify(wikiQueryPort).findRelevantPages("", 10000);
        verify(lintService).lint(anyList(), eq(30));
        verify(lintReportPort).save(report);
    }

    @Test
    void runScheduledLint_emptyWiki_stillSavesReport() {
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of());

        LintReport report = new LintReport(
                Instant.now(), Instant.now(), 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
        when(lintService.lint(anyList(), eq(30))).thenReturn(report);

        scheduler.runScheduledLint();

        verify(lintReportPort).save(report);
    }

    @Test
    void runScheduledLint_exceptionCaught_schedulerSurvives() {
        when(wikiQueryPort.findRelevantPages("", 10000))
                .thenThrow(new RuntimeException("DB down"));

        // Should not throw
        scheduler.runScheduledLint();

        verify(lintReportPort, never()).save(any());
    }

    @Test
    void runScheduledLint_passesConfiguredThreshold() {
        WikiLintScheduler customScheduler = new WikiLintScheduler(
                lintService, wikiQueryPort, lintReportPort, 60);
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of());

        LintReport report = new LintReport(
                Instant.now(), Instant.now(), 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
        when(lintService.lint(anyList(), eq(60))).thenReturn(report);

        customScheduler.runScheduledLint();

        verify(lintService).lint(anyList(), eq(60));
    }
}
