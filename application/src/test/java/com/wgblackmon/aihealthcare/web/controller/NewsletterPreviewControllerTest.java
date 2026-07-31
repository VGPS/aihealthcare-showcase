package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link NewsletterPreviewController}.
 *
 * <p>All port calls are mocked — no real persistence or delivery occurs.
 * Newsletter preview pages require the ADMIN role (Slice 32).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-18
 * @updated 2026-05-30
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(NewsletterPreviewController.class)
class NewsletterPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private NewsletterRunPort newsletterRunPort;

    @MockitoBean
    private DeliverNewsletterUseCase deliverUseCase;

    private static final NewsletterRun SAMPLE_RUN = new NewsletterRun(
            "run-001",
            "AI Healthcare Weekly",
            LocalDate.of(2026, 5, 18),
            "<h1>Hello</h1><p>Newsletter content</p>",
            "Hello\nNewsletter content",
            NewsletterRunStatus.DRAFT,
            Instant.parse("2026-05-18T10:00:00Z")
    );

    @Test
    void listRuns_returnsRunListPage() throws Exception {
        when(newsletterRunPort.findAll()).thenReturn(List.of(SAMPLE_RUN));

        mockMvc.perform(get("/newsletter/runs"))
                .andExpect(status().isOk())
                .andExpect(view().name("newsletter-runs"))
                .andExpect(model().attributeExists("runs"))
                .andExpect(model().attributeExists("runTimestamps"));
    }

    @Test
    void editRun_returnsEditPage() throws Exception {
        when(newsletterRunPort.findByRunId("run-001")).thenReturn(SAMPLE_RUN);

        mockMvc.perform(get("/newsletter/runs/run-001/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("newsletter-edit"))
                .andExpect(model().attributeExists("run"))
                .andExpect(model().attributeExists("htmlContent"));
    }

    @Test
    void editRun_notFound_returns404() throws Exception {
        when(newsletterRunPort.findByRunId("unknown"))
                .thenThrow(new RunNotFoundException("unknown"));

        mockMvc.perform(get("/newsletter/runs/unknown/edit"))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveDraft_updatesAndRedirects() throws Exception {
        when(newsletterRunPort.findByRunId("run-001")).thenReturn(SAMPLE_RUN);

        mockMvc.perform(post("/newsletter/runs/run-001/save")
                        .with(csrf())
                        .param("htmlContent", "<h1>Edited</h1><p>New content</p>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/newsletter/runs/run-001/edit?saved=true"));

        verify(newsletterRunPort).save(any(NewsletterRun.class));
    }

    @Test
    void sendNewsletter_deliversAndRedirectsWithCount() throws Exception {
        when(deliverUseCase.deliver("run-001")).thenReturn(12);

        mockMvc.perform(post("/newsletter/runs/run-001/send").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/newsletter/runs?sent=true&count=12"));

        verify(deliverUseCase).deliver("run-001");
    }

    @Test
    void sendNewsletter_error_redirectsWithErrorMessage() throws Exception {
        when(deliverUseCase.deliver("unknown"))
                .thenThrow(new RunNotFoundException("unknown"));

        mockMvc.perform(post("/newsletter/runs/unknown/send").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listRuns_asUser_returns403() throws Exception {
        mockMvc.perform(get("/newsletter/runs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void editRun_asUser_returns403() throws Exception {
        mockMvc.perform(get("/newsletter/runs/run-001/edit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void saveDraft_asUser_returns403() throws Exception {
        mockMvc.perform(post("/newsletter/runs/run-001/save")
                        .with(csrf())
                        .param("htmlContent", "<h1>Edited</h1>"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void sendNewsletter_asUser_returns403() throws Exception {
        mockMvc.perform(post("/newsletter/runs/run-001/send").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
