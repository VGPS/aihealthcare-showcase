package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EnterpriseRemoteConnectionEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface EnterpriseRemoteConnectionRepository extends JpaRepository<EnterpriseRemoteConnectionEntity, String> {

    Optional<EnterpriseRemoteConnectionEntity> findByConnectionIdAndOwnerEmail(String connectionId, String ownerEmail);

    List<EnterpriseRemoteConnectionEntity> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
}
