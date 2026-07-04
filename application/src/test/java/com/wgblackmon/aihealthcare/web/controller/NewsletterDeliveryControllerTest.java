package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.web.dto.DeliverRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link NewsletterDeliveryController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {NewsletterDeliveryController.class, GlobalExceptionHandler.class})
class NewsletterDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeliverNewsletterUseCase deliverNewsletterUseCase;

    private static final String RUN_ID = "run-001";

    @Test
    void deliver_returns204OnSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/newsletter/deliver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliverRequest(RUN_ID))))
                .andExpect(status().isNoContent());

        verify(deliverNewsletterUseCase).deliver(RUN_ID);
    }

    @Test
    void deliver_runNotFound_returns404() throws Exception {
        doThrow(new RunNotFoundException(RUN_ID))
                .when(deliverNewsletterUseCase).deliver(RUN_ID);

        mockMvc.perform(post("/api/v1/newsletter/deliver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliverRequest(RUN_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
