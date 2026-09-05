package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Driving (inbound) port for resolving which LinkedIn feature post is due on a
 * given date.
 *
 * The rotation is a fixed-length weekday cycle anchored to a start date: each
 * post occupies one (week, weekday) slot, and the cycle repeats once the last
 * week is exhausted. Callers ask for a date and get back the post assigned to
 * that date's slot.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public interface RotateFeaturePostsUseCase {

    /**
     * Returns the post scheduled for the given date.
     *
     * @param date the date to resolve
     * @return the post due that day, or empty on weekends and when the
     *         resolved slot has no post assigned
     */
    Optional<FeaturePost> postFor(LocalDate date);

    /**
     * Returns the next post due on or after the given date, skipping weekends
     * and any unfilled slots. Used so the page still shows something useful
     * when opened on a Saturday.
     *
     * @param date the date to search forward from
     * @return the next scheduled post, or empty when the library holds none
     */
    Optional<FeaturePost> nextPostFrom(LocalDate date);

    /**
     * Returns the posts for the calendar week containing the given date, in
     * Monday-to-Friday order, so the page can show the week at a glance.
     *
     * @param date any date within the week of interest
     * @return that week's posts, in weekday order; may be shorter than five
     *         entries when slots are unfilled
     */
    List<FeaturePost> weekOf(LocalDate date);

    /**
     * Returns the full library in rotation order — week 1 Monday first.
     *
     * @return every post, ordered by slot
     */
    List<FeaturePost> schedule();

    /**
     * Looks up a single post by its library id, for direct linking.
     *
     * @param id the post id
     * @return the matching post, or empty when no post carries that id
     */
    Optional<FeaturePost> findById(String id);
}
