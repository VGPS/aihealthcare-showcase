package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CompanyProfileEntity}.
 *
 * <p>Provides CRUD operations and custom finders for company profiles
 * stored in the {@code company_profiles} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface CompanyProfileRepository extends JpaRepository<CompanyProfileEntity, String> {

    /**
     * Returns all profiles ordered by article count descending.
     */
    List<CompanyProfileEntity> findAllByOrderByArticleCountDesc();

    /**
     * Returns all profiles whose categoriesPipe contains the given category.
     */
    List<CompanyProfileEntity> findByCategoriesPipeContaining(String category);
}
