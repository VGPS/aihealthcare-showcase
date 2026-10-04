package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link EditorialItem} records.
 *
 * <p>Implemented by {@code EditorialCalendarItemAdapter} in
 * {@code infrastructure.persistence}. The domain service depends only on this
 * interface — no JPA or Spring dependencies enter the domain.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface EditorialCalendarPort {

    /** Idempotent upsert by {@code id}. */
    void upsert(EditorialItem item);

    List<EditorialItem> findAll();

    Optional<EditorialItem> findById(String id);

    List<EditorialItem> findByStatus(EditorialStatus status);

    List<EditorialItem> findByPriority(EditorialPriority priority);

    /** Updates only the status field for the given item id. */
    void updateStatus(String id, EditorialStatus status);

    /**
     * Returns the next recommended item: first PLANNED item ordered by
     * {@code priority_tier ASC}, then {@code preferred_date ASC}.
     */
    Optional<EditorialItem> findNext();

    /** Returns all items belonging to the given series slug, ordered by preferred date. */
    List<EditorialItem> findBySeries(String series);
}
