package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TierLimits;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TierGatingService}.
 *
 * <p>Verifies that tier limits are resolved correctly and that usage gating
 * predicates return the expected results for each tier and usage state.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
class TierGatingServiceTest {

    private TierGatingService service;

    private static final TierLimits FREE_LIMITS   = new TierLimits(30, 15);
    private static final TierLimits MEMBER_LIMITS = new TierLimits(0, 200);

    @BeforeEach
    void setUp() {
        service = new TierGatingService(FREE_LIMITS, MEMBER_LIMITS);
    }

    @Test
    void getLimits_freeTier_returnsFreeLimits() {
        TierLimits result = service.getLimits(SubscriptionTier.FREE);

        assertThat(result.archiveDays()).isEqualTo(30);
        assertThat(result.monthlyQueryLimit()).isEqualTo(15);
    }

    @Test
    void getLimits_memberTier_returnsMemberLimits() {
        TierLimits result = service.getLimits(SubscriptionTier.MEMBER);

        assertThat(result.archiveDays()).isEqualTo(0);
        assertThat(result.monthlyQueryLimit()).isEqualTo(200);
    }

    @Test
    void getLimits_nullTier_defaultsToFree() {
        TierLimits result = service.getLimits(null);

        assertThat(result).isEqualTo(FREE_LIMITS);
    }

    @Test
    void canQuery_underLimit_returnsTrue() {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 10, 15);

        assertThat(service.canQuery(usage)).isTrue();
    }

    @Test
    void canQuery_atLimit_returnsFalse() {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 15, 15);

        assertThat(service.canQuery(usage)).isFalse();
    }

    @Test
    void canQuery_overLimit_returnsFalse() {
        UsageRecord usage = new UsageRecord("user@example.com", "2026-05", 20, 15);

        assertThat(service.canQuery(usage)).isFalse();
    }

    @Test
    void archiveDaysFor_freeTier_returns30() {
        assertThat(service.archiveDaysFor(SubscriptionTier.FREE)).isEqualTo(30);
    }

    @Test
    void archiveDaysFor_memberTier_returns0() {
        assertThat(service.archiveDaysFor(SubscriptionTier.MEMBER)).isEqualTo(0);
    }

    @Test
    void monthlyQueryLimitFor_freeTier_returns15() {
        assertThat(service.monthlyQueryLimitFor(SubscriptionTier.FREE)).isEqualTo(15);
    }

    @Test
    void monthlyQueryLimitFor_memberTier_returns200() {
        assertThat(service.monthlyQueryLimitFor(SubscriptionTier.MEMBER)).isEqualTo(200);
    }
}
