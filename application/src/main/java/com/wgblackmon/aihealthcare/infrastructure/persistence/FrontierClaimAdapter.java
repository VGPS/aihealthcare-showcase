package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrontierClaimPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing {@link FrontierClaimPort}.
 *
 * <p>Delegates all persistence operations to {@link FrontierClaimRepository}.
 * Enum fields ({@code claimType}, {@code verdict}) are stored and retrieved
 * as their {@code name()} string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Slf4j
@Component
public class FrontierClaimAdapter implements FrontierClaimPort {

    private final FrontierClaimRepository repository;

    public FrontierClaimAdapter(FrontierClaimRepository repository) {
        log.debug("FrontierClaimAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(FrontierClaim claim) {
        log.debug("save() | claimId={}", claim.claimId());
        repository.save(toEntity(claim));
        log.debug("save() | return=void");
    }

    @Override
    public void saveAll(List<FrontierClaim> claims) {
        log.debug("saveAll() | count={}", claims.size());
        repository.saveAll(claims.stream().map(this::toEntity).collect(Collectors.toList()));
        log.debug("saveAll() | return=void");
    }

    @Override
    public List<FrontierClaim> findAll() {
        log.debug("findAll()");
        List<FrontierClaim> result = repository.findAllByOrderByDetectedAtDesc()
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findAll() | return={} claims", result.size());
        return result;
    }

    @Override
    public List<FrontierClaim> findByCompany(String company) {
        log.debug("findByCompany() | company={}", company);
        List<FrontierClaim> result = repository.findByCompanyIgnoreCaseOrderByDetectedAtDesc(company)
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findByCompany() | return={} claims", result.size());
        return result;
    }

    @Override
    public List<FrontierClaim> findByVerdict(String verdict) {
        log.debug("findByVerdict() | verdict={}", verdict);
        List<FrontierClaim> result = repository.findByVerdictOrderByDetectedAtDesc(verdict)
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findByVerdict() | return={} claims", result.size());
        return result;
    }

    @Override
    public List<FrontierClaim> findByType(String claimType) {
        log.debug("findByType() | claimType={}", claimType);
        List<FrontierClaim> result = repository.findByClaimTypeOrderByDetectedAtDesc(claimType)
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findByType() | return={} claims", result.size());
        return result;
    }

    @Override
    public Optional<FrontierClaim> findById(String claimId) {
        log.debug("findById() | claimId={}", claimId);
        Optional<FrontierClaim> result = repository.findById(claimId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    private FrontierClaimEntity toEntity(FrontierClaim claim) {
        FrontierClaimEntity e = new FrontierClaimEntity();
        e.setId(claim.claimId());
        e.setCompany(claim.company());
        e.setClaimText(claim.claimText());
        e.setClaimDate(claim.claimDate());
        e.setSourceUrl(claim.sourceUrl());
        e.setSourceTitle(claim.sourceTitle());
        e.setClaimType(claim.claimType().name());
        e.setVerdict(claim.verdict().name());
        e.setEvidenceNotes(claim.evidenceNotes());
        e.setArticleId(claim.articleId());
        e.setDetectedAt(claim.detectedAt());
        e.setLastReviewedAt(claim.lastReviewedAt());
        return e;
    }

    private FrontierClaim toDomain(FrontierClaimEntity e) {
        return new FrontierClaim(
                e.getId(),
                e.getCompany(),
                e.getClaimText(),
                e.getClaimDate(),
                e.getSourceUrl(),
                e.getSourceTitle(),
                ClaimType.valueOf(e.getClaimType()),
                ClaimVerdict.valueOf(e.getVerdict()),
                e.getEvidenceNotes(),
                e.getArticleId(),
                e.getDetectedAt(),
                e.getLastReviewedAt()
        );
    }
}
