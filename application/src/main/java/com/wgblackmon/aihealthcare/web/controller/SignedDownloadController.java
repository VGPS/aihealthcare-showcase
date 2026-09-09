package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SignedLinkPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Unauthenticated endpoint for downloading artifacts via signed tokens.
 *
 * <p>{@code GET /d/{token}} verifies the HMAC-signed token, loads the
 * artifact, and streams it as a download. No session is required — the
 * token is the sole proof of access.
 *
 * <p>Returns {@code 403} on a bad signature, {@code 410} on an expired
 * token or deleted artifact, and {@code 200} with the file on success.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@RestController
public class SignedDownloadController {

    private final SignedLinkPort signedLinkPort;
    private final DataJobPort dataJobPort;
    private final DataArtifactPort artifactPort;
    private final DataAccessAuditPort auditPort;
    private final Clock clock;

    public SignedDownloadController(SignedLinkPort signedLinkPort,
                                    DataJobPort dataJobPort,
                                    DataArtifactPort artifactPort,
                                    DataAccessAuditPort auditPort,
                                    Clock clock) {
        this.signedLinkPort = signedLinkPort;
        this.dataJobPort = dataJobPort;
        this.artifactPort = artifactPort;
        this.auditPort = auditPort;
        this.clock = clock;
        log.debug("SignedDownloadController() | constructed");
    }

    @GetMapping("/d/{token}")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        log.debug("download() | token=[REDACTED]");
        Instant now = clock.instant();

        Optional<String> jobIdOpt = signedLinkPort.verifyToken(token, now);
        if (jobIdOpt.isEmpty()) {
            log.debug("download() | return=403 (invalid or expired token)");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String jobId = jobIdOpt.get();

        Optional<DataJob> jobOpt = dataJobPort.findByJobId(jobId);
        if (jobOpt.isEmpty()) {
            log.debug("download() | return=410 (job not found)");
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        DataJob job = jobOpt.get();
        if (!artifactPort.exists(job.jobId())) {
            log.debug("download() | return=410 (artifact gone)");
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        try {
            byte[] data = artifactPort.read(job.jobId()).readAllBytes();

            auditPort.append(new DataAccessAuditEntry(
                    now, job.ownerEmail(), jobId, job.scheduleId(),
                    DataAccessAction.LINK_REDEEM, "SUCCESS", null,
                    job.rowCount(), (long) data.length
            ));

            String contentType = resolveContentType(job);
            String fileName = job.artifactPath();

            log.debug("download() | return=200, bytes={}", data.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(data.length)
                    .body(data);
        } catch (Exception e) {
            log.error("download() | failed to read artifact: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    private String resolveContentType(DataJob job) {
        if (job.format() == null) return "application/octet-stream";
        return switch (job.format()) {
            case CSV -> "text/csv";
            case JSON -> "application/json";
            case PDF -> "application/pdf";
        };
    }
}
