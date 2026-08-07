package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.SampleNewsletterRenderer;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests for {@link SampleNewsletterController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(controllers = SampleNewsletterController.class)
class SampleNewsletterControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;
    @MockitoBean
    private SampleNewsletterRenderer renderer;
    @MockitoBean
    private NewsletterDeliveryPort deliveryPort;
    @MockitoBean
    private SubscriberPort subscriberPort;

    private NewsletterRun sampleRun() {
        return new NewsletterRun("sample-2026-08-06",
                "Complimentary Issue: AI Healthcare Intelligence",
                LocalDate.of(2026, 8, 6),
                "<html><body>Sample</body></html>",
                "Sample plain text",
                NewsletterRunStatus.DRAFT,
                Instant.now());
    }

    @Test
    void preview_withArticles_returns200Html() throws Exception {
        when(renderer.buildSample()).thenReturn(Optional.of(sampleRun()));

        mockMvc.perform(get("/monitoring/sample-newsletter"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample")));
    }

    @Test
    void preview_noArticles_returns204() throws Exception {
        when(renderer.buildSample()).thenReturn(Optional.empty());

        mockMvc.perform(get("/monitoring/sample-newsletter"))
                .andExpect(status().isNoContent());
    }

    @Test
    void send_withArticles_sendsEmail() throws Exception {
        when(renderer.buildSample()).thenReturn(Optional.of(sampleRun()));
        when(subscriberPort.findByEmail("test@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/monitoring/sample-newsletter/send")
                        .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(deliveryPort).deliver(any(NewsletterRun.class), anyList());
    }

    @Test
    void send_existingSubscriber_usesExistingRecord() throws Exception {
        when(renderer.buildSample()).thenReturn(Optional.of(sampleRun()));
        Subscriber existing = new Subscriber("test@example.com", "Test", true,
                Instant.now(), SubscriptionTier.FREE, "tok", null, null);
        when(subscriberPort.findByEmail("test@example.com")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/monitoring/sample-newsletter/send")
                        .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        verify(deliveryPort).deliver(any(NewsletterRun.class), anyList());
    }

    @Test
    void send_noArticles_returns400() throws Exception {
        when(renderer.buildSample()).thenReturn(Optional.empty());

        mockMvc.perform(post("/monitoring/sample-newsletter/send")
                        .param("email", "test@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(deliveryPort, never()).deliver(any(), any());
    }
}
