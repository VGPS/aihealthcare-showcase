package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClaimClassifierPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrontierClaimPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * @version 1.1
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

        // Contradiction pass: group fresh claims by company, fetch 90-day prior claims,
        // ask LLM to flag conflicts. Only runs when there are prior claims to compare.
        List<FrontierClaim> withContradictions = applyContradictionCheck(fresh);

        if (!withContradictions.isEmpty()) {
            claimPort.saveAll(withContradictions);
        }

        return withContradictions;
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

    private List<FrontierClaim> applyContradictionCheck(List<FrontierClaim> fresh) {
        if (fresh.isEmpty()) {
            return fresh;
        }
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        Map<String, List<FrontierClaim>> byCompany = new HashMap<>();
        for (FrontierClaim c : fresh) {
            byCompany.computeIfAbsent(c.company().toLowerCase(), k -> new ArrayList<>()).add(c);
        }
        List<FrontierClaim> result = new ArrayList<>();
        for (Map.Entry<String, List<FrontierClaim>> entry : byCompany.entrySet()) {
            List<FrontierClaim> newForCompany = entry.getValue();
            List<FrontierClaim> prior = claimPort.findByCompanyDetectedAfter(entry.getKey(), since);
            if (prior.isEmpty()) {
                result.addAll(newForCompany);
            } else {
                result.addAll(classifierPort.detectContradictions(newForCompany, prior));
            }
        }
        return result;
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
