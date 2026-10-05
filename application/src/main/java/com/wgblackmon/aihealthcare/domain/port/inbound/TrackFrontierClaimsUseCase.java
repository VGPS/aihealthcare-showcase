package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the Frontier AI Claim Tracker pipeline.
 *
 * <p>Provides operations for driving claim extraction from harvested articles,
 * retrieving stored claims with optional filtering, and fetching individual
 * claims by their identifier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public interface TrackFrontierClaimsUseCase {

    /**
     * Extracts and classifies claims from the supplied articles, deduplicates
     * against existing records, and persists any new claims.
     *
     * @param articles articles to scan for frontier AI company claims
     * @return the list of newly persisted claims (may be empty)
     */
    List<FrontierClaim> detectClaims(List<NewsArticle> articles);

    /** Returns all stored claims, most recently detected first. */
    List<FrontierClaim> getAll();

    /**
     * Returns all claims attributed to the specified company name
     * (case-insensitive).
     */
    List<FrontierClaim> getByCompany(String company);

    /** Returns all claims with the specified verdict. */
    List<FrontierClaim> getByVerdict(ClaimVerdict verdict);

    /** Returns all claims with the specified claim type. */
    List<FrontierClaim> getByType(ClaimType claimType);

    /** Returns the claim with the given identifier, or empty if not found. */
    Optional<FrontierClaim> getById(String claimId);
}
