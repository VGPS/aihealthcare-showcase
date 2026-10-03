package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link CompanyOutreachEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public interface CompanyOutreachRepository extends JpaRepository<CompanyOutreachEntity, Long> {

    List<CompanyOutreachEntity> findBySlug(String slug);

    Optional<CompanyOutreachEntity> findBySlugAndPurpose(String slug, String purpose);

    List<CompanyOutreachEntity> findAllByOrderByCreatedAtDesc();
}
