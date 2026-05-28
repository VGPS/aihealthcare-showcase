package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.inbound.EvaluatePromptsUseCase;
import com.wgblackmon.aihealthcare.web.dto.CompareRequest;
import com.wgblackmon.aihealthcare.web.dto.EvaluateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link PromptEvaluationController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(controllers = {PromptEvaluationController.class, GlobalExceptionHandler.class})
class PromptEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluatePromptsUseCase evaluatePromptsUseCase;

    private static final Instant NOW = Instant.parse("2026-04-18T00:00:00Z");

    private static final NewsletterSection SECTION = new NewsletterSection(
            "sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Advances", "Summary of advances.", List.of("art-001"));

    private static final EvaluationScore SCORE = new EvaluationScore(
            0.85, 0.90, 0.75, 0.80, 0.70, 0.80, "Solid output");

    private static final EvaluationResult EVAL_1 = new EvaluationResult(
            "eval-001", "variant-a", "Variant A", List.of("art-001"),
            "AI Healthcare", NewsletterTone.PROFESSIONAL, SECTION, SCORE, NOW);

    private static final EvaluationResult EVAL_2 = new EvaluationResult(
            "eval-002", "variant-b", "Variant B", List.of("art-001"),
            "AI Healthcare", NewsletterTone.PROFESSIONAL, SECTION, SCORE, NOW);

    // -------------------------------------------------------------------------
    // Evaluation endpoints
    // -------------------------------------------------------------------------

    @Test
    void evaluate_returnsCreated() throws Exception {
        EvaluateRequest request = new EvaluateRequest(
                "variant-a", List.of("art-001"), "AI Healthcare", "PROFESSIONAL");

        when(evaluatePromptsUseCase.evaluate(
                eq("variant-a"), eq(List.of("art-001")),
                eq("AI Healthcare"), eq(NewsletterTone.PROFESSIONAL)))
                .thenReturn(EVAL_1);

        mockMvc.perform(post("/api/v1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationId").value("eval-001"))
                .andExpect(jsonPath("$.variantId").value("variant-a"))
                .andExpect(jsonPath("$.score.overall").value(0.80))
                .andExpect(jsonPath("$.section.headline").value("AI Advances"));
    }

    @Test
    void listEvaluations_returnsAll() throws Exception {
        when(evaluatePromptsUseCase.listEvaluations()).thenReturn(List.of(EVAL_1, EVAL_2));

        mockMvc.perform(get("/api/v1/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listEvaluations_byVariant_filtersCorrectly() throws Exception {
        when(evaluatePromptsUseCase.listEvaluationsByVariant("variant-a"))
                .thenReturn(List.of(EVAL_1));

        mockMvc.perform(get("/api/v1/evaluations").param("variantId", "variant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].variantId").value("variant-a"));
    }

    @Test
    void getEvaluation_returnsResult() throws Exception {
        when(evaluatePromptsUseCase.getEvaluation("eval-001")).thenReturn(EVAL_1);

        mockMvc.perform(get("/api/v1/evaluations/eval-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value("eval-001"))
                .andExpect(jsonPath("$.score.relevance").value(0.85));
    }

    @Test
    void getEvaluation_notFound_returns404() throws Exception {
        when(evaluatePromptsUseCase.getEvaluation("unknown"))
                .thenThrow(new EvaluationNotFoundException("unknown"));

        mockMvc.perform(get("/api/v1/evaluations/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // Comparison endpoints
    // -------------------------------------------------------------------------

    @Test
    void compare_returnsCreated() throws Exception {
        CompareRequest request = new CompareRequest(
                List.of("variant-a", "variant-b"),
                List.of("art-001"), "AI Healthcare", "PROFESSIONAL");

        ComparisonResult comparison = new ComparisonResult(
                "comp-001", List.of("art-001"), "AI Healthcare",
                NewsletterTone.PROFESSIONAL, List.of(EVAL_1, EVAL_2), NOW);

        when(evaluatePromptsUseCase.compare(
                eq(List.of("variant-a", "variant-b")),
                eq(List.of("art-001")),
                eq("AI Healthcare"),
                eq(NewsletterTone.PROFESSIONAL)))
                .thenReturn(comparison);

        mockMvc.perform(post("/api/v1/comparisons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comparisonId").value("comp-001"))
                .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    void listComparisons_returnsAll() throws Exception {
        ComparisonResult comparison = new ComparisonResult(
                "comp-001", List.of("art-001"), "AI Healthcare",
                NewsletterTone.PROFESSIONAL, List.of(EVAL_1, EVAL_2), NOW);

        when(evaluatePromptsUseCase.listComparisons()).thenReturn(List.of(comparison));

        mockMvc.perform(get("/api/v1/comparisons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getComparison_returnsResult() throws Exception {
        ComparisonResult comparison = new ComparisonResult(
                "comp-001", List.of("art-001"), "AI Healthcare",
                NewsletterTone.PROFESSIONAL, List.of(EVAL_1, EVAL_2), NOW);

        when(evaluatePromptsUseCase.getComparison("comp-001")).thenReturn(comparison);

        mockMvc.perform(get("/api/v1/comparisons/comp-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisonId").value("comp-001"))
                .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    void getComparison_notFound_returns404() throws Exception {
        when(evaluatePromptsUseCase.getComparison("unknown"))
                .thenThrow(new EvaluationNotFoundException("unknown"));

        mockMvc.perform(get("/api/v1/comparisons/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
