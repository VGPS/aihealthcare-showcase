package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link CompanyContactEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public interface CompanyContactRepository extends JpaRepository<CompanyContactEntity, Long> {

    List<CompanyContactEntity> findBySlugOrderByCreatedAtDesc(String slug);
}
