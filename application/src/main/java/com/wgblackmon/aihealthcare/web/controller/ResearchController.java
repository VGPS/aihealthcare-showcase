package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.web.dto.ResearchAnswerDto;
import com.wgblackmon.aihealthcare.web.dto.ResearchRequestDto;
import com.wgblackmon.aihealthcare.web.dto.ResearchSectionDto;
import com.wgblackmon.aihealthcare.web.dto.SourceCitationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes the research pipeline via {@code POST /api/v1/research}.
 *
 * <p>Validates and maps the incoming {@link ResearchRequestDto} to a domain
 * {@link ResearchRequest}, delegates to {@link ConductResearchUseCase}, and maps the
 * resulting {@link ResearchAnswer} back to a {@link ResearchAnswerDto} for the response.
 *
 * <p>When the optional {@code X-Subscriber-Email} header is present, the controller
 * checks the subscriber's monthly AI query usage against their tier limit.  If the
 * limit is reached, HTTP 429 (Too Many Requests) is returned with usage details.
 * Without the header, requests are allowed anonymously (backwards-compatible).
 *
 * <p>Validation rules applied here:
 * <ul>
 *   <li>{@code query} must be non-blank; returns HTTP 400 otherwise.</li>
 *   <li>{@code mode} defaults to {@code LEGACY_GOOGLE} when absent or unrecognised.</li>
 *   <li>{@code maxSources} defaults to {@code 20} when absent; clamped to [1, 100].</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-04
 * @updated 2026-08-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;

    private final ConductResearchUseCase conductResearchUseCase;
    private final UsageTrackingPort      usageTrackingPort;
    private final TierGatingService      tierGatingService;

    /**
     * Constructs the controller with its inbound use-case port and usage-tracking
     * dependencies.
     *
     * @param conductResearchUseCase Use case that drives the research pipeline.
     * @param usageTrackingPort      Port for tracking per-subscriber query usage.
     * @param tierGatingService      Service for checking tier-based usage limits.
     */
    public ResearchController(ConductResearchUseCase conductResearchUseCase,
                              UsageTrackingPort usageTrackingPort,
                              TierGatingService tierGatingService) {
        log.debug("ResearchController() | conductResearchUseCase={}, usageTrackingPort={}, tierGatingService={}",
                  conductResearchUseCase.getClass().getSimpleName(),
                  usageTrackingPort.getClass().getSimpleName(),
                  tierGatingService.getClass().getSimpleName());
        this.conductResearchUseCase = conductResearchUseCase;
        this.usageTrackingPort      = usageTrackingPort;
        this.tierGatingService      = tierGatingService;
        log.debug("ResearchController() | return=void");
    }

    /**
     * Conduct a research query and return a structured answer.
     *
     * @param dto             Incoming request body; {@code query} is required.
     * @param subscriberEmail Optional subscriber email for usage metering.
     * @return HTTP 200 with a {@link ResearchAnswerDto}, HTTP 400 if validation fails,
     *         or HTTP 429 if the subscriber's monthly query limit is reached.
     */
    @PostMapping
    public ResponseEntity<?> research(
            @RequestBody ResearchRequestDto dto,
            @RequestHeader(value = "X-Subscriber-Email", required = false) String subscriberEmail) {
        log.debug("research() | dto={}, subscriberEmail={}", dto, subscriberEmail);

        if (dto == null || dto.query() == null || dto.query().isBlank()) {
            log.warn("research() | request rejected: query is blank");
            throw new IllegalArgumentException("query must not be blank");
        }

        // Usage gating — check monthly limit if subscriber email is provided
        if (subscriberEmail != null && !subscriberEmail.isBlank()) {
            String currentMonth = YearMonth.now().toString();
            UsageRecord usage = usageTrackingPort.getOrCreateUsage(subscriberEmail, currentMonth);

            if (!tierGatingService.canQuery(usage)) {
                log.warn("research() | Monthly query limit reached: email={}, used={}, limit={}",
                         LogSanitizer.maskEmail(subscriberEmail), usage.queryCount(), usage.queryLimit());
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("error", "Monthly query limit reached");
                body.put("used", usage.queryCount());
                body.put("limit", usage.queryLimit());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
            }

            // Increment usage before executing the query
            usageTrackingPort.incrementAndGet(subscriberEmail, currentMonth);
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
