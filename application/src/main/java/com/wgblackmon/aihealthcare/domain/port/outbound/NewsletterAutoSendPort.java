package com.wgblackmon.aihealthcare.domain.port.outbound;

import java.time.LocalDate;

/**
 * Outbound port for managing the newsletter auto-send override setting.
 *
 * <p>By default, the daily newsletter scheduler auto-sends after generating
 * a draft. When the override is active for a given date, auto-send is
 * suppressed and the draft must be sent manually via the Newsletter Preview UI.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface NewsletterAutoSendPort {

    /**
     * Returns true if auto-send is overridden (suppressed) for the given date.
     *
     * @param date the date to check
     * @return true if auto-send should be skipped
     */
    boolean isOverriddenForDate(LocalDate date);

    /**
     * Sets or clears the auto-send override for the given date.
     *
     * @param date     the date to override
     * @param override true to suppress auto-send, false to allow it
     */
    void setOverride(LocalDate date, boolean override);
}
