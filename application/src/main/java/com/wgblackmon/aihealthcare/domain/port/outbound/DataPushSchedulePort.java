package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for enterprise push schedules.
 *
 * <p>Ownership is resolved <em>in the query</em> — lookups by
 * {@code scheduleId} always include {@code ownerEmail}. A schedule
 * belonging to another owner returns {@code Optional.empty()}, never
 * an access-denied error.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataPushSchedulePort {

    DataPushSchedule save(DataPushSchedule schedule);

    Optional<DataPushSchedule> findByScheduleIdAndOwnerEmail(String scheduleId, String ownerEmail);

    List<DataPushSchedule> findByOwnerEmail(String ownerEmail);

    /**
     * Returns active schedules whose {@code nextRunAt} is at or before {@code now},
     * ordered by {@code nextRunAt} ascending, bounded by {@code limit}.
     */
    List<DataPushSchedule> findDue(Instant now, int limit);

    /**
     * Atomically claims a schedule for firing.
     *
     * <p>Performs a conditional UPDATE guarded on {@code next_run_at} still
     * equalling {@code observedNextRunAt}. Returns {@code true} only when
     * exactly one row was updated. This single-row guarantee ensures a
     * schedule fires exactly once even if two sweeps overlap or a second
     * instance is ever introduced.
     *
     * <p>{@code next_run_at} advances <em>before</em> the job runs, so a
     * crashing job cannot re-fire in a tight loop.
     *
     * @param scheduleId        the schedule to claim
     * @param observedNextRunAt the value the sweeper observed
     * @param newNextRunAt      the next fire time after this run
     * @param now               current timestamp for {@code last_run_at}
     * @return true if the claim succeeded (exactly one row updated)
     */
    boolean claim(String scheduleId, Instant observedNextRunAt, Instant newNextRunAt, Instant now);

    /**
     * Records the outcome of a scheduled run.
     */
    void recordOutcome(String scheduleId, String jobId, DataJobStatus status, Instant completedAt);

    /**
     * Deactivates a schedule, recording the reason.
     */
    void deactivate(String scheduleId, String reason);

    /**
     * Deletes a schedule owned by the given email.
     */
    void delete(String scheduleId, String ownerEmail);
}
