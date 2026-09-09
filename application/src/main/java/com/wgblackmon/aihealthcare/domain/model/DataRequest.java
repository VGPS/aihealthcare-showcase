package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable submission payload for an enterprise data job.
 *
 * <p>Constructed by the controller from the incoming REST/form request and
 * passed to {@link com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase#submit}.
 * The compact constructor enforces non-blank identifiers, non-null format,
 * positive row limit, and takes a defensive copy of the parameters map.
 *
 * @param jobId         UUID assigned at submission
 * @param ownerEmail    authenticated user's email (tenancy key)
 * @param teamId        optional team scope (null in ED-1)
 * @param mode          PULL or PUSH
 * @param feedId        target feed identifier
 * @param promptId      canned prompt id (nullable)
 * @param promptText    free-text prompt (nullable)
 * @param parameters    key-value parameters (defensive copy taken)
 * @param format        desired export format
 * @param rowLimit      maximum rows requested (server-clamped)
 * @param connectionId  remote connection id for CUSTOMER_REMOTE (nullable)
 * @param plan          resolved query plan (null until resolution step)
 * @param requestedAt   submission timestamp
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataRequest(
        String jobId,
        String ownerEmail,
        String teamId,
        DataJobMode mode,
        String feedId,
        String promptId,
        String promptText,
        Map<String, String> parameters,
        ExportFormat format,
        int rowLimit,
        String connectionId,
        DataQueryPlan plan,
        Instant requestedAt
) {
    public DataRequest {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("DataRequest jobId must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("DataRequest ownerEmail must not be blank");
        }
        if (feedId == null || feedId.isBlank()) {
            throw new IllegalArgumentException("DataRequest feedId must not be blank");
        }
        if (format == null) {
            throw new IllegalArgumentException("DataRequest format must not be null");
        }
        if (rowLimit <= 0) {
            throw new IllegalArgumentException("DataRequest rowLimit must be positive, got " + rowLimit);
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
