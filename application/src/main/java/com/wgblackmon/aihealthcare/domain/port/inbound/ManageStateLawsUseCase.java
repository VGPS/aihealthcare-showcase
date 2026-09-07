package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.NewBillCandidate;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port — drives state health-AI legislation registry operations.
 *
 * <p>Query methods expose the full registry for browsing, filtering, and
 * searching. Change-event and candidate methods support the admin review
 * workflow for source-freshness monitoring and new-bill discovery.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface ManageStateLawsUseCase {

    /**
     * Returns all laws in the registry.
     */
    List<StateLaw> getAll();

    /**
     * Returns the law with the given slug id.
     */
    Optional<StateLaw> getById(String id);

    /**
     * Returns all laws for a given state.
     */
    List<StateLaw> getByState(StateCode stateCode);

    /**
     * Returns all laws matching a given category.
     */
    List<StateLaw> getByCategory(LawCategory category);

    /**
     * Returns all laws with a given status.
     */
    List<StateLaw> getByStatus(LawStatus status);

    /**
     * Returns laws with effective dates within the given number of days from now.
     */
    List<StateLaw> getUpcoming(int days);

    /**
     * Full-text search across law titles, bill numbers, requirements, and notes.
     */
    List<StateLaw> search(String query);

    /**
     * Returns all source-change events that have not yet been admin-reviewed.
     */
    List<LawChangeEvent> getUnreviewedChanges();

    /**
     * Marks a change event as reviewed.
     */
    void reviewChange(Long eventId);

    /**
     * Returns all new-bill candidates that have not yet been admin-reviewed.
     */
    List<NewBillCandidate> getUnreviewedCandidates();
}
