package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for managing company intelligence profiles and their event timelines.
 *
 * <p>Used by web controllers to serve company detail pages and the company index.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface ManageCompanyProfilesUseCase {

    /**
     * Returns the company profile for the given slug, if it exists.
     */
    Optional<CompanyProfile> getProfile(String slug);

    /**
     * Returns all company profiles, ordered by article count descending.
     */
    List<CompanyProfile> listProfiles();

    /**
     * Returns the event timeline for a company, ordered by occurredAt descending.
     */
    List<CompanyEvent> getCompanyTimeline(String slug);
}
