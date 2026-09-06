package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExcerptTruncator}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
class ExcerptTruncatorTest {

    @Test
    @DisplayName("truncate() returns null input unchanged")
    void truncate_nullInput_returnsNull() {
        assertThat(ExcerptTruncator.truncate(null, 500)).isNull();
    }

    @Test
    @DisplayName("truncate() returns short text unchanged")
    void truncate_shortText_returnsUnchanged() {
        String text = "Short text under the limit.";
        assertThat(ExcerptTruncator.truncate(text, 500)).isEqualTo(text);
    }

    @Test
    @DisplayName("truncate() returns text exactly at limit unchanged")
    void truncate_exactLimit_returnsUnchanged() {
        String text = "A".repeat(500);
        assertThat(ExcerptTruncator.truncate(text, 500)).isEqualTo(text);
    }

    @Test
    @DisplayName("truncate() cuts at sentence boundary when available")
    void truncate_sentenceBoundary_cutsAtPeriod() {
        String text = "First sentence. Second sentence. Third sentence that goes beyond the limit and should be cut.";
        String result = ExcerptTruncator.truncate(text, 35);

        assertThat(result).isEqualTo("First sentence. Second sentence.");
        assertThat(result).endsWith(".");
        assertThat(result.length()).isLessThanOrEqualTo(35);
    }

    @Test
    @DisplayName("truncate() cuts at exclamation mark boundary")
    void truncate_exclamationBoundary() {
        String text = "Breaking news! This is a really long article that continues beyond the cap.";
        String result = ExcerptTruncator.truncate(text, 20);

        assertThat(result).isEqualTo("Breaking news!");
    }

    @Test
    @DisplayName("truncate() cuts at question mark boundary")
    void truncate_questionBoundary() {
        String text = "Is AI safe? Many researchers have debated this question extensively.";
        String result = ExcerptTruncator.truncate(text, 15);

        assertThat(result).isEqualTo("Is AI safe?");
    }

    @Test
    @DisplayName("truncate() falls back to space with ellipsis when no sentence boundary")
    void truncate_noSentenceBoundary_cutsAtSpace() {
        String text = "One long sentence without any punctuation that goes on and on and on";
        String result = ExcerptTruncator.truncate(text, 30);

        assertThat(result).endsWith("...");
        assertThat(result.length()).isLessThanOrEqualTo(33); // 30 + "..."
    }

    @Test
    @DisplayName("truncate() handles text with no spaces and no sentence boundary")
    void truncate_noSpaces_truncatesAtLimit() {
        String text = "A".repeat(1000);
        String result = ExcerptTruncator.truncate(text, 500);

        assertThat(result).isEqualTo("A".repeat(500) + "...");
    }

    @Test
    @DisplayName("truncate() with 500 cap matches COMPETITOR tier setting")
    void truncate_competitorCap() {
        String longContent = "AI healthcare startup raises funding. ".repeat(50);
        String result = ExcerptTruncator.truncate(longContent, 500);

        assertThat(result.length()).isLessThanOrEqualTo(500);
        assertThat(result).endsWith(".");
    }

    @Test
    @DisplayName("truncate() with 2000 cap matches RSS tier setting")
    void truncate_rssCap() {
        String longContent = "Healthcare AI research published. ".repeat(200);
        String result = ExcerptTruncator.truncate(longContent, 2000);

        assertThat(result.length()).isLessThanOrEqualTo(2000);
        assertThat(result).endsWith(".");
    }
}
