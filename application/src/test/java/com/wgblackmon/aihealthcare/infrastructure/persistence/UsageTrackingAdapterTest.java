package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TierLimits;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link UsageTrackingAdapter} using an embedded H2 database.
 *
 * <p>{@code @DataJpaTest} loads only the JPA slice.  {@link SubscriberPort} and
 * {@link TierGatingService} are mocked since the adapter depends on them for
 * resolving tier limits when creating new usage records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@DataJpaTest
@Import(UsageTrackingAdapter.class)
class UsageTrackingAdapterTest {

    @Autowired
    private UsageTrackingAdapter adapter;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @MockitoBean
    private TierGatingService tierGatingService;

    private static final String EMAIL      = "test@example.com";
    private static final String YEAR_MONTH = "2026-05";

    @BeforeEach
    void setUp() {
        // Default: unknown subscriber → FREE tier → 15 queries/month
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(tierGatingService.monthlyQueryLimitFor(SubscriptionTier.FREE)).thenReturn(15);
    }

    @Test
    void getOrCreateUsage_newRecord_createsWithZeroCount() {
        UsageRecord result = adapter.getOrCreateUsage(EMAIL, YEAR_MONTH);

        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.yearMonth()).isEqualTo(YEAR_MONTH);
        assertThat(result.queryCount()).isZero();
        assertThat(result.queryLimit()).isEqualTo(15);
    }

    @Test
    void getOrCreateUsage_existingRecord_returnsExisting() {
        adapter.getOrCreateUsage(EMAIL, YEAR_MONTH);
        adapter.incrementAndGet(EMAIL, YEAR_MONTH);

        UsageRecord result = adapter.getOrCreateUsage(EMAIL, YEAR_MONTH);

        assertThat(result.queryCount()).isEqualTo(1);
    }

    @Test
    void getOrCreateUsage_memberTier_useMemberLimit() {
        Subscriber memberSub = new Subscriber(EMAIL, "Test", true, Instant.now(), SubscriptionTier.MEMBER);
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.of(memberSub));
        when(tierGatingService.monthlyQueryLimitFor(SubscriptionTier.MEMBER)).thenReturn(200);

        UsageRecord result = adapter.getOrCreateUsage(EMAIL, YEAR_MONTH);

        assertThat(result.queryLimit()).isEqualTo(200);
    }

    @Test
    void incrementAndGet_incrementsCount() {
        adapter.getOrCreateUsage(EMAIL, YEAR_MONTH);

        UsageRecord first  = adapter.incrementAndGet(EMAIL, YEAR_MONTH);
        UsageRecord second = adapter.incrementAndGet(EMAIL, YEAR_MONTH);

        assertThat(first.queryCount()).isEqualTo(1);
        assertThat(second.queryCount()).isEqualTo(2);
    }

    @Test
    void incrementAndGet_noExistingRecord_createsAndIncrements() {
        UsageRecord result = adapter.incrementAndGet(EMAIL, YEAR_MONTH);

        assertThat(result.queryCount()).isEqualTo(1);
        assertThat(result.queryLimit()).isEqualTo(15);
    }

    @Test
    void findAllByYearMonth_returnsMatchingRecords() {
        adapter.getOrCreateUsage("a@example.com", YEAR_MONTH);
        adapter.getOrCreateUsage("b@example.com", YEAR_MONTH);
        adapter.getOrCreateUsage("c@example.com", "2026-06");

        List<UsageRecord> result = adapter.findAllByYearMonth(YEAR_MONTH);

        assertThat(result).hasSize(2);
    }
}
