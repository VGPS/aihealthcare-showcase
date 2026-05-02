package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.web.dto.DraftResponse;
import com.wgblackmon.aihealthcare.web.dto.GenerateRequest;
import com.wgblackmon.aihealthcare.web.dto.IngestRequest;
import com.wgblackmon.aihealthcare.web.dto.IngestResponse;
import com.wgblackmon.aihealthcare.web.dto.SectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the AI-in-Healthcare newsletter API.
 *
 * <p>Three endpoints form the two-step workflow:
 * <ol>
 *   <li><b>POST /api/v1/ingest</b> — kick off article ingestion for a set of topics.
 *       Returns a {@link IngestResponse} with the run ID and article count.</li>
 *   <li><b>POST /api/v1/drafts</b> — generate a newsletter draft from a completed
 *       ingestion run.  Returns the full {@link DraftResponse} (201 Created).</li>
 *   <li><b>GET  /api/v1/drafts/{draftId}</b> — retrieve a previously generated draft
 *       by ID (200 OK), or 404 if the ID is unknown.</li>
 * </ol>
 *
 * <p><b>Architecture note:</b> this controller calls only the two inbound-port
 * interfaces ({@link IngestArticlesUseCase} and {@link GenerateNewsletterUseCase}).
 * It never references {@code NewsletterService}, adapters, or domain repositories
 * directly, keeping the web layer decoupled from implementation details.
 *
 * <p>Domain exceptions are mapped to HTTP status codes by
 * {@link GlobalExceptionHandler} — this controller contains no try/catch blocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class NewsletterController {

    private final IngestArticlesUseCase    ingestUseCase;
    private final GenerateNewsletterUseCase generateUseCase;

    public NewsletterController(IngestArticlesUseCase ingestUseCase,
                                GenerateNewsletterUseCase generateUseCase) {
        log.debug("NewsletterController() | ingestUseCase={}, generateUseCase={}",
                  ingestUseCase.getClass().getSimpleName(),
                  generateUseCase.getClass().getSimpleName());
        this.ingestUseCase   = ingestUseCase;
        this.generateUseCase = generateUseCase;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/ingest
    // -------------------------------------------------------------------------

    /**
     * Kick off an article ingestion run for the supplied topics.
     *
     * @param request Ingestion parameters (run ID, week, topics, article cap).
     * @return 201 Created with {@link IngestResponse} confirming the run and article count.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        log.debug("ingest() | request={}", request);

        List<NewsArticle> articles = ingestUseCase.ingest(
                request.runId(),
                request.weekOf(),
                request.topics(),
                request.maxArticlesPerTopic()
        );

        IngestResponse response = new IngestResponse(request.runId(), articles.size());
        log.info("ingest() | Ingestion accepted: runId={}, articleCount={}", request.runId(), articles.size());
        log.debug("ingest() | return={}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/drafts
    // -------------------------------------------------------------------------

    /**
     * Generate a newsletter draft from a previously completed ingestion run.
     *
     * @param request Generation parameters (run ID, draft ID, title, tone, section cap).
     * @return 201 Created with the full {@link DraftResponse}.
     */
    @PostMapping("/drafts")
    public ResponseEntity<DraftResponse> generate(@RequestBody GenerateRequest request) {
        log.debug("generate() | request={}", request);

        NewsletterDraft draft = generateUseCase.generate(
                request.runId(),
                request.draftId(),
                request.title(),
                request.tone(),
                request.maxSectionsPerTopic(),
                request.ragEnabled()      != null ? request.ragEnabled()      : false,
                request.ragContextCount() != null ? request.ragContextCount() : 3
        );

        DraftResponse response = toResponse(draft);
        log.info("generate() | Draft created: draftId={}, sectionCount={}",
                 draft.draftId(), draft.sections().size());
        log.debug("generate() | return={}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/drafts/{draftId}
    // -------------------------------------------------------------------------

    /**
     * Retrieve a previously generated draft by ID.
     *
     * @param draftId Path variable identifying the draft.
     * @return 200 OK with the {@link DraftResponse}, or 404 if not found (via
     *         {@link GlobalExceptionHandler}).
     */
    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<DraftResponse> getDraft(@PathVariable String draftId) {
        log.debug("getDraft() | draftId={}", draftId);

        NewsletterDraft draft = generateUseCase.getDraft(draftId);

        DraftResponse response = toResponse(draft);
        log.debug("getDraft() | return={}", response);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Mapping helper
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link NewsletterDraft} domain object to a {@link DraftResponse} DTO.
     *
     * <p>Sections are mapped individually using a for loop (no streams per convention).
     * Source articles are collapsed to a count; full attribution data is available on
     * the domain object for future rendering endpoints.
     */
    private DraftResponse toResponse(NewsletterDraft draft) {
        log.debug("toResponse() | draftId={}", draft.draftId());

        List<SectionResponse> sections = new ArrayList<>();
        for (NewsletterSection section : draft.sections()) {
            sections.add(new SectionResponse(
                    section.sectionId(),
                    section.sectionType(),
                    section.topic(),
                    section.headline(),
                    section.summary()
            ));
        }

        DraftResponse result = new DraftResponse(
                draft.draftId(),
                draft.runId(),
                draft.title(),
                draft.weekOf(),
                draft.introduction(),
                sections,
                draft.sourceArticles().size(),
                draft.generatedAt()
        );
        log.debug("toResponse() | return={}", result);
        return result;
    }
}
