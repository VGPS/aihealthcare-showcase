package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link LegalTrendSnapshot} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class LegalTrendSnapshotTest {

    @Test
    void validSnapshot_createsSuccessfully() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "FDA AI", "REGULATION", 10, 3, 3.33,
                TrendDirection.RISING, List.of("a1"));
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, List.of(signal), 42);

        assertThat(snapshot.windowDays()).isEqualTo(30);
        assertThat(snapshot.risingTrends()).hasSize(1);
        assertThat(snapshot.totalKeywords()).isEqualTo(42);
    }

    @Test
    void nullGeneratedAt_throws() {
        assertThatThrownBy(() -> new LegalTrendSnapshot(
                null, 30, List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedAt");
    }

    @Test
    void zeroWindowDays_throws() {
        assertThatThrownBy(() -> new LegalTrendSnapshot(
                Instant.now(), 0, List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowDays");
    }

    @Test
    void nullRisingTrends_throws() {
        assertThatThrownBy(() -> new LegalTrendSnapshot(
                Instant.now(), 30, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("risingTrends");
    }

    @Test
    void risingTrends_areDefensivelyCopied() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "HIPAA", "LITIGATION", 5, 2, 2.5,
                TrendDirection.RISING, List.of());
        java.util.ArrayList<LegalTrendSignal> mutableList = new java.util.ArrayList<>();
        mutableList.add(signal);

        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, mutableList, 10);

        assertThatThrownBy(() -> snapshot.risingTrends().add(signal))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
