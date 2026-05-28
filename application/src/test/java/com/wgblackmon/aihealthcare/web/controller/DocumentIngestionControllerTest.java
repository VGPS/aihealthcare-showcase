package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.web.dto.DocumentIngestRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link DocumentIngestionController}.
 *
 * <p>Covers the happy path (files processed and embedded), the no-files case
 * (empty directory), the partial-failure case (some files fail to parse), and
 * the bad-request case (invalid directory → {@link IllegalArgumentException} → 400).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(DocumentIngestionController.class)
class DocumentIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngestDocumentsUseCase ingestUseCase;

    private static final String INGEST_URL = "/api/v1/documents/ingest";

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void postIngest_validRequest_returns200WithCounts() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), anyInt()))
                .thenReturn(new DocumentIngestionResult(3, 12, List.of()));

        DocumentIngestRequest req = new DocumentIngestRequest(
                "/some/dir", "Clinical Guidelines", null);

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filesProcessed").value(3))
                .andExpect(jsonPath("$.chunksEmbedded").value(12))
                .andExpect(jsonPath("$.failures").isEmpty());
    }

    @Test
    void postIngest_emptyDirectory_returns200WithZeroCounts() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), anyInt()))
                .thenReturn(new DocumentIngestionResult(0, 0, List.of()));

        DocumentIngestRequest req = new DocumentIngestRequest(
                "/empty/dir", "Empty Run", null);

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filesProcessed").value(0))
                .andExpect(jsonPath("$.chunksEmbedded").value(0));
    }

    // -------------------------------------------------------------------------
    // Partial failures
    // -------------------------------------------------------------------------

    @Test
    void postIngest_partialFailures_returns200WithFailureList() throws Exception {
        List<String> failures = List.of("corrupt.pdf: PDF parse error");
        when(ingestUseCase.ingest(anyString(), anyString(), anyInt()))
                .thenReturn(new DocumentIngestionResult(2, 8, failures));

        DocumentIngestRequest req = new DocumentIngestRequest(
                "/mixed/dir", "Mixed Docs", 500);

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filesProcessed").value(2))
                .andExpect(jsonPath("$.chunksEmbedded").value(8))
                .andExpect(jsonPath("$.failures[0]").value("corrupt.pdf: PDF parse error"));
    }

    // -------------------------------------------------------------------------
    // Custom chunk size is forwarded
    // -------------------------------------------------------------------------

    @Test
    void postIngest_customChunkSize_forwardedToUseCase() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), eq(500)))
                .thenReturn(new DocumentIngestionResult(1, 4, List.of()));

        DocumentIngestRequest req = new DocumentIngestRequest(
                "/docs", "My Label", 500);

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filesProcessed").value(1));
    }

    // -------------------------------------------------------------------------
    // Bad request (invalid directory)
    // -------------------------------------------------------------------------

    @Test
    void postIngest_invalidDirectory_returns400() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("Directory does not exist: /no/such/path"));

        DocumentIngestRequest req = new DocumentIngestRequest(
                "/no/such/path", "Bad Dir", null);

        mockMvc.perform(post(INGEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
