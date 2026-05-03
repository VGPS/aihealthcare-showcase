package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link NewsletterRunEntity}.
 *
 * <p>Inherits {@code findById}, {@code save}, and {@code findAll} from
 * {@link JpaRepository}.  Slice 10 added aggregate query methods for the
 * analytics dashboard.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-05-03
 */
public interface NewsletterRunRepository extends JpaRepository<NewsletterRunEntity, String> {

    /**
     * Returns run counts grouped by {@code status}.
     * Each element is a two-element {@code Object[]} where index 0 is the
     * {@link com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus} enum
     * and index 1 is the {@code Long} count.
     *
     * @return list of [status, count] pairs
     */
    @Query("SELECT n.status, COUNT(n) FROM NewsletterRunEntity n GROUP BY n.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Returns the {@code generatedAt} timestamp of the most recently created run,
     * or empty if no runs exist.
     *
     * @return most recent generation instant, or empty
     */
    @Query("SELECT MAX(n.generatedAt) FROM NewsletterRunEntity n")
    Optional<Instant> findMostRecentGeneratedAt();
}
