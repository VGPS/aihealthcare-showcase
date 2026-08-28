package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TrackedCompanyEntry} validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
class TrackedCompanyEntryTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validEntry_createsSuccessfully() {
        TrackedCompanyEntry entry = new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", NOW);

        assertThat(entry.entryId()).isEqualTo("entry-1");
        assertThat(entry.tickerSymbol()).isEqualTo("DOCS");
        assertThat(entry.companyName()).isEqualTo("Doximity");
        assertThat(entry.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void blankEntryId_throws() {
        assertThatThrownBy(() -> new TrackedCompanyEntry(" ", "DOCS", "Doximity", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entryId");
    }

    @Test
    void blankTickerSymbol_throws() {
        assertThatThrownBy(() -> new TrackedCompanyEntry("entry-1", "", "Doximity", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickerSymbol");
    }

    @Test
    void blankCompanyName_throws() {
        assertThatThrownBy(() -> new TrackedCompanyEntry("entry-1", "DOCS", "", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    void nullPublishedAt_throws() {
        assertThatThrownBy(() -> new TrackedCompanyEntry("entry-1", "DOCS", "Doximity", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedAt");
    }
}
