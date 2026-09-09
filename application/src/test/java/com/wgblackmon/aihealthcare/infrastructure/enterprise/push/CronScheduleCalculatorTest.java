package com.wgblackmon.aihealthcare.infrastructure.enterprise.push;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CronScheduleCalculator} — cron validation, zone
 * validation, minimum interval enforcement, and DST-crossing computation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class CronScheduleCalculatorTest {

    private final CronScheduleCalculator calculator = new CronScheduleCalculator(15);

    @Test
    void validCronAndZone_isValidTrue() {
        assertThat(calculator.isValid("0 0 7 * * MON-FRI", "America/Chicago")).isTrue();
    }

    @Test
    void invalidCron_isValidFalse() {
        assertThat(calculator.isValid("not a cron", "UTC")).isFalse();
    }

    @Test
    void unknownZone_isValidFalse() {
        assertThat(calculator.isValid("0 0 7 * * MON-FRI", "Fake/Zone")).isFalse();
    }

    @Test
    void validate_invalidCron_throwsWithCronMessage() {
        assertThatThrownBy(() -> calculator.validate("bad", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cron expression");
    }

    @Test
    void validate_unknownZone_throwsWithZoneMessage() {
        assertThatThrownBy(() -> calculator.validate("0 0 7 * * MON-FRI", "Fake/Zone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown time zone");
    }

    @Test
    void validate_intervalBelowMinimum_throwsWithIntervalMessage() {
        assertThatThrownBy(() -> calculator.validate("0 * * * * *", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below the minimum");
    }

    @Test
    void validate_validExpression_noException() {
        calculator.validate("0 0 7 * * MON-FRI", "America/Chicago");
    }

    @Test
    void nextRunAfter_weekdaysAt7AM_Chicago() {
        // Monday 2026-09-07 06:00 CT → next should be Mon 2026-09-07 07:00 CT
        ZonedDateTime mon = ZonedDateTime.of(2026, 9, 7, 6, 0, 0, 0, ZoneId.of("America/Chicago"));
        Instant after = mon.toInstant();

        Instant next = calculator.nextRunAfter("0 0 7 * * MON-FRI", "America/Chicago", after);

        ZonedDateTime expected = ZonedDateTime.of(2026, 9, 7, 7, 0, 0, 0, ZoneId.of("America/Chicago"));
        assertThat(next).isEqualTo(expected.toInstant());
    }

    @Test
    void nextRuns_threeFireTimes_weekdays() {
        // Friday 2026-09-11 08:00 CT → should get Mon, Tue, Wed of next week
        ZonedDateTime fri = ZonedDateTime.of(2026, 9, 11, 8, 0, 0, 0, ZoneId.of("America/Chicago"));
        Instant after = fri.toInstant();

        List<Instant> runs = calculator.nextRuns("0 0 7 * * MON-FRI", "America/Chicago", after, 3);

        assertThat(runs).hasSize(3);
        // Next fire: Mon 9/14 07:00 CT
        ZonedDateTime mon = ZonedDateTime.of(2026, 9, 14, 7, 0, 0, 0, ZoneId.of("America/Chicago"));
        assertThat(runs.get(0)).isEqualTo(mon.toInstant());
    }

    @Test
    void nextRuns_dstSpringForward_chicagoMarch2026() {
        // DST spring forward: 2026-03-08 at 2:00 AM CT → clocks move to 3:00 AM
        // After DST: UTC offset changes from -06:00 to -05:00
        // 07:00 CT before DST = 13:00 UTC; 07:00 CT after DST = 12:00 UTC
        ZonedDateTime beforeDst = ZonedDateTime.of(2026, 3, 6, 8, 0, 0, 0, ZoneId.of("America/Chicago"));
        Instant after = beforeDst.toInstant();

        List<Instant> runs = calculator.nextRuns("0 0 7 * * MON-FRI", "America/Chicago", after, 3);

        // Fri 3/6 already past 07:00, so:
        // Mon 3/9 07:00 CT (AFTER DST) = 12:00 UTC (CDT = UTC-5)
        // Tue 3/10 07:00 CT = 12:00 UTC
        // Wed 3/11 07:00 CT = 12:00 UTC
        for (Instant run : runs) {
            ZonedDateTime inChicago = run.atZone(ZoneId.of("America/Chicago"));
            assertThat(inChicago.getHour()).isEqualTo(7);
        }

        // The UTC hour should be 12 (CDT) not 13 (CST) after spring forward
        ZonedDateTime firstRunChicago = runs.get(0).atZone(ZoneId.of("America/Chicago"));
        assertThat(firstRunChicago.getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
    }

    @Test
    void nextRuns_dstFallBack_chicagoNovember2026() {
        // DST fall back: 2026-11-01 at 2:00 AM CT → clocks move to 1:00 AM
        // Before fall-back: UTC offset is -05:00 (CDT); after: -06:00 (CST)
        ZonedDateTime beforeFallBack = ZonedDateTime.of(2026, 10, 30, 8, 0, 0, 0, ZoneId.of("America/Chicago"));
        Instant after = beforeFallBack.toInstant();

        List<Instant> runs = calculator.nextRuns("0 0 7 * * MON-FRI", "America/Chicago", after, 5);

        for (Instant run : runs) {
            ZonedDateTime inChicago = run.atZone(ZoneId.of("America/Chicago"));
            assertThat(inChicago.getHour()).isEqualTo(7);
        }

        // Runs before Nov 1: CDT (UTC-5); runs after: CST (UTC-6)
        // The UTC hours should shift from 12 to 13
        ZonedDateTime lastRun = runs.get(runs.size() - 1).atZone(ZoneId.of("America/Chicago"));
        assertThat(lastRun.getOffset().getTotalSeconds()).isEqualTo(-6 * 3600);
    }

    @Test
    void everySecond_rejectedByMinInterval() {
        assertThatThrownBy(() -> calculator.validate("* * * * * *", "UTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below the minimum");
    }

    @Test
    void every15Minutes_acceptedByMinInterval() {
        calculator.validate("0 */15 * * * *", "UTC");
    }

    @Test
    void hourly_acceptedByMinInterval() {
        calculator.validate("0 0 * * * *", "UTC");
    }
}
