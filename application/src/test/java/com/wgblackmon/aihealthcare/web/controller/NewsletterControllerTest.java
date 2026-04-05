package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.web.dto.GenerateRequest;
import com.wgblackmon.aihealthcare.web.dto.IngestRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link NewsletterController} and {@link GlobalExceptionHandler}.
 *
 * <p>{@code @WebMvcTest} loads only the web layer (controllers, filters, exception
 * handlers).  No Spring AI beans, no adapters, no {@code AppConfig} — just the
 * controller under test.  Both use-case ports are injected as {@code @MockBean}s,
 * which keeps these tests fast and isolated from AI and ingestion infrastructure.
 *
 * <p>The {@link GlobalExceptionHandler} is imported explicitly so exception-mapping
 * behaviour is verified alongside the controller.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
@WebMvcTest(NewsletterController.class)
@Import(GlobalExceptionHandler.class)
class NewsletterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestArticlesUseCase ingestUseCase;

    @MockBean
    private GenerateNewsletterUseCase generateUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // --- shared fixtures ---

    private static final String RUN_ID   = "run-001";
    private static final String DRAFT_ID = "draft-001";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001", "AI Improves Diagnostics",
            URI.create("https://example.com/article-001"),
            "Researchers found AI outperforms radiologists.",
            "AI diagnostics", null, null
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001", SectionType.WHAT_SHIPPED, "AI diagnostics",
            "AI Outperforms Radiologists", "AI-assisted diagnosis improves accuracy by 20%.",
            List.of("article-001")
    );

    private static final NewsletterDraft DRAFT = new NewsletterDraft(
            DRAFT_ID, RUN_ID, "AI in Healthcare Weekly",
            LocalDate.of(2026, 4, 4),
            "Welcome to this week's edition.",
            List.of(SECTION), List.of(ARTICLE), Instant.parse("2026-04-04T12:00:00Z")
    );

    // -------------------------------------------------------------------------
    // POST /api/v1/ingest
    // -------------------------------------------------------------------------

    @Test
    void postIngest_validRequest_returns201WithRunIdAndCount() throws Exception {
        when(ingestUseCase.ingest(anyString(), any(LocalDate.class), anyList(), anyInt()))
                .thenReturn(List.of(ARTICLE));

        IngestRequest req = new IngestRequest(RUN_ID, LocalDate.of(2026, 4, 4),
                List.of("AI diagnostics"), 5);

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(RUN_ID))
                .andExpect(jsonPath("$.articleCount").value(1));
    }

    @Test
    void postIngest_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(ingestUseCase.ingest(anyString(), any(), anyList(), anyInt()))
                .thenThrow(new IllegalArgumentException("runId must not be blank"));

        IngestRequest req = new IngestRequest("", LocalDate.now(), List.of("topic"), 5);

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/drafts
    // -------------------------------------------------------------------------

    @Test
    void postDrafts_validRequest_returns201WithDraftBody() throws Exception {
        when(generateUseCase.generate(eq(RUN_ID), eq(DRAFT_ID), anyString(),
                any(NewsletterTone.class), anyInt()))
                .thenReturn(DRAFT);

        GenerateRequest req = new GenerateRequest(RUN_ID, DRAFT_ID,
                "AI in Healthcare Weekly", NewsletterTone.PROFESSIONAL, 3);

        mockMvc.perform(post("/api/v1/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                .andExpect(jsonPath("$.runId").value(RUN_ID))
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.sections[0].sectionType").value("WHAT_SHIPPED"))
                .andExpect(jsonPath("$.sourceArticleCount").value(1));
    }

    @Test
    void postDrafts_unknownRunId_returns404() throws Exception {
        when(generateUseCase.generate(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenThrow(new RunNotFoundException("no-such-run"));

        GenerateRequest req = new GenerateRequest("no-such-run", DRAFT_ID,
                "Title", NewsletterTone.ACCESSIBLE, 3);

        mockMvc.perform(post("/api/v1/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void postDrafts_emptyRun_returns422() throws Exception {
        when(generateUseCase.generate(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenThrow(new NoArticlesFoundException(RUN_ID));

        GenerateRequest req = new GenerateRequest(RUN_ID, DRAFT_ID,
                "Title", NewsletterTone.TECHNICAL, 3);

        mockMvc.perform(post("/api/v1/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_ARTICLES"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/drafts/{draftId}
    // -------------------------------------------------------------------------

    @Test
    void getDraft_existingId_returns200WithBody() throws Exception {
        when(generateUseCase.getDraft(eq(DRAFT_ID))).thenReturn(DRAFT);

        mockMvc.perform(get("/api/v1/drafts/{draftId}", DRAFT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                .andExpect(jsonPath("$.introduction").value("Welcome to this week's edition."))
                .andExpect(jsonPath("$.sections[0].headline").value("AI Outperforms Radiologists"));
    }

    @Test
    void getDraft_unknownId_returns404() throws Exception {
        when(generateUseCase.getDraft(anyString()))
                .thenThrow(new RunNotFoundException("no-such-draft"));

        mockMvc.perform(get("/api/v1/drafts/{draftId}", "no-such-draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
