package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for managing saved social post drafts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public interface ManageSavedPostsUseCase {

    SavedPost save(SocialPlatform platform, String digestDate, String body, String comment, String yourTake);

    Optional<SavedPost> findById(String postId);

    List<SavedPost> listDrafts();

    SavedPost update(String postId, String body, String comment, String yourTake);

    SavedPost markPosted(String postId);

    SavedPost archive(String postId);

    void delete(String postId);
}
