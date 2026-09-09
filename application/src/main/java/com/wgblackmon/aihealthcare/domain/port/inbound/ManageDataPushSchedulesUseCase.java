package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for managing enterprise push schedules.
 *
 * <p>All methods enforce ENTERPRISE tier access and ownership — a
 * schedule belonging to another owner results in a not-found response,
 * never access-denied.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface ManageDataPushSchedulesUseCase {

    DataPushSchedule create(DataPushSchedule schedule);

    DataPushSchedule update(String scheduleId, String ownerEmail, DataPushSchedule schedule);

    void delete(String scheduleId, String ownerEmail);

    List<DataPushSchedule> list(String ownerEmail);

    DataJob runNow(String scheduleId, String ownerEmail);

    /**
     * Previews the next {@code count} fire times for a cron expression
     * in the given time zone, without creating a schedule.
     */
    List<Instant> previewNextRuns(String cronExpression, String zoneId, int count);
}
