package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link CompanyOutreach} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public interface CompanyOutreachPort {

    CompanyOutreach save(CompanyOutreach outreach);

    Optional<CompanyOutreach> findById(Long id);

    Optional<CompanyOutreach> findBySlugAndPurpose(String slug, OutreachPurpose purpose);

    List<CompanyOutreach> findAll();

    List<CompanyOutreach> findBySlug(String slug);

    void deleteById(Long id);
}
