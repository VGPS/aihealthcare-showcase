package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReactionHorizon} offsets and intraday classification.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
class ReactionHorizonTest {

    @Test
    void oneHour_offsetAndIntraday() {
        assertThat(ReactionHorizon.ONE_HOUR.offset()).isEqualTo(Duration.ofHours(1));
        assertThat(ReactionHorizon.ONE_HOUR.isIntraday()).isTrue();
    }

    @Test
    void fourHour_offsetAndIntraday() {
        assertThat(ReactionHorizon.FOUR_HOUR.offset()).isEqualTo(Duration.ofHours(4));
        assertThat(ReactionHorizon.FOUR_HOUR.isIntraday()).isTrue();
    }

    @Test
    void oneDay_offsetAndNotIntraday() {
        assertThat(ReactionHorizon.ONE_DAY.offset()).isEqualTo(Duration.ofDays(1));
        assertThat(ReactionHorizon.ONE_DAY.isIntraday()).isFalse();
    }

    @Test
    void threeDay_offsetAndNotIntraday() {
        assertThat(ReactionHorizon.THREE_DAY.offset()).isEqualTo(Duration.ofDays(3));
        assertThat(ReactionHorizon.THREE_DAY.isIntraday()).isFalse();
    }
}
