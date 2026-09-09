package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageDataPushSchedulesUseCase;
import com.wgblackmon.aihealthcare.web.dto.DataJobResponse;
import com.wgblackmon.aihealthcare.web.dto.DataPushScheduleRequest;
import com.wgblackmon.aihealthcare.web.dto.DataPushScheduleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for enterprise push schedule CRUD, run-now, and
 * cron preview operations.
 *
 * <p>All endpoints resolve the owner email from the authenticated
 * principal. Ownership is enforced at the use-case layer — a request
 * for another owner's schedule returns 404 (not 403).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enterprise/data/schedules")
public class EnterpriseScheduleRestController {

    private final ManageDataPushSchedulesUseCase useCase;

    public EnterpriseScheduleRestController(ManageDataPushSchedulesUseCase useCase) {
        log.debug("EnterpriseScheduleRestController() | useCase={}",
                useCase.getClass().getSimpleName());
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<DataPushScheduleResponse> create(
            @RequestBody DataPushScheduleRequest body, Principal principal) {
        log.debug("create() | label={}, feedId={}, principal={}",
                body.label(), body.feedId(), principal.getName());

        DataPushSchedule schedule = toDomain(body, principal.getName());

        DataPushSchedule created;
        try {
            created = useCase.create(schedule);
        } catch (IllegalStateException e) {
            log.debug("create() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.debug("create() | return=400, reason={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        List<Instant> nextRuns = useCase.previewNextRuns(
                created.cronExpression(), created.zoneId(), 3);

        DataPushScheduleResponse response = DataPushScheduleResponse.from(created, nextRuns);
        URI location = URI.create("/api/v1/enterprise/data/schedules/" + created.scheduleId());

        log.debug("create() | return=201, scheduleId={}", created.scheduleId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DataPushScheduleResponse>> list(Principal principal) {
        log.debug("list() | principal={}", principal.getName());
        try {
            List<DataPushSchedule> schedules = useCase.list(principal.getName());
            List<DataPushScheduleResponse> result = schedules.stream()
                    .map(DataPushScheduleResponse::from).toList();
            log.debug("list() | return={} schedules", result.size());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.debug("list() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<DataPushScheduleResponse> update(
            @PathVariable String scheduleId,
            @RequestBody DataPushScheduleRequest body,
            Principal principal) {
        log.debug("update() | scheduleId={}, principal={}", scheduleId, principal.getName());

        DataPushSchedule schedule = toDomain(body, principal.getName());

        DataPushSchedule updated;
        try {
            updated = useCase.update(scheduleId, principal.getName(), schedule);
        } catch (IllegalStateException e) {
            log.debug("update() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.debug("update() | return=404");
                return ResponseEntity.notFound().build();
            }
            log.debug("update() | return=400, reason={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        DataPushScheduleResponse response = DataPushScheduleResponse.from(updated);
        log.debug("update() | return=200, scheduleId={}", updated.scheduleId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable String scheduleId, Principal principal) {
        log.debug("delete() | scheduleId={}, principal={}", scheduleId, principal.getName());
        try {
            useCase.delete(scheduleId, principal.getName());
            log.debug("delete() | return=204");
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            log.debug("delete() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.debug("delete() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{scheduleId}/run")
    public ResponseEntity<DataJobResponse> runNow(
            @PathVariable String scheduleId, Principal principal) {
        log.debug("runNow() | scheduleId={}, principal={}", scheduleId, principal.getName());

        DataJob job;
        try {
            job = useCase.runNow(scheduleId, principal.getName());
        } catch (IllegalStateException e) {
            log.debug("runNow() | return=403, reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.debug("runNow() | return=404");
            return ResponseEntity.notFound().build();
        }

        DataJobResponse response = DataJobResponse.from(job);
        log.debug("runNow() | return=202, jobId={}", job.jobId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/preview")
    public ResponseEntity<List<Instant>> previewNextRuns(
            @RequestParam String cron,
            @RequestParam String zone,
            @RequestParam(defaultValue = "3") int count) {
        log.debug("previewNextRuns() | cron={}, zone={}, count={}", cron, zone, count);
        try {
            List<Instant> runs = useCase.previewNextRuns(cron, zone, Math.min(count, 10));
            log.debug("previewNextRuns() | return={} instants", runs.size());
            return ResponseEntity.ok(runs);
        } catch (IllegalArgumentException e) {
            log.debug("previewNextRuns() | return=400, reason={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private DataPushSchedule toDomain(DataPushScheduleRequest body, String ownerEmail) {
        ExportFormat format = body.format() != null
                ? ExportFormat.valueOf(body.format().toUpperCase())
                : ExportFormat.CSV;

        return new DataPushSchedule(
                UUID.randomUUID().toString(),
                ownerEmail,
                body.label(),
                body.feedId(),
                body.promptId(),
                body.promptText(),
                body.parameters() != null ? body.parameters() : Map.of(),
                format,
                body.cronExpression(),
                body.zoneId(),
                body.recipients() != null ? body.recipients() : List.of(),
                body.active() != null ? body.active() : true,
                null, null, null, null, 0,
                Instant.now(), Instant.now());
    }
}
