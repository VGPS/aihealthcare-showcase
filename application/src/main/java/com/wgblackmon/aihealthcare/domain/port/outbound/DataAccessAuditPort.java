package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataAccessAuditEntry;

import java.time.Instant;
import java.util.List;

/**
 * Append-only persistence port for the enterprise data audit trail.
 *
 * <p>Audit entries outlive the artifacts they describe. They are never
 * modified or deleted, providing a durable compliance record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataAccessAuditPort {

    void append(DataAccessAuditEntry entry);

    List<DataAccessAuditEntry> findByOwnerEmail(String ownerEmail, Instant since, int limit);
}
