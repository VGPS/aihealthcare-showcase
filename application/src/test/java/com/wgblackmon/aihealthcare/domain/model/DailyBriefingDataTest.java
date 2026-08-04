package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link DailyBriefingData} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class DailyBriefingDataTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);

    @Test
    void validData_constructsSuccessfully() {
        DailyBriefingData data = new DailyBriefingData(
                "Test User", "test@example.com", TODAY,
                List.of(), List.of(), List.of());

        assertThat(data.subscriberName()).isEqualTo("Test User");
        assertThat(data.subscriberEmail()).isEqualTo("test@example.com");
        assertThat(data.briefingDate()).isEqualTo(TODAY);
        assertThat(data.recentMatches()).isEmpty();
        assertThat(data.watchedCompanySentiments()).isEmpty();
        assertThat(data.recentNotes()).isEmpty();
    }

    @Test
    void nullEmail_throwsException() {
        assertThatThrownBy(() -> new DailyBriefingData(
                "User", null, TODAY, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscriberEmail");
    }

    @Test
    void blankEmail_throwsException() {
        assertThatThrownBy(() -> new DailyBriefingData(
                "User", "  ", TODAY, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscriberEmail");
    }

    @Test
    void nullBriefingDate_throwsException() {
        assertThatThrownBy(() -> new DailyBriefingData(
                "User", "test@example.com", null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("briefingDate");
    }

    @Test
    void nullMatchesList_throwsException() {
        assertThatThrownBy(() -> new DailyBriefingData(
                "User", "test@example.com", TODAY, null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recentMatches");
    }

    @Test
    void defensiveCopy_preventsExternalMutation() {
        List<WatchlistMatch> matches = new ArrayList<>();
        matches.add(new WatchlistMatch("m1", "i1", "a1", Instant.now(), "snippet"));

        DailyBriefingData data = new DailyBriefingData(
                "User", "test@example.com", TODAY, matches, List.of(), List.of());

        assertThatThrownBy(() -> data.recentMatches().add(
                new WatchlistMatch("m2", "i2", "a2", Instant.now(), null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
