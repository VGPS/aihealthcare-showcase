package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link DealSignal} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class DealSignalTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validRecord_createsSuccessfully() {
        DealSignal signal = new DealSignal("s1", "a1", "Title",
                DealSignalType.FUNDING, "Acme", "Summary", 0.8, NOW);
        assertThat(signal.signalId()).isEqualTo("s1");
        assertThat(signal.signalType()).isEqualTo(DealSignalType.FUNDING);
        assertThat(signal.confidence()).isEqualTo(0.8);
    }

    @Test
    void blankSignalId_throws() {
        assertThatThrownBy(() -> new DealSignal("", "a1", "Title",
                DealSignalType.FUNDING, "Co", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signalId");
    }

    @Test
    void nullArticleId_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", null, "Title",
                DealSignalType.FUNDING, "Co", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleId");
    }

    @Test
    void blankTitle_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", "a1", "  ",
                DealSignalType.FUNDING, "Co", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullSignalType_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", "a1", "Title",
                null, "Co", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signalType");
    }

    @Test
    void confidenceOutOfRange_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", "a1", "Title",
                DealSignalType.IPO, "Co", "Sum", 1.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void negativeConfidence_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", "a1", "Title",
                DealSignalType.IPO, "Co", "Sum", -0.1, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void nullDetectedAt_throws() {
        assertThatThrownBy(() -> new DealSignal("s1", "a1", "Title",
                DealSignalType.FUNDING, "Co", "Sum", 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectedAt");
    }

    @Test
    void allSignalTypes_haveValues() {
        assertThat(DealSignalType.values()).containsExactly(
                DealSignalType.FUNDING,
                DealSignalType.ACQUISITION,
                DealSignalType.PARTNERSHIP,
                DealSignalType.IPO,
                DealSignalType.PRODUCT_LAUNCH
        );
    }
}
