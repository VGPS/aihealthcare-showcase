package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAction;
import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataAccessAuditPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed adapter implementing {@link DataAccessAuditPort}.
 *
 * <p>Append-only — no update or delete operations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class DataAccessAuditAdapter implements DataAccessAuditPort {

    private final EnterpriseDataAuditRepository repository;

    public DataAccessAuditAdapter(EnterpriseDataAuditRepository repository) {
        this.repository = repository;
        log.debug("DataAccessAuditAdapter() | repository={}", repository.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public void append(DataAccessAuditEntry entry) {
        log.debug("append() | action={}, ownerEmail={}, jobId={}",
                entry.action(), entry.ownerEmail(), entry.jobId());
        EnterpriseDataAuditEntity entity = toEntity(entry);
        repository.save(entity);
        log.debug("append() | return=void");
    }

    @Override
    public List<DataAccessAuditEntry> findByOwnerEmail(String ownerEmail, Instant since, int limit) {
        log.debug("findByOwnerEmail() | ownerEmail={}, since={}, limit={}", ownerEmail, since, limit);
        PageRequest page = PageRequest.of(0, limit);
        List<DataAccessAuditEntry> result;
        if (since != null) {
            result = repository
                    .findByOwnerEmailAndOccurredAtAfterOrderByOccurredAtDesc(ownerEmail, since, page)
                    .map(this::toDomain).getContent();
        } else {
            result = repository
                    .findByOwnerEmailOrderByOccurredAtDesc(ownerEmail, page)
                    .map(this::toDomain).getContent();
        }
        log.debug("findByOwnerEmail() | return={} entries", result.size());
        return result;
    }

    // ---- mapping helpers ----

    private EnterpriseDataAuditEntity toEntity(DataAccessAuditEntry entry) {
        EnterpriseDataAuditEntity e = new EnterpriseDataAuditEntity();
        e.setOccurredAt(entry.occurredAt());
        e.setOwnerEmail(entry.ownerEmail());
        e.setJobId(entry.jobId());
        e.setScheduleId(entry.scheduleId());
        e.setAction(entry.action().name());
        e.setOutcome(entry.outcome());
        e.setDetail(entry.detail());
        e.setRowCount(entry.rowCount());
        e.setByteSize(entry.byteSize());
        return e;
    }

    private DataAccessAuditEntry toDomain(EnterpriseDataAuditEntity e) {
        return new DataAccessAuditEntry(
                e.getOccurredAt(),
                e.getOwnerEmail(),
                e.getJobId(),
                e.getScheduleId(),
                DataAccessAction.valueOf(e.getAction()),
                e.getOutcome(),
                e.getDetail(),
                e.getRowCount(),
                e.getByteSize()
        );
    }
}
