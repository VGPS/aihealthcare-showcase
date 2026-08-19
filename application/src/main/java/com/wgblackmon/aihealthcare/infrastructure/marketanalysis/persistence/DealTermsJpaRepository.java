package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DealTermsEntity}.
 *
 * <p>Keyed by {@code entryHeadline} as a natural surrogate key.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface DealTermsJpaRepository extends JpaRepository<DealTermsEntity, Long> {

    Optional<DealTermsEntity> findByEntryHeadline(String entryHeadline);

    boolean existsByEntryHeadline(String entryHeadline);
}
