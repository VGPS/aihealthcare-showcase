package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link SavedPostEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface SavedPostRepository extends JpaRepository<SavedPostEntity, String> {

    List<SavedPostEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<SavedPostEntity> findAllByOrderByCreatedAtDesc();
}
