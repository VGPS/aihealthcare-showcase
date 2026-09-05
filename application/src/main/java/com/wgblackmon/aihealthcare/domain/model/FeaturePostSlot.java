package com.wgblackmon.aihealthcare.domain.model;

import java.time.DayOfWeek;
import java.util.Objects;

/**
 * Immutable domain record identifying a single position in the LinkedIn
 * feature-post rotation.
 *
 * A slot is a (week, weekday) pair within a fixed-length cycle — week 1
 * Monday, week 1 Tuesday, and so on. Slots are the rotation's coordinate
 * system: the schedule assigns exactly one {@link FeaturePost} to each slot,
 * and {@code FeaturePostRotationService} converts a calendar date into the
 * slot that date falls on.
 *
 * Weekday is restricted to Monday through Friday. Weekend dates have no slot
 * and therefore no post — the rotation is a weekday posting habit, not a
 * seven-day one.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public record FeaturePostSlot(int week, DayOfWeek weekday) {

    public FeaturePostSlot {
        if (week < 1) {
            throw new IllegalArgumentException("week must be 1 or greater, was " + week);
        }
        Objects.requireNonNull(weekday, "weekday must not be null");
        if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(
                    "rotation slots are weekdays only, was " + weekday);
        }
    }

    /**
     * Renders the slot as a stable, sortable key such as {@code W1-MONDAY}.
     * Used as the map key when the schedule is indexed for lookup.
     *
     * @return the slot key
     */
    public String key() {
        return "W" + week + "-" + weekday.name();
    }
}
