package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ApiKeyEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    List<ApiKeyEntity> findAllByOwnerEmail(String ownerEmail);
}
