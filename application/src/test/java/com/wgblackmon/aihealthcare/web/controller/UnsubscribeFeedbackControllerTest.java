package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link UnsubscribeFeedbackController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(controllers = UnsubscribeFeedbackController.class)
class UnsubscribeFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    void recordFeedback_withReason_returnsRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/unsubscribe")
                        .param("reason", "too_many_emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));
    }

    @Test
    void recordFeedback_noReason_returnsRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/unsubscribe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));
    }

    @Test
    void recordFeedback_blankReason_returnsRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/unsubscribe")
                        .param("reason", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));
    }
}
