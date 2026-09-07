package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;

import java.util.List;

/**
 * Outbound port for recording and querying {@link LawChangeEvent} records.
 *
 * <p>Change events are created by the source monitor when a law's source
 * URL content changes. Events remain unreviewed until an admin acknowledges
 * them via {@link #markReviewed(Long)}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface LawChangeEventPort {

    /**
     * Persists a new change event.
     */
    void recordChangeEvent(LawChangeEvent event);

    /**
     * Returns all change events that have not yet been reviewed.
     */
    List<LawChangeEvent> findUnreviewed();

    /**
     * Marks a change event as reviewed by an admin.
     */
    void markReviewed(Long eventId);
}
