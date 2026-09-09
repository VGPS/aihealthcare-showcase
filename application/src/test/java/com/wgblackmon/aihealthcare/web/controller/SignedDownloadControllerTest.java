package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link SignedDownloadController} — the unauthenticated
 * signed-link download path.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Import(SecurityConfig.class)
@WebMvcTest(SignedDownloadController.class)
class SignedDownloadControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignedLinkPort signedLinkPort;

    @MockitoBean
    private DataJobPort dataJobPort;

    @MockitoBean
    private DataArtifactPort artifactPort;

    @MockitoBean
    private DataAccessAuditPort auditPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private Clock clock;

    private DataJob testJob() {
        return new DataJob(
                "job-42", "alice@test.com", null, DataJobMode.PUSH, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 2048L, "abc123", "job-42.csv", null,
                null, null, NOW, NOW, NOW, NOW, NOW.plusSeconds(86400), "sched-1"
        );
    }

    @Test
    @DisplayName("403 on invalid/expired token — no session required")
    void invalidToken_returns403() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(signedLinkPort.verifyToken(eq("bad-token"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/d/bad-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("410 when job not found")
    void jobNotFound_returns410() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(signedLinkPort.verifyToken(eq("valid-token"), any())).thenReturn(Optional.of("job-42"));
        when(dataJobPort.findByJobId("job-42")).thenReturn(Optional.empty());

        mockMvc.perform(get("/d/valid-token"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("410 when artifact deleted")
    void artifactDeleted_returns410() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(signedLinkPort.verifyToken(eq("valid-token"), any())).thenReturn(Optional.of("job-42"));
        when(dataJobPort.findByJobId("job-42")).thenReturn(Optional.of(testJob()));
        when(artifactPort.exists("job-42")).thenReturn(false);

        mockMvc.perform(get("/d/valid-token"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("200 happy path — file streamed as attachment")
    void validToken_returns200WithFile() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(signedLinkPort.verifyToken(eq("valid-token"), any())).thenReturn(Optional.of("job-42"));
        when(dataJobPort.findByJobId("job-42")).thenReturn(Optional.of(testJob()));
        when(artifactPort.exists("job-42")).thenReturn(true);
        when(artifactPort.read("job-42")).thenReturn(new java.io.ByteArrayInputStream("col1,col2\nval1,val2".getBytes()));

        mockMvc.perform(get("/d/valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"job-42.csv\""))
                .andExpect(content().contentType("text/csv"));

        verify(auditPort).append(any(DataAccessAuditEntry.class));
    }

    @Test
    @DisplayName("No session required — unauthenticated request succeeds")
    void noSessionRequired() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(signedLinkPort.verifyToken(eq("valid-token"), any())).thenReturn(Optional.of("job-42"));
        when(dataJobPort.findByJobId("job-42")).thenReturn(Optional.of(testJob()));
        when(artifactPort.exists("job-42")).thenReturn(true);
        when(artifactPort.read("job-42")).thenReturn(new java.io.ByteArrayInputStream("data".getBytes()));

        // No @WithMockUser — this is unauthenticated
        mockMvc.perform(get("/d/valid-token"))
                .andExpect(status().isOk());
    }
}
