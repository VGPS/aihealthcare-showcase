package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalGatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persistence adapter for the push-delivery approval gate.
 *
 * <p>Absence of a row for a given {@code scheduleId} means the gate is not
 * required — the first {@link #setRequired} call for a schedule creates its row.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Slf4j
@Component
public class PushApprovalGateAdapter implements PushApprovalGatePort {

    private final PushApprovalGateRepository repository;

    public PushApprovalGateAdapter(PushApprovalGateRepository repository) {
        log.debug("PushApprovalGateAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public boolean isRequired(String scheduleId) {
        log.debug("isRequired() | scheduleId={}", scheduleId);
        boolean result = repository.findById(scheduleId)
                .map(PushApprovalGateEntity::isRequired)
                .orElse(false);
        log.debug("isRequired() | return={}", result);
        return result;
    }

    @Override
    public void setRequired(String scheduleId, boolean required) {
        log.debug("setRequired() | scheduleId={}, required={}", scheduleId, required);
        PushApprovalGateEntity entity = repository.findById(scheduleId)
                .orElseGet(() -> {
                    PushApprovalGateEntity newEntity = new PushApprovalGateEntity();
                    newEntity.setScheduleId(scheduleId);
                    return newEntity;
                });
        entity.setRequired(required);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        log.debug("setRequired() | return=void");
    }
}
