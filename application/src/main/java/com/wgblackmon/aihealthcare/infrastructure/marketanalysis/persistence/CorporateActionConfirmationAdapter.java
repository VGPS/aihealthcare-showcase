package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.CorporateActionConfirmation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed persistence adapter for {@link CorporateActionConfirmation} records.
 *
 * <p>Provides save and query operations for corporate action confirmations linked
 * to a digest date. Used by
 * {@link com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca.AlpacaCorporateActionsAdapter}
 * to persist results after matching Alpaca API actions to digest tickers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class CorporateActionConfirmationAdapter {

    private final CorporateActionConfirmationJpaRepository repo;

    public CorporateActionConfirmationAdapter(CorporateActionConfirmationJpaRepository repo) {
        this.repo = repo;
    }

    /**
     * Persists a single confirmation record.
     *
     * @param confirmation the confirmation to save (non-null)
     */
    public void save(CorporateActionConfirmation confirmation) {
        log.debug("save() | confirmationId={}, digestDate={}, ticker={}",
                confirmation.confirmationId(), confirmation.digestDate(), confirmation.tickerSymbol());
        repo.save(toEntity(confirmation));
        log.debug("save() | return=void");
    }

    /**
     * Returns all confirmations for the given digest date.
     *
     * @param digestDate the date to query
     * @return list of confirmations; empty if none found
     */
    public List<CorporateActionConfirmation> findByDigestDate(LocalDate digestDate) {
        log.debug("findByDigestDate() | digestDate={}", digestDate);
        List<CorporateActionConfirmationEntity> entities = repo.findByDigestDate(digestDate);
        List<CorporateActionConfirmation> result = new ArrayList<>();
        for (CorporateActionConfirmationEntity e : entities) {
            result.add(toDomain(e));
        }
        log.debug("findByDigestDate() | return.size={}", result.size());
        return result;
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private CorporateActionConfirmationEntity toEntity(CorporateActionConfirmation c) {
        return new CorporateActionConfirmationEntity(
                c.confirmationId(), c.digestDate(), c.tickerSymbol(), c.actionType(),
                c.declarationDate(), c.exDate(), c.recordDate(), c.payableDate());
    }

    private CorporateActionConfirmation toDomain(CorporateActionConfirmationEntity e) {
        return new CorporateActionConfirmation(
                e.getConfirmationId(), e.getDigestDate(), e.getTickerSymbol(), e.getActionType(),
                e.getDeclarationDate(), e.getExDate(), e.getRecordDate(), e.getPayableDate());
    }
}
