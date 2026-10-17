package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link PushApprovalPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Slf4j
@Component
public class PushApprovalRequestAdapter implements PushApprovalPort {

    private final PushApprovalRequestRepository repository;

    public PushApprovalRequestAdapter(PushApprovalRequestRepository repository) {
        log.debug("PushApprovalRequestAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public PushApprovalRequest save(PushApprovalRequest request) {
        log.debug("save() | jobId={}, status={}", request.jobId(), request.status());
        PushApprovalRequestEntity entity = repository.findById(request.jobId())
                .orElseGet(PushApprovalRequestEntity::new);
        entity.setJobId(request.jobId());
        entity.setScheduleId(request.scheduleId());
        entity.setOwnerEmail(request.ownerEmail());
        entity.setRequestedAt(request.requestedAt());
        entity.setStatus(request.status().name());
        entity.setDecidedAt(request.decidedAt());
        entity.setDecidedBy(request.decidedBy());
        PushApprovalRequest result = toDomain(repository.save(entity));
        log.debug("save() | return={}", result);
        return result;
    }

    @Override
    public Optional<PushApprovalRequest> findByJobId(String jobId) {
        log.debug("findByJobId() | jobId={}", jobId);
        Optional<PushApprovalRequest> result = repository.findById(jobId).map(this::toDomain);
        log.debug("findByJobId() | return={}", result);
        return result;
    }

    @Override
    public List<PushApprovalRequest> findPending() {
        log.debug("findPending()");
        List<PushApprovalRequestEntity> entities =
                repository.findByStatusOrderByRequestedAtAsc(PushApprovalStatus.PENDING.name());
        List<PushApprovalRequest> result = new ArrayList<>();
        for (PushApprovalRequestEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findPending() | return={} requests", result.size());
        return result;
    }

    private PushApprovalRequest toDomain(PushApprovalRequestEntity entity) {
        return new PushApprovalRequest(
                entity.getJobId(),
                entity.getScheduleId(),
                entity.getOwnerEmail(),
                entity.getRequestedAt(),
                PushApprovalStatus.valueOf(entity.getStatus()),
                entity.getDecidedAt(),
                entity.getDecidedBy());
    }
}
