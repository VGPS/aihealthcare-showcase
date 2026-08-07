package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LintReport;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LintReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link WikiLintController}.
 *
 * <p>Verifies the manual wiki lint trigger and latest-report retrieval
 * endpoints return correct JSON responses.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(WikiLintController.class)
class WikiLintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private WikiLintService lintService;

    @MockBean
    private WikiQueryPort wikiQueryPort;

    @MockBean
    private LintReportPort lintReportPort;

    private static final Instant STARTED = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-05T10:01:00Z");

    @Test
    void triggerLint_returnsReport() throws Exception {
        WikiPage page = new WikiPage("test-page", "Test Page", WikiPageType.ENTITY,
                List.of("tag"), "content", List.of(), List.of(),
                Instant.now(), null, 1);
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of(page));

        LintReport report = new LintReport(
                STARTED, COMPLETED, 1,
                List.of("test-page"), List.of(), List.of(), List.of("no-sources"), List.of());
        when(lintService.lint(anyList(), anyInt())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPagesChecked").value(1))
                .andExpect(jsonPath("$.orphanedSlugs[0]").value("test-page"))
                .andExpect(jsonPath("$.missingProvenance[0]").value("no-sources"))
                .andExpect(jsonPath("$.totalIssues").value(2));

        verify(lintReportPort).save(report);
    }

    @Test
    void triggerLint_emptyWiki_returnsCleanReport() throws Exception {
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of());

        LintReport report = new LintReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
        when(lintService.lint(anyList(), anyInt())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPagesChecked").value(0))
                .andExpect(jsonPath("$.totalIssues").value(0));
    }

    @Test
    void triggerLint_withBrokenRefs_returnsDetails() throws Exception {
        when(wikiQueryPort.findRelevantPages("", 10000)).thenReturn(List.of());

        LintReport report = new LintReport(
                STARTED, COMPLETED, 5,
                List.of(), List.of("page-a -> ghost"), List.of("stale-page"), List.of(), List.of());
        when(lintService.lint(anyList(), anyInt())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokenRefs[0]").value("page-a -> ghost"))
                .andExpect(jsonPath("$.staleSlugs[0]").value("stale-page"))
                .andExpect(jsonPath("$.totalIssues").value(2));
    }

    @Test
    void getLatest_returnsLatestReport() throws Exception {
        LintReport report = new LintReport(
                STARTED, COMPLETED, 10,
                List.of("orphan"), List.of(), List.of(), List.of(), List.of());
        when(lintReportPort.findLatest()).thenReturn(report);

        mockMvc.perform(get("/monitoring/wiki/lint/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPagesChecked").value(10))
                .andExpect(jsonPath("$.orphanedSlugs[0]").value("orphan"))
                .andExpect(jsonPath("$.totalIssues").value(1));
    }

    @Test
    void getLatest_noReports_returns404() throws Exception {
        when(lintReportPort.findLatest()).thenReturn(null);

        mockMvc.perform(get("/monitoring/wiki/lint/latest"))
                .andExpect(status().isNotFound());
    }
}
