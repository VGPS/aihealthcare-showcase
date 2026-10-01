package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Persistable social media post draft that can be edited before publishing.
 *
 * <p>Saved from either the template-based generator or the agent draft workflow.
 * Bill can edit {@code body} and {@code comment} via TinyMCE, add a personal
 * {@code yourTake} prefix, then mark the post as POSTED once published.
 *
 * <p>The {@code comment} field holds the first comment (LinkedIn) or expanded
 * detail comment (Facebook) — nullable when not applicable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public record SavedPost(
        String postId,
        SocialPlatform platform,
        String digestDate,
        String body,
        String comment,
        String yourTake,
        SavedPostStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant postedAt
) {
    public SavedPost {
        if (postId == null || postId.isBlank()) throw new IllegalArgumentException("postId must not be blank");
        if (platform == null) throw new IllegalArgumentException("platform must not be null");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("body must not be blank");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
    }
}
