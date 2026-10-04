package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for managing the healthcare AI editorial calendar.
 *
 * <p>Implemented by {@link com.wgblackmon.aihealthcare.domain.service.EditorialCalendarService}.
 * Used by web controllers and the seed runner — never by infrastructure adapters.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface ManageEditorialCalendarUseCase {

    /** Returns all items in the calendar regardless of status. */
    List<EditorialItem> getAll();

    Optional<EditorialItem> getById(String id);

    /** Returns all items with the given status. */
    List<EditorialItem> getByStatus(EditorialStatus status);

    /** Returns all PUBLISHED items for the public insights listing. */
    List<EditorialItem> getPublished();

    /**
     * Returns all non-PUBLISHED items sorted by priority tier (P0 first),
     * then by preferred publish date ascending.
     */
    List<EditorialItem> getQueue();

    /**
     * Returns the highest-priority PLANNED item — the one editors should work
     * on next.
     */
    Optional<EditorialItem> getNext();

    /**
     * Advances an item's status by one step in the lifecycle state machine.
     *
     * @param id the item slug
     * @return the updated item
     * @throws IllegalArgumentException if the id does not exist
     */
    EditorialItem advanceStatus(String id);

    /** Returns all items belonging to a named series, ordered by preferred date. */
    List<EditorialItem> getBySeries(String series);
}
