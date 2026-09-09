package com.wgblackmon.aihealthcare.infrastructure.enterprise.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates Spring 6-field cron expressions and computes next-run instants
 * in a customer's time zone.
 *
 * <p>Wraps Spring's {@link CronExpression} and {@link ZoneId} so the domain
 * service can validate and compute without importing Spring directly.
 * All computation uses the customer's zone for DST correctness; all results
 * are converted to {@link Instant} (UTC) for storage.
 *
 * <p>Rejects expressions whose minimum interval between consecutive fires
 * is below the configured {@code minIntervalMinutes} (default 15) to prevent
 * customers from flooding the job queue.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class CronScheduleCalculator {

    private final int minIntervalMinutes;

    public CronScheduleCalculator(
            @org.springframework.beans.factory.annotation.Value(
                    "${aihealthcare.enterprise.data.push.min-interval-minutes:15}") int minIntervalMinutes) {
        this.minIntervalMinutes = minIntervalMinutes;
        log.debug("CronScheduleCalculator() | minIntervalMinutes={}", minIntervalMinutes);
    }

    /**
     * Returns {@code true} if the cron expression is a valid Spring 6-field
     * format and the zone id is recognised by {@link ZoneId#of}.
     */
    public boolean isValid(String cronExpression, String zoneId) {
        log.debug("isValid() | cronExpression={}, zoneId={}", cronExpression, zoneId);
        boolean result = parseCron(cronExpression) != null && parseZone(zoneId) != null;
        log.debug("isValid() | return={}", result);
        return result;
    }

    /**
     * Validates the cron expression, zone, and minimum interval, throwing
     * with a specific message on each failure.
     *
     * @throws IllegalArgumentException with a message naming the specific
     *         validation failure (cron, zone, or interval)
     */
    public void validate(String cronExpression, String zoneId) {
        log.debug("validate() | cronExpression={}, zoneId={}", cronExpression, zoneId);
        CronExpression cron = parseCron(cronExpression);
        if (cron == null) {
            throw new IllegalArgumentException(
                    "Invalid cron expression (expected Spring 6-field format: second minute hour day month weekday): "
                    + cronExpression);
        }
        ZoneId zone = parseZone(zoneId);
        if (zone == null) {
            throw new IllegalArgumentException("Unknown time zone: " + zoneId);
        }
        checkMinInterval(cron, zone);
        log.debug("validate() | return=void");
    }

    /**
     * Returns the next fire time after the given instant.
     */
    public Instant nextRunAfter(String cronExpression, String zoneId, Instant after) {
        log.debug("nextRunAfter() | cronExpression={}, zoneId={}, after={}", cronExpression, zoneId, after);
        CronExpression cron = CronExpression.parse(cronExpression);
        ZoneId zone = ZoneId.of(zoneId);
        LocalDateTime ldt = LocalDateTime.ofInstant(after, zone);
        LocalDateTime next = cron.next(ldt);
        if (next == null) {
            throw new IllegalStateException("Cron expression has no next fire time after " + after);
        }
        Instant result = next.atZone(zone).toInstant();
        log.debug("nextRunAfter() | return={}", result);
        return result;
    }

    /**
     * Returns the next {@code count} fire times after the given instant.
     */
    public List<Instant> nextRuns(String cronExpression, String zoneId, Instant after, int count) {
        log.debug("nextRuns() | cronExpression={}, zoneId={}, after={}, count={}", cronExpression, zoneId, after, count);
        CronExpression cron = CronExpression.parse(cronExpression);
        ZoneId zone = ZoneId.of(zoneId);
        List<Instant> results = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.ofInstant(after, zone);
        for (int i = 0; i < count; i++) {
            LocalDateTime next = cron.next(cursor);
            if (next == null) {
                break;
            }
            results.add(next.atZone(zone).toInstant());
            cursor = next;
        }
        log.debug("nextRuns() | return={} instants", results.size());
        return results;
    }

    private CronExpression parseCron(String expression) {
        try {
            return CronExpression.parse(expression);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ZoneId parseZone(String zoneId) {
        try {
            return ZoneId.of(zoneId);
        } catch (Exception e) {
            return null;
        }
    }

    private void checkMinInterval(CronExpression cron, ZoneId zone) {
        ZonedDateTime reference = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, zone);
        LocalDateTime first = cron.next(reference.toLocalDateTime());
        if (first == null) {
            return;
        }
        LocalDateTime second = cron.next(first);
        if (second == null) {
            return;
        }
        long gapMinutes = ChronoUnit.MINUTES.between(first, second);
        if (gapMinutes < minIntervalMinutes) {
            throw new IllegalArgumentException(
                    "Cron interval (" + gapMinutes + " minutes) is below the minimum of "
                    + minIntervalMinutes + " minutes");
        }
    }
}
