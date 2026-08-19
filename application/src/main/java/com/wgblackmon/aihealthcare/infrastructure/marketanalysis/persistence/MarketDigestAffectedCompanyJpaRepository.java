package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link MarketDigestAffectedCompanyEntity}.
 *
 * <p>Companies are keyed by {@code entry_id}. The adapter loads all companies for
 * each entry after loading the entries — no {@code @OneToMany} is used.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDigestAffectedCompanyJpaRepository
        extends JpaRepository<MarketDigestAffectedCompanyEntity, String> {

    List<MarketDigestAffectedCompanyEntity> findByEntryId(String entryId);

    void deleteByEntryId(String entryId);
}
