package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;
import com.wgblackmon.aihealthcare.domain.marketanalysis.DisclosedPortion;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link DealTermsPort}.
 *
 * <p>Uses entry headline as a natural key. Save is an upsert — if terms already
 * exist for the headline, the old row is deleted (flushed) before the new row
 * is inserted to avoid the unique-constraint violation under Hibernate batching.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class DealTermsAdapter implements DealTermsPort {

    private final DealTermsJpaRepository repo;

    public DealTermsAdapter(DealTermsJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void save(String entryHeadline, DealTerms terms) {
        log.debug("save() | headline={}, disclosed={}", entryHeadline, terms.disclosedPortion());

        Optional<DealTermsEntity> existing = repo.findByEntryHeadline(entryHeadline);
        if (existing.isPresent()) {
            repo.delete(existing.get());
            repo.flush();
        }

        DealTermsEntity entity = new DealTermsEntity(
                entryHeadline,
                terms.upfrontCashUsd(),
                terms.milestonePaymentsUsd(),
                terms.equityStakePct(),
                terms.royaltyPct(),
                terms.disclosedPortion().name()
        );
        repo.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<DealTerms> findByEntryHeadline(String entryHeadline) {
        log.debug("findByEntryHeadline() | headline={}", entryHeadline);

        Optional<DealTerms> result = repo.findByEntryHeadline(entryHeadline).map(this::toDomain);

        log.debug("findByEntryHeadline() | return=present:{}", result.isPresent());
        return result;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private DealTerms toDomain(DealTermsEntity entity) {
        return new DealTerms(
                entity.getUpfrontCashUsd(),
                entity.getMilestonePaymentsUsd(),
                entity.getEquityStakePct(),
                entity.getRoyaltyPct(),
                DisclosedPortion.valueOf(entity.getDisclosedPortion())
        );
    }
}
