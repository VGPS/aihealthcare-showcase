package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link PushApprovalGateEntity}, keyed by schedule id.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface PushApprovalGateRepository extends JpaRepository<PushApprovalGateEntity, String> {
}
