package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link CompanyRelationship} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface CompanyRelationshipPort {

    void saveAll(List<CompanyRelationship> relationships);

    List<CompanyRelationship> findAll();

    List<CompanyRelationship> findByCompany(String companyName);

    boolean existsBySourceAndTargetAndType(String source, String target,
                                           String relationshipType);
}
