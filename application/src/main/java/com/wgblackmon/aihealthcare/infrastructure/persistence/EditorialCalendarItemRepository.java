package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EditorialCalendarItemEntity}.
 *
 * <p>{@code priorityTier} stores enum names (P0, P1, P2) whose alphabetical
 * ordering naturally yields the correct priority sequence, so
 * {@code ORDER BY priority_tier ASC, preferred_date ASC} works without
 * custom mapping.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface EditorialCalendarItemRepository
        extends JpaRepository<EditorialCalendarItemEntity, String> {

    List<EditorialCalendarItemEntity> findByStatusOrderByPriorityTierAscPreferredDateAsc(String status);

    List<EditorialCalendarItemEntity> findByPriorityTierOrderByPreferredDateAsc(String priorityTier);

    Optional<EditorialCalendarItemEntity> findFirstByStatusOrderByPriorityTierAscPreferredDateAsc(String status);

    List<EditorialCalendarItemEntity> findBySeriesOrderByPreferredDateAsc(String series);
}
