package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link NewsletterRunController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@WebMvcTest(NewsletterRunController.class)
@Import(SecurityConfig.class)
@WithMockUser
class NewsletterRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsletterRunPort newsletterRunPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void listRuns_returnsAllRuns() throws Exception {
        NewsletterRun run = new NewsletterRun("run-1", "Weekly Edition",
                LocalDate.of(2026, 8, 1), "<h1>HTML</h1>", "Plain text",
                NewsletterRunStatus.DRAFT, Instant.now());
        when(newsletterRunPort.findAll()).thenReturn(List.of(run));

        mockMvc.perform(get("/api/v1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value("run-1"))
                .andExpect(jsonPath("$[0].title").value("Weekly Edition"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void listRuns_emptyList() throws Exception {
        when(newsletterRunPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRunDetail_existingRun() throws Exception {
        NewsletterRun run = new NewsletterRun("run-2", "AI Health Weekly",
                LocalDate.of(2026, 8, 6), "<div>Content</div>", "Content",
                NewsletterRunStatus.SENT, Instant.now());
        when(newsletterRunPort.findByRunId("run-2")).thenReturn(run);

        mockMvc.perform(get("/api/v1/runs/run-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-2"))
                .andExpect(jsonPath("$.htmlContent").value("<div>Content</div>"))
                .andExpect(jsonPath("$.plainTextContent").value("Content"))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void listRuns_multipleRuns_returnsAll() throws Exception {
        NewsletterRun run1 = new NewsletterRun("run-a", "Edition A",
                LocalDate.of(2026, 7, 1), "<p>A</p>", "A",
                NewsletterRunStatus.SENT, Instant.now());
        NewsletterRun run2 = new NewsletterRun("run-b", "Edition B",
                LocalDate.of(2026, 8, 1), "<p>B</p>", "B",
                NewsletterRunStatus.DRAFT, Instant.now());
        when(newsletterRunPort.findAll()).thenReturn(List.of(run1, run2));

        mockMvc.perform(get("/api/v1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].runId").value("run-a"))
                .andExpect(jsonPath("$[1].runId").value("run-b"));
    }
}
