package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for held push deliveries awaiting admin review.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface PushApprovalPort {

    PushApprovalRequest save(PushApprovalRequest request);

    Optional<PushApprovalRequest> findByJobId(String jobId);

    /** Returns all requests still awaiting a decision, oldest first. */
    List<PushApprovalRequest> findPending();
}
