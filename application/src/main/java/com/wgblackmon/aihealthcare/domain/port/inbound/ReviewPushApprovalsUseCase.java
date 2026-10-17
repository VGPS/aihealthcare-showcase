package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;

import java.util.List;

/**
 * Inbound port for an admin reviewing push deliveries held by the approval gate.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface ReviewPushApprovalsUseCase {

    /** Returns every held delivery still awaiting a decision, oldest first. */
    List<PushApprovalRequest> listPending();

    /**
     * Approves a held delivery — delivers the artifact now and records the outcome
     * exactly as an un-gated delivery would have.
     *
     * @param jobId     the held job to release
     * @param decidedBy identifier of the approving admin, for the audit trail
     */
    void approve(String jobId, String decidedBy);

    /**
     * Rejects a held delivery — the artifact is never sent. The underlying job
     * remains SUCCEEDED (data generation worked); only delivery is withheld.
     *
     * @param jobId     the held job to reject
     * @param decidedBy identifier of the rejecting admin, for the audit trail
     */
    void reject(String jobId, String decidedBy);
}
