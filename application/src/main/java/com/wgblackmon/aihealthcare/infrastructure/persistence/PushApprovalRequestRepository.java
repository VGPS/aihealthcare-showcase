package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link PushApprovalRequestEntity}, keyed by job id.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface PushApprovalRequestRepository extends JpaRepository<PushApprovalRequestEntity, String> {

    List<PushApprovalRequestEntity> findByStatusOrderByRequestedAtAsc(String status);
}
