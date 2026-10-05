package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link FrontierClaim} records.
 *
 * <p>Adapter implementations live in {@code infrastructure.persistence}. The
 * domain service interacts with this port only — it has no knowledge of JPA,
 * SQL, or the underlying storage mechanism.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public interface FrontierClaimPort {

    /** Persists a single claim, overwriting any existing record with the same claimId. */
    void save(FrontierClaim claim);

    /** Persists a batch of claims, overwriting any with matching claimIds. */
    void saveAll(List<FrontierClaim> claims);

    /** Returns all stored claims, most recently detected first. */
    List<FrontierClaim> findAll();

    /** Returns all claims attributed to the given company name (case-insensitive). */
    List<FrontierClaim> findByCompany(String company);

    /** Returns all claims with the given verdict string (matches {@link com.wgblackmon.aihealthcare.domain.model.ClaimVerdict#name()}). */
    List<FrontierClaim> findByVerdict(String verdict);

    /** Returns all claims with the given claim type string (matches {@link com.wgblackmon.aihealthcare.domain.model.ClaimType#name()}). */
    List<FrontierClaim> findByType(String claimType);

    /** Returns the claim with the given identifier, or empty if not found. */
    Optional<FrontierClaim> findById(String claimId);
}
