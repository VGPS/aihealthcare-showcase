package com.wgblackmon.aihealthcare.web.controller;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Shared date/time formatting constants for Thymeleaf controllers and renderers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-12
 * @updated 2026-09-12
 */
public final class DisplayFormats {

    private DisplayFormats() {}

    /** "MMM d, yyyy" — e.g. "Sep 12, 2026" */
    public static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    /** "MMM d, yyyy h:mm a z" — e.g. "Sep 12, 2026 7:00 AM CDT" */
    public static final DateTimeFormatter TIMESTAMP_Z =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z");

    /** "MMM d, yyyy HH:mm" — e.g. "Sep 12, 2026 07:00" */
    public static final DateTimeFormatter TIMESTAMP_24H =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    /** "MMM d, yyyy h:mm a" — e.g. "Sep 12, 2026 7:00 AM" */
    public static final DateTimeFormatter NOTE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    /** "MMMM d, yyyy" — e.g. "September 12, 2026" */
    public static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    public static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
}
