package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link CompanyContact} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public interface CompanyContactPort {

    CompanyContact save(CompanyContact contact);

    Optional<CompanyContact> findById(Long id);

    List<CompanyContact> findBySlug(String slug);

    void deleteById(Long id);
}
