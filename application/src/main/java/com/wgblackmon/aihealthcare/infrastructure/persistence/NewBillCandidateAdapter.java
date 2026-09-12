package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.NewBillCandidate;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewBillCandidatePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link NewBillCandidatePort}.
 *
 * <p>Converts between the immutable {@link NewBillCandidate} domain record
 * and the mutable {@link NewBillCandidateEntity} JPA entity. The
 * {@code sourceUrls} list is stored as a pipe-delimited string.
 *
 * <p>This adapter is separate from {@link StateLawAdapter} because Java
 * cannot merge the conflicting {@code findUnreviewed()} return types from
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.LawChangeEventPort}
 * and {@link NewBillCandidatePort} into a single class.
 *
 * <p>The {@link #promote(Long, StateLaw)} method delegates to
 * {@link StateLawPort#upsert(StateLaw)} to create the registry entry,
 * then marks the candidate as reviewed with a link to the promoted law.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class NewBillCandidateAdapter implements NewBillCandidatePort {

    private final NewBillCandidateRepository candidateRepository;
    private final StateLawPort stateLawPort;

    public NewBillCandidateAdapter(NewBillCandidateRepository candidateRepository,
                                   StateLawPort stateLawPort) {
        log.debug("NewBillCandidateAdapter() | candidateRepository={}, stateLawPort={}",
                candidateRepository.getClass().getSimpleName(),
                stateLawPort.getClass().getSimpleName());
        this.candidateRepository = candidateRepository;
        this.stateLawPort = stateLawPort;
    }

    @Override
    public void saveCandidate(NewBillCandidate candidate) {
        log.debug("saveCandidate() | stateCode={}, billNumber={}", candidate.stateCode(), candidate.billNumber());
        NewBillCandidateEntity entity = toEntity(candidate);
        candidateRepository.save(entity);
        log.debug("saveCandidate() | return=void");
    }

    @Override
    public List<NewBillCandidate> findUnreviewed() {
        log.debug("findUnreviewed()");
        List<NewBillCandidateEntity> entities = candidateRepository.findByReviewedFalseOrderByDiscoveredAtDesc();
        List<NewBillCandidate> result = new ArrayList<>();
        for (NewBillCandidateEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findUnreviewed() | return={} candidates", result.size());
        return result;
    }

    @Override
    public void markReviewed(Long candidateId) {
        log.debug("markReviewed() | candidateId={}", candidateId);
        candidateRepository.findById(candidateId).ifPresent(entity -> {
            entity.setReviewed(true);
            candidateRepository.save(entity);
        });
        log.debug("markReviewed() | return=void");
    }

    @Override
    @Transactional
    public void promote(Long candidateId, StateLaw law) {
        log.debug("promote() | candidateId={}, lawId={}", candidateId, law.id());
        stateLawPort.upsert(law);
        candidateRepository.findById(candidateId).ifPresent(entity -> {
            entity.setReviewed(true);
            entity.setPromotedLawId(law.id());
            candidateRepository.save(entity);
        });
        log.debug("promote() | return=void");
    }

    @Override
    public void dismiss(Long candidateId) {
        log.debug("dismiss() | candidateId={}", candidateId);
        candidateRepository.findById(candidateId).ifPresent(entity -> {
            entity.setReviewed(true);
            candidateRepository.save(entity);
        });
        log.debug("dismiss() | return=void");
    }

    // ── Private conversion helpers ────────────────────────────────────────────

    private NewBillCandidateEntity toEntity(NewBillCandidate candidate) {
        NewBillCandidateEntity entity = new NewBillCandidateEntity();
        if (candidate.id() != null) {
            entity.setId(candidate.id());
        }
        entity.setStateCode(candidate.stateCode().name());
        entity.setBillNumber(candidate.billNumber());
        entity.setTitle(candidate.title());
        entity.setSummary(candidate.summary());
        entity.setSourceUrls(PipeDelimitedUtils.join(candidate.sourceUrls()));
        entity.setDiscoveredAt(candidate.discoveredAt());
        entity.setConfidence(candidate.confidence());
        entity.setReviewed(candidate.reviewed());
        entity.setPromotedLawId(candidate.promotedLawId());
        return entity;
    }

    private NewBillCandidate toDomain(NewBillCandidateEntity entity) {
        return new NewBillCandidate(
                entity.getId(),
                StateCode.valueOf(entity.getStateCode()),
                entity.getBillNumber(),
                entity.getTitle(),
                entity.getSummary(),
                PipeDelimitedUtils.split(entity.getSourceUrls()),
                entity.getDiscoveredAt(),
                entity.getConfidence(),
                entity.isReviewed(),
                entity.getPromotedLawId()
        );
    }

}
