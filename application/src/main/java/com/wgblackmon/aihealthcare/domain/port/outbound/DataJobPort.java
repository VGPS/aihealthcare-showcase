package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for enterprise data jobs.
 *
 * <p>Every read method that accepts an owner email resolves ownership
 * <em>in the query</em>, never by id followed by a check. A job belonging
 * to another owner returns {@code Optional.empty()}, not an access-denied
 * error, so job ids are not enumerable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08 — ED-2 findByJobId, countPushRunsSince
 */
public interface DataJobPort {

    DataJob save(DataJob job);

    Optional<DataJob> findByJobIdAndOwnerEmail(String jobId, String ownerEmail);

    /**
     * Finds a job by id without owner check — used exclusively by the signed
     * download path where the HMAC token is the authorization, not the session.
     */
    Optional<DataJob> findByJobId(String jobId);

    List<DataJob> findByOwnerEmail(String ownerEmail, int page, int size);

    int countActiveByOwnerEmail(String ownerEmail);

    void updateStatus(String jobId, DataJobStatus status, String errorType, String errorMessage,
                      Integer rowCount, Long byteSize, String contentSha256,
                      String artifactPath, Instant completedAt);

    void touchHeartbeat(String jobId, Instant heartbeatAt);

    int countPushRunsSince(String ownerEmail, Instant since);

    List<DataJob> findStaleRunning(Instant heartbeatBefore);

    List<DataJob> findExpired(Instant now);
}
