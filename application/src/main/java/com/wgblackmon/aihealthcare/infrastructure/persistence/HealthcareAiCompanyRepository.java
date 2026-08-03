package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link HealthcareAiCompanyEntity}.
 *
 * <p>Provides lookup by normalized name and domain for deduplication,
 * and ordered listing for the UI.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public interface HealthcareAiCompanyRepository
        extends JpaRepository<HealthcareAiCompanyEntity, String> {

    Optional<HealthcareAiCompanyEntity> findByNameNormalized(String nameNormalized);

    Optional<HealthcareAiCompanyEntity> findByDomain(String domain);

    boolean existsByNameNormalized(String nameNormalized);

    boolean existsByDomain(String domain);

    List<HealthcareAiCompanyEntity> findAllByOrderByDiscoveredAtDesc();
}
