package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalStatus;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.ReviewPushApprovalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushSchedulePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalPort;

import java.time.Instant;
import java.util.List;

/**
 * Domain service that lets an admin review and release (or reject) enterprise
 * push deliveries held by the approval gate.
 *
 * <p>Approving a held delivery performs exactly the same delivery + outcome
 * recording an un-gated delivery would have performed in
 * {@code EnterpriseDataPushScheduler.awaitAndDeliver(...)} — this service is
 * the deferred second half of that method. Rejecting leaves the underlying
 * {@link DataJob} at {@code SUCCEEDED} (data generation worked) but never
 * calls {@link DataPushDeliveryPort} — the artifact is simply never sent.
 *
 * <p>This service is pure domain — no Spring, Lombok, or infrastructure imports.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public class PushApprovalService implements ReviewPushApprovalsUseCase {

    private final PushApprovalPort approvalPort;
    private final DataJobPort dataJobPort;
    private final DataArtifactPort artifactPort;
    private final DataPushSchedulePort schedulePort;
    private final DataPushDeliveryPort deliveryPort;
    private final DataAccessAuditPort auditPort;

    public PushApprovalService(PushApprovalPort approvalPort,
                               DataJobPort dataJobPort,
                               DataArtifactPort artifactPort,
                               DataPushSchedulePort schedulePort,
                               DataPushDeliveryPort deliveryPort,
                               DataAccessAuditPort auditPort) {
        this.approvalPort = approvalPort;
        this.dataJobPort = dataJobPort;
        this.artifactPort = artifactPort;
        this.schedulePort = schedulePort;
        this.deliveryPort = deliveryPort;
        this.auditPort = auditPort;
    }

    @Override
    public List<PushApprovalRequest> listPending() {
        return approvalPort.findPending();
    }

    @Override
    public void approve(String jobId, String decidedBy) {
        PushApprovalRequest request = pendingRequestOrThrow(jobId);
        DataJob job = dataJobPort.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        DataPushSchedule schedule = schedulePort
                .findByScheduleIdAndOwnerEmail(request.scheduleId(), request.ownerEmail())
                .orElseThrow(() -> new IllegalStateException("Schedule not found: " + request.scheduleId()));

        Instant now = Instant.now();

        if (job.status() != DataJobStatus.SUCCEEDED || !artifactPort.exists(jobId)) {
            approvalPort.save(decided(request, PushApprovalStatus.REJECTED, decidedBy, now));
            auditPort.append(new DataAccessAuditEntry(
                    now, request.ownerEmail(), jobId, request.scheduleId(),
                    DataAccessAction.PUSH_FAIL, "FAILED",
                    "Approved but artifact no longer available", null, null));
            return;
        }

        DataArtifact artifact = new DataArtifact(
                job.jobId(), job.format(), job.artifactPath(),
                job.byteSize() != null ? job.byteSize() : 0L,
                job.contentSha256(), now, job.expiresAt());

        PushDeliveryResult result = deliveryPort.deliver(schedule, job, artifact);

        approvalPort.save(decided(request, PushApprovalStatus.APPROVED, decidedBy, now));

        if (result.success()) {
            schedulePort.recordOutcome(request.scheduleId(), jobId, DataJobStatus.SUCCEEDED, now);
            auditPort.append(new DataAccessAuditEntry(
                    now, request.ownerEmail(), jobId, request.scheduleId(),
                    DataAccessAction.PUSH_SEND, "SUCCESS",
                    "Released by admin " + decidedBy + " mode=" + result.mode()
                            + " recipients=" + result.recipients().size(),
                    job.rowCount(), job.byteSize()));
        } else {
            schedulePort.recordOutcome(request.scheduleId(), jobId, DataJobStatus.FAILED, now);
            auditPort.append(new DataAccessAuditEntry(
                    now, request.ownerEmail(), jobId, request.scheduleId(),
                    DataAccessAction.PUSH_FAIL, "FAILED", result.errorMessage(), null, null));
        }
    }

    @Override
    public void reject(String jobId, String decidedBy) {
        PushApprovalRequest request = pendingRequestOrThrow(jobId);
        Instant now = Instant.now();

        approvalPort.save(decided(request, PushApprovalStatus.REJECTED, decidedBy, now));

        auditPort.append(new DataAccessAuditEntry(
                now, request.ownerEmail(), jobId, request.scheduleId(),
                DataAccessAction.PUSH_REJECT, "REJECTED",
                "Rejected by admin " + decidedBy, null, null));
    }

    private PushApprovalRequest pendingRequestOrThrow(String jobId) {
        PushApprovalRequest request = approvalPort.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("No approval request for job: " + jobId));
        if (request.status() != PushApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request for job " + jobId
                    + " is already " + request.status());
        }
        return request;
    }

    private PushApprovalRequest decided(PushApprovalRequest request, PushApprovalStatus status,
                                        String decidedBy, Instant now) {
        return new PushApprovalRequest(request.jobId(), request.scheduleId(), request.ownerEmail(),
                request.requestedAt(), status, now, decidedBy);
    }
}
