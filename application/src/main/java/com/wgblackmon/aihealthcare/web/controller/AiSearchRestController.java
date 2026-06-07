package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.web.dto.AiSearchResponse;
import com.wgblackmon.aihealthcare.web.dto.AiSearchSynthesisDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST controller exposing the AI-enhanced search endpoint.
 *
 * <p>Provides {@code GET /api/v1/search/ai} which retrieves articles via
 * vector similarity and synthesizes them through multiple LLM models,
 * returning a JSON response with side-by-side model comparisons.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-02
 * @updated 2026-06-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
public class AiSearchRestController {

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;

    private final ConductAiSearchUseCase aiSearchUseCase;

    public AiSearchRestController(ConductAiSearchUseCase aiSearchUseCase) {
        log.debug("AiSearchRestController() | aiSearchUseCase={}", aiSearchUseCase.getClass().getSimpleName());
        this.aiSearchUseCase = aiSearchUseCase;
    }

    /**
     * Executes an AI-enhanced search and returns multi-model syntheses.
     *
     * @param q    the natural-language search query (required)
     * @param topK maximum number of articles to retrieve (default 10, max 50)
     * @return 200 OK with {@link AiSearchResponse}; 400 if query is missing
     */
    @GetMapping("/ai")
    public ResponseEntity<AiSearchResponse> aiSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) List<String> models) {
        log.debug("aiSearch() | q={}, topK={}, models={}", q, topK, models);

        if (q == null || q.isBlank()) {
            log.warn("aiSearch() | missing or blank query parameter");
            return ResponseEntity.badRequest().build();
        }

        int resolvedTopK = resolveTopK(topK);
        AiSearchResult result = aiSearchUseCase.search(q.trim(), resolvedTopK, models);

        List<AiSearchSynthesisDto> synthesisDtos = new ArrayList<>();
        for (AiSearchSynthesis synthesis : result.syntheses()) {
            synthesisDtos.add(new AiSearchSynthesisDto(
                    synthesis.modelName(),
                    synthesis.summary(),
                    synthesis.keyFindings()));
        }

        AiSearchResponse response = new AiSearchResponse(
                result.searchId(),
                result.query(),
                result.articles().size(),
                synthesisDtos,
                result.searchedAt());

        log.debug("aiSearch() | return=AiSearchResponse[articles={}, syntheses={}]",
                  response.articleCount(), response.syntheses().size());
        return ResponseEntity.ok(response);
    }

    private int resolveTopK(Integer topK) {
        log.debug("resolveTopK() | topK={}", topK);
        if (topK == null || topK < 1) {
            log.debug("resolveTopK() | return={} (default)", DEFAULT_TOP_K);
            return DEFAULT_TOP_K;
        }
        int result = Math.min(topK, MAX_TOP_K);
        log.debug("resolveTopK() | return={}", result);
        return result;
    }
}
