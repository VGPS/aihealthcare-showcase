package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link SavedPost} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
class SavedPostTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void construct_validFields_succeeds() {
        SavedPost post = new SavedPost("id-1", SocialPlatform.LINKEDIN, "2026-10-01",
                "Post body text", "First comment", "My take",
                SavedPostStatus.DRAFT, NOW, NOW, null);

        assertThat(post.postId()).isEqualTo("id-1");
        assertThat(post.platform()).isEqualTo(SocialPlatform.LINKEDIN);
        assertThat(post.status()).isEqualTo(SavedPostStatus.DRAFT);
        assertThat(post.postedAt()).isNull();
    }

    @Test
    void construct_nullPostId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SavedPost(null, SocialPlatform.LINKEDIN, "2026-10-01",
                "body", null, null, SavedPostStatus.DRAFT, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_blankBody_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SavedPost("id-1", SocialPlatform.FACEBOOK, "2026-10-01",
                "   ", null, null, SavedPostStatus.DRAFT, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_nullStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SavedPost("id-1", SocialPlatform.FACEBOOK, "2026-10-01",
                "body", null, null, null, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SavedPost("id-1", SocialPlatform.FACEBOOK, "2026-10-01",
                "body", null, null, SavedPostStatus.DRAFT, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savedPostStatus_isTerminal_postedIsTerminal() {
        assertThat(SavedPostStatus.POSTED.isTerminal()).isTrue();
    }

    @Test
    void savedPostStatus_isTerminal_archivedIsTerminal() {
        assertThat(SavedPostStatus.ARCHIVED.isTerminal()).isTrue();
    }

    @Test
    void savedPostStatus_isTerminal_draftIsNotTerminal() {
        assertThat(SavedPostStatus.DRAFT.isTerminal()).isFalse();
    }

    @Test
    void socialPlatform_enumValues() {
        assertThat(SocialPlatform.values()).containsExactlyInAnyOrder(
                SocialPlatform.LINKEDIN, SocialPlatform.FACEBOOK);
    }

    @Test
    void construct_nullCommentAndYourTake_allowed() {
        SavedPost post = new SavedPost("id-2", SocialPlatform.FACEBOOK, "2026-10-01",
                "body text", null, null, SavedPostStatus.DRAFT, NOW, NOW, null);

        assertThat(post.comment()).isNull();
        assertThat(post.yourTake()).isNull();
    }
}
