package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;

import java.util.List;

/**
 * Inbound port for detecting and querying inter-company relationships
 * extracted from articles and deal signals.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface MapCompanyRelationshipsUseCase {

    List<CompanyRelationship> detectRelationships();

    List<CompanyRelationship> getAllRelationships();

    List<CompanyRelationship> getRelationshipsForCompany(String companyName);
}
