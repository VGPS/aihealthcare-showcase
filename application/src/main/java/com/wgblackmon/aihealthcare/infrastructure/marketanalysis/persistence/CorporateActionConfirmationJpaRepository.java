package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link CorporateActionConfirmationEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface CorporateActionConfirmationJpaRepository
        extends JpaRepository<CorporateActionConfirmationEntity, String> {

    List<CorporateActionConfirmationEntity> findByDigestDate(LocalDate digestDate);

    List<CorporateActionConfirmationEntity> findByDigestDateAndTickerSymbol(
            LocalDate digestDate, String tickerSymbol);
}
