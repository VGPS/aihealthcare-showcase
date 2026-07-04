package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import com.wgblackmon.aihealthcare.domain.port.inbound.EvaluatePromptsUseCase;
import com.wgblackmon.aihealthcare.web.dto.VariantRequest;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link PromptVariantController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {PromptVariantController.class, GlobalExceptionHandler.class})
class PromptVariantControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluatePromptsUseCase evaluatePromptsUseCase;

    private static final Instant NOW = Instant.parse("2026-04-18T00:00:00Z");

    private static final PromptVariant VARIANT = new PromptVariant(
            "summarize-v1", "Default V1",
            "Summarize {topic} articles.", "Baseline prompt", NOW);

    @Test
    void create_returnsCreated() throws Exception {
        VariantRequest request = new VariantRequest(
                "summarize-v1", "Default V1",
                "Summarize {topic} articles.", "Baseline prompt");

        when(evaluatePromptsUseCase.createVariant(
                "summarize-v1", "Default V1",
                "Summarize {topic} articles.", "Baseline prompt"))
                .thenReturn(VARIANT);

        mockMvc.perform(post("/api/v1/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variantId").value("summarize-v1"))
                .andExpect(jsonPath("$.name").value("Default V1"))
                .andExpect(jsonPath("$.templateText").value("Summarize {topic} articles."));
    }

    @Test
    void list_returnsAll() throws Exception {
        when(evaluatePromptsUseCase.listVariants()).thenReturn(List.of(VARIANT));

        mockMvc.perform(get("/api/v1/variants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].variantId").value("summarize-v1"));
    }

    @Test
    void get_returnsVariant() throws Exception {
        when(evaluatePromptsUseCase.getVariant("summarize-v1")).thenReturn(VARIANT);

        mockMvc.perform(get("/api/v1/variants/summarize-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value("summarize-v1"))
                .andExpect(jsonPath("$.name").value("Default V1"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(evaluatePromptsUseCase.getVariant("unknown"))
                .thenThrow(new PromptVariantNotFoundException("unknown"));

        mockMvc.perform(get("/api/v1/variants/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/variants/summarize-v1"))
                .andExpect(status().isNoContent());

        verify(evaluatePromptsUseCase).deleteVariant("summarize-v1");
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new PromptVariantNotFoundException("unknown"))
                .when(evaluatePromptsUseCase).deleteVariant("unknown");

        mockMvc.perform(delete("/api/v1/variants/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
