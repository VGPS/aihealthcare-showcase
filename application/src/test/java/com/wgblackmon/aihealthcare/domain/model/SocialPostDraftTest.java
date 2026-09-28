package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SocialPostDraft} compact-constructor validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
class SocialPostDraftTest {

    @Test
    void constructor_validFields_creates() {
        SocialPostDraft draft = new SocialPostDraft(
                "LI body", "LI comment", "FB body", "FB comment",
                List.of("Headline A"), "Rationale text", Instant.now());

        assertThat(draft.linkedinBody()).isEqualTo("LI body");
        assertThat(draft.facebookBody()).isEqualTo("FB body");
        assertThat(draft.entriesSelected()).containsExactly("Headline A");
    }

    @Test
    void constructor_nullLinkedinBody_throws() {
        assertThatThrownBy(() -> new SocialPostDraft(
                null, "comment", "FB body", "", List.of(), "rationale", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkedinBody");
    }

    @Test
    void constructor_blankLinkedinBody_throws() {
        assertThatThrownBy(() -> new SocialPostDraft(
                "  ", "comment", "FB body", "", List.of(), "rationale", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkedinBody");
    }

    @Test
    void constructor_nullFacebookBody_throws() {
        assertThatThrownBy(() -> new SocialPostDraft(
                "LI body", "comment", null, "", List.of(), "rationale", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("facebookBody");
    }

    @Test
    void constructor_nullGeneratedAt_throws() {
        assertThatThrownBy(() -> new SocialPostDraft(
                "LI body", "comment", "FB body", "", List.of(), "rationale", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedAt");
    }

    @Test
    void constructor_nullEntriesSelected_defaultsToEmptyList() {
        SocialPostDraft draft = new SocialPostDraft(
                "LI body", "comment", "FB body", "", null, "rationale", Instant.now());

        assertThat(draft.entriesSelected()).isEmpty();
    }

    @Test
    void constructor_emptyLinkedinComment_allowed() {
        SocialPostDraft draft = new SocialPostDraft(
                "LI body", "", "FB body", "", List.of(), "rationale", Instant.now());

        assertThat(draft.linkedinComment()).isEmpty();
    }
}
