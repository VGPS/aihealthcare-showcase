package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying saved social post drafts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface SavedPostPort {

    SavedPost save(SavedPost post);

    Optional<SavedPost> findById(String postId);

    List<SavedPost> findAll();

    List<SavedPost> findByStatus(SavedPostStatus status);

    void delete(String postId);
}
