package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.web.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for enterprise data job operations.
 *
 * <p>All endpoints resolve the owner email from the authenticated principal.
 * Job ownership is enforced at the use-case layer — a request for another
 * owner's job returns 404 (not 403).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enterprise/data")
public class EnterpriseDataRestController {

    private final RequestEnterpriseDataUseCase useCase;
    private final DataArtifactPort artifactPort;

    public EnterpriseDataRestController(RequestEnterpriseDataUseCase useCase,
                                         DataArtifactPort artifactPort) {
        log.debug("EnterpriseDataRestController() | useCase={}, artifactPort={}",
                useCase.getClass().getSimpleName(), artifactPort.getClass().getSimpleName());
        this.useCase = useCase;
        this.artifactPort = artifactPort;
    }

    @PostMapping("/jobs")
    public ResponseEntity<DataJobResponse> submitJob(@RequestBody DataJobSubmitRequest body,
                                                      Principal principal) {
        log.debug("submitJob() | feedId={}, promptId={}, principal={}",
                body.feedId(), body.promptId(), principal.getName());

        ExportFormat format = body.format() != null
                ? ExportFormat.valueOf(body.format().toUpperCase())
                : ExportFormat.CSV;

        DataRequest request = new DataRequest(
                UUID.randomUUID().toString(),
                principal.getName(),
                null,
                DataJobMode.PULL,
                body.feedId(),
                body.promptId(),
                body.promptText(),
                body.parameters() != null ? body.parameters() : Map.of(),
                format,
                body.rowLimit() != null ? body.rowLimit() : 100,
                body.connectionId(),
                null,
                Instant.now());

        DataJob job;
        try {
            job = useCase.submit(request);
        } catch (IllegalStateException e) {
            log.debug("submitJob() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.debug("submitJob() | return=400, reason={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        DataJobResponse response = DataJobResponse.from(job);

        URI location = URI.create("/api/v1/enterprise/data/jobs/" + job.jobId());
        log.debug("submitJob() | return=202, jobId={}", job.jobId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(HttpHeaders.LOCATION, location.toString())
                .body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<DataJobResponse> getJob(@PathVariable String jobId,
                                                   Principal principal) {
        log.debug("getJob() | jobId={}, principal={}", jobId, principal.getName());
        try {
            DataJob job = useCase.getJob(jobId, principal.getName());
            DataJobResponse result = DataJobResponse.from(job);
            log.debug("getJob() | return={}", result.status());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.debug("getJob() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<DataJobResponse>> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        log.debug("listJobs() | page={}, size={}, principal={}", page, size, principal.getName());
        List<DataJob> jobs = useCase.listJobs(principal.getName(), page, size);
        List<DataJobResponse> result = jobs.stream().map(DataJobResponse::from).toList();
        log.debug("listJobs() | return={} jobs", result.size());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId, Principal principal) {
        log.debug("cancelJob() | jobId={}, principal={}", jobId, principal.getName());
        try {
            useCase.cancel(jobId, principal.getName());
            log.debug("cancelJob() | return=204");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.debug("cancelJob() | return=404");
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.debug("cancelJob() | return=409, message={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/feeds")
    public ResponseEntity<List<DataFeedResponse>> listFeeds(Principal principal) {
        log.debug("listFeeds() | principal={}", principal.getName());
        List<DataFeed> feeds = useCase.listFeeds(principal.getName());
        List<DataFeedResponse> result = feeds.stream().map(DataFeedResponse::from).toList();
        log.debug("listFeeds() | return={} feeds", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/feeds/{feedId}/prompts")
    public ResponseEntity<List<CannedPromptResponse>> listPrompts(
            @PathVariable String feedId, Principal principal) {
        log.debug("listPrompts() | feedId={}, principal={}", feedId, principal.getName());
        List<CannedPrompt> prompts = useCase.listPrompts(principal.getName(), feedId);
        List<CannedPromptResponse> result = prompts.stream()
                .map(CannedPromptResponse::from).toList();
        log.debug("listPrompts() | return={} prompts", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs/{jobId}/log")
    public ResponseEntity<String> readLog(@PathVariable String jobId,
                                           @RequestParam(defaultValue = "0") long offset,
                                           Principal principal) {
        log.debug("readLog() | jobId={}, offset={}, principal={}", jobId, offset, principal.getName());
        try {
            String logContent = useCase.readLog(jobId, principal.getName(), offset);
            log.debug("readLog() | return={} chars", logContent != null ? logContent.length() : 0);
            return ResponseEntity.ok(logContent);
        } catch (IllegalArgumentException e) {
            log.debug("readLog() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/jobs/{jobId}/artifact")
    public ResponseEntity<?> downloadArtifact(@PathVariable String jobId,
                                               Principal principal) {
        log.debug("downloadArtifact() | jobId={}, principal={}", jobId, principal.getName());
        DataJob job;
        try {
            job = useCase.getJob(jobId, principal.getName());
        } catch (IllegalArgumentException e) {
            log.debug("downloadArtifact() | return=404 (job not found)");
            return ResponseEntity.notFound().build();
        }

        if (job.status() != DataJobStatus.SUCCEEDED) {
            log.debug("downloadArtifact() | return=409 (job status={})", job.status());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Job is not in SUCCEEDED state",
                                 "status", job.status().name()));
        }

        if (job.expiresAt() != null && Instant.now().isAfter(job.expiresAt())) {
            log.debug("downloadArtifact() | return=410 (expired)");
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "Artifact has expired"));
        }

        if (!artifactPort.exists(jobId)) {
            log.debug("downloadArtifact() | return=410 (artifact missing)");
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "Artifact file not found"));
        }

        InputStream stream = artifactPort.read(jobId);
        String contentType = resolveContentType(job.format());
        String fileName = jobId + "." + job.format().name().toLowerCase();

        log.debug("downloadArtifact() | return=200, fileName={}", fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(new InputStreamResource(stream));
    }

    private String resolveContentType(ExportFormat format) {
        if (format == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return switch (format) {
            case CSV -> "text/csv";
            case JSON -> MediaType.APPLICATION_JSON_VALUE;
            case PDF -> MediaType.APPLICATION_PDF_VALUE;
        };
    }
}
