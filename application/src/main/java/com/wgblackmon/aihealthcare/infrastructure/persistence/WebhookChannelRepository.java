package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WebhookChannelEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface WebhookChannelRepository extends JpaRepository<WebhookChannelEntity, String> {

    List<WebhookChannelEntity> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    List<WebhookChannelEntity> findByActiveTrue();
}
