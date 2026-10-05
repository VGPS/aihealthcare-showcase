package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClaimClassifierPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrontierClaimPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Domain service orchestrating the Frontier AI Claim Tracker pipeline.
 *
 * <p>Implements {@link TrackFrontierClaimsUseCase}. On {@code detectClaims()},
 * the service delegates article classification to {@link ClaimClassifierPort},
 * deduplicates results against existing records (keyed on company + a
 * normalised prefix of the claim text) and persists only new claims via
 * {@link FrontierClaimPort}.
 *
 * <p>No Spring or Lombok dependencies — wired entirely through {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public class FrontierClaimService implements TrackFrontierClaimsUseCase {

    private final ClaimClassifierPort classifierPort;
    private final FrontierClaimPort claimPort;

    public FrontierClaimService(ClaimClassifierPort classifierPort,
                                FrontierClaimPort claimPort) {
        this.classifierPort = classifierPort;
        this.claimPort = claimPort;
    }

    @Override
    public List<FrontierClaim> detectClaims(List<NewsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<FrontierClaim> classified = classifierPort.classifyClaims(articles);

        Set<String> existingKeys = buildExistingKeys();

        List<FrontierClaim> fresh = new ArrayList<>();
        for (FrontierClaim claim : classified) {
            String key = dedupeKey(claim);
            if (existingKeys.add(key)) {
                fresh.add(claim);
            }
        }

        if (!fresh.isEmpty()) {
            claimPort.saveAll(fresh);
        }

        return fresh;
    }

    @Override
    public List<FrontierClaim> getAll() {
        return claimPort.findAll();
    }

    @Override
    public List<FrontierClaim> getByCompany(String company) {
        return claimPort.findByCompany(company);
    }

    @Override
    public List<FrontierClaim> getByVerdict(ClaimVerdict verdict) {
        return claimPort.findByVerdict(verdict.name());
    }

    @Override
    public List<FrontierClaim> getByType(ClaimType claimType) {
        return claimPort.findByType(claimType.name());
    }

    @Override
    public Optional<FrontierClaim> getById(String claimId) {
        return claimPort.findById(claimId);
    }

    private Set<String> buildExistingKeys() {
        Set<String> keys = new HashSet<>();
        for (FrontierClaim existing : claimPort.findAll()) {
            keys.add(dedupeKey(existing));
        }
        return keys;
    }

    private String dedupeKey(FrontierClaim claim) {
        String prefix = claim.claimText().length() > 80
                ? claim.claimText().substring(0, 80)
                : claim.claimText();
        return claim.company().toLowerCase() + "|" + prefix.toLowerCase().trim();
    }
}
