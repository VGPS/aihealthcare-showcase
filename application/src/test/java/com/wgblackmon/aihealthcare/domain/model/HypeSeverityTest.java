package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enum coverage test for {@link HypeSeverity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-24
 * @updated 2026-11-24
 */
class HypeSeverityTest {

    @Test
    void allValues_arePresent() {
        assertThat(HypeSeverity.values()).containsExactlyInAnyOrder(
                HypeSeverity.MILD,
                HypeSeverity.MODERATE,
                HypeSeverity.SEVERE
        );
    }

    @Test
    void valueOf_roundTrips() {
        for (HypeSeverity s : HypeSeverity.values()) {
            assertThat(HypeSeverity.valueOf(s.name())).isEqualTo(s);
        }
    }
}
