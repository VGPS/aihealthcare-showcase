package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.web.dto.ResearchAnswerDto;
import com.wgblackmon.aihealthcare.web.dto.ResearchRequestDto;
import com.wgblackmon.aihealthcare.web.dto.ResearchSectionDto;
import com.wgblackmon.aihealthcare.web.dto.SourceCitationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller that exposes the research pipeline via {@code POST /api/v1/research}.
 *
 * <p>Validates and maps the incoming {@link ResearchRequestDto} to a domain
 * {@link ResearchRequest}, delegates to {@link ConductResearchUseCase}, and maps the
 * resulting {@link ResearchAnswer} back to a {@link ResearchAnswerDto} for the response.
 *
 * <p>Validation rules applied here:
 * <ul>
 *   <li>{@code query} must be non-blank; returns HTTP 400 otherwise.</li>
 *   <li>{@code mode} defaults to {@code LEGACY_GOOGLE} when absent or unrecognised.</li>
 *   <li>{@code maxSources} defaults to {@code 20} when absent; clamped to [1, 100].</li>
 * </ul>
 *
 * <p>The {@link com.wgblackmon.aihealthcare.web.GlobalExceptionHandler} catches
 * {@link IllegalArgumentException} and maps it to HTTP 400.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;

    private final ConductResearchUseCase conductResearchUseCase;

    /**
     * Constructs the controller with its inbound use-case port.
     *
     * @param conductResearchUseCase Use case that drives the research pipeline.
     */
    public ResearchController(ConductResearchUseCase conductResearchUseCase) {
        log.debug("ResearchController() | conductResearchUseCase={}",
                  conductResearchUseCase.getClass().getSimpleName());
        this.conductResearchUseCase = conductResearchUseCase;
        log.debug("ResearchController() | return=void");
    }

    /**
     * Conduct a research query and return a structured answer.
     *
     * @param dto Incoming request body; {@code query} is required.
     * @return HTTP 200 with a {@link ResearchAnswerDto}, or HTTP 400 if validation fails.
     */
    @PostMapping
    public ResponseEntity<ResearchAnswerDto> research(@RequestBody ResearchRequestDto dto) {
        log.debug("research() | dto={}", dto);

        if (dto == null || dto.query() == null || dto.query().isBlank()) {
            log.warn("research() | request rejected: query is blank");
            throw new IllegalArgumentException("query must not be blank");
        }

        ResearchMode mode = parseMode(dto.mode());
        int maxSources    = resolveMaxSources(dto.maxSources());

        ResearchRequest request = new ResearchRequest(
                dto.query().trim(),
                mode,
                dto.topicHint(),
                maxSources);

        log.info("research() | conducting research: query='{}', mode={}, maxSources={}",
                 request.query(), request.mode(), request.maxSources());

        ResearchAnswer answer = conductResearchUseCase.conduct(request);

        ResearchAnswerDto response = toDto(answer);
        log.info("research() | answer ready: answerId={}, sections={}",
                 response.answerId(), response.sections().size());
        log.debug("research() | return={}", response.answerId());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    /**
     * Parse the mode string, defaulting to LEGACY_GOOGLE when absent or unrecognised.
     */
    private ResearchMode parseMode(String modeStr) {
        log.debug("parseMode() | modeStr={}", modeStr);
        if (modeStr == null || modeStr.isBlank()) {
            log.debug("parseMode() | return=LEGACY_GOOGLE (default)");
            return ResearchMode.LEGACY_GOOGLE;
        }
        try {
            ResearchMode result = ResearchMode.valueOf(modeStr.toUpperCase().trim());
            log.debug("parseMode() | return={}", result);
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("parseMode() | Unrecognised mode '{}' — defaulting to LEGACY_GOOGLE", modeStr);
            return ResearchMode.LEGACY_GOOGLE;
        }
    }

    /** Apply default and cap to maxSources. */
    private int resolveMaxSources(Integer maxSources) {
        log.debug("resolveMaxSources() | maxSources={}", maxSources);
        if (maxSources == null || maxSources < 1) {
            log.debug("resolveMaxSources() | return={} (default)", DEFAULT_MAX_SOURCES);
            return DEFAULT_MAX_SOURCES;
        }
        int result = Math.min(maxSources, MAX_SOURCES_CAP);
        log.debug("resolveMaxSources() | return={}", result);
        return result;
    }

    /** Map a domain {@link ResearchAnswer} to a response DTO. */
    private ResearchAnswerDto toDto(ResearchAnswer answer) {
        log.debug("toDto() | answerId={}", answer.answerId());

        List<ResearchSectionDto> sectionDtos = new ArrayList<>();
        for (ResearchSection section : answer.sections()) {
            List<SourceCitationDto> citDtos = toCitationDtos(section.citations());
            sectionDtos.add(new ResearchSectionDto(section.heading(), section.body(), citDtos));
        }

        List<SourceCitationDto> allCitDtos = toCitationDtos(answer.allCitations());

        ResearchAnswerDto result = new ResearchAnswerDto(
                answer.answerId(),
                answer.query(),
                sectionDtos,
                allCitDtos,
                answer.generatedAt() != null ? answer.generatedAt().toString() : null);

        log.debug("toDto() | return=ResearchAnswerDto[answerId={}]", result.answerId());
        return result;
    }

    /** Map a list of domain citations to DTOs. */
    private List<SourceCitationDto> toCitationDtos(List<SourceCitation> citations) {
        log.debug("toCitationDtos() | count={}", citations == null ? 0 : citations.size());
        List<SourceCitationDto> result = new ArrayList<>();
        if (citations == null) {
            log.debug("toCitationDtos() | return=[] (null input)");
            return result;
        }
        for (SourceCitation c : citations) {
            result.add(new SourceCitationDto(
                    c.citationNumber(),
                    c.title(),
                    c.url(),
                    c.retrievedAt() != null ? c.retrievedAt().toString() : null));
        }
        log.debug("toCitationDtos() | return={} dtos", result.size());
        return result;
    }
}
