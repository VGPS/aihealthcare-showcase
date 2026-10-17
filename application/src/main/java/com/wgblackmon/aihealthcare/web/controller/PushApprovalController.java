package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.port.inbound.ReviewPushApprovalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalGatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for the enterprise push human-approval checkpoint.
 *
 * <p>Lets an admin flag a push schedule for review ({@link PushApprovalGatePort})
 * and release or reject deliveries {@code EnterpriseDataPushScheduler} has held
 * for that flag ({@link ReviewPushApprovalsUseCase}). Restricted to ADMIN role
 * via SecurityConfig ({@code /admin/**}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Slf4j
@Controller
@RequestMapping("/admin/push-approvals")
public class PushApprovalController {

    private final ReviewPushApprovalsUseCase reviewUseCase;
    private final PushApprovalGatePort gatePort;

    public PushApprovalController(ReviewPushApprovalsUseCase reviewUseCase,
                                  PushApprovalGatePort gatePort) {
        log.debug("PushApprovalController() | reviewUseCase={}, gatePort={}",
                reviewUseCase.getClass().getSimpleName(), gatePort.getClass().getSimpleName());
        this.reviewUseCase = reviewUseCase;
        this.gatePort = gatePort;
    }

    /** Returns every held delivery still awaiting a decision, as JSON. */
    @GetMapping(produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<PushApprovalRequest>> listPending() {
        log.debug("listPending()");
        List<PushApprovalRequest> result = reviewUseCase.listPending();
        log.debug("listPending() | return={} pending", result.size());
        return ResponseEntity.ok(result);
    }

    /** Releases a held delivery — delivers the artifact now. */
    @PostMapping("/{jobId}/approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approve(
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            Authentication auth) {
        log.debug("approve() | jobId={}", jobId);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            reviewUseCase.approve(jobId, auth.getName());
            result.put("ok", true);
            log.info("approve() | jobId={} released by admin {}", jobId, auth.getName());
        } catch (Exception ex) {
            log.warn("approve() | error: {}", ex.getMessage());
            result.put("ok", false);
            result.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
        log.debug("approve() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /** Rejects a held delivery — the artifact is never sent. */
    @PostMapping("/{jobId}/reject")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reject(
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            Authentication auth) {
        log.debug("reject() | jobId={}", jobId);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            reviewUseCase.reject(jobId, auth.getName());
            result.put("ok", true);
            log.info("reject() | jobId={} rejected by admin {}", jobId, auth.getName());
        } catch (Exception ex) {
            log.warn("reject() | error: {}", ex.getMessage());
            result.put("ok", false);
            result.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
        log.debug("reject() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Sets or clears the approval gate for a push schedule by id. There is no
     * cross-customer schedule picker here — an admin supplies the id (visible
     * via the enterprise audit trail or database) they want to gate.
     */
    @PostMapping("/gate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setGate(
            @RequestParam String scheduleId,
            @RequestParam(defaultValue = "false") boolean required) {
        log.debug("setGate() | scheduleId={}, required={}", scheduleId, required);
        gatePort.setRequired(scheduleId, required);
        log.info("setGate() | scheduleId={} approval requirement set to {}", scheduleId, required);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheduleId", scheduleId);
        result.put("required", required);
        log.debug("setGate() | return={}", result);
        return ResponseEntity.ok(result);
    }
}
