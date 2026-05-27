package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TierLimits;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain service that evaluates feature access and usage limits per subscription tier.
 *
 * <p>Holds the configured {@link TierLimits} for each tier and provides simple
 * predicate methods that controllers and services can call to decide whether a
 * subscriber is allowed to perform a gated action (e.g. an AI research query).
 *
 * <p>This class carries no Spring annotations; it is wired as a {@code @Bean} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Slf4j
public class TierGatingService {

    private final TierLimits freeLimits;
    private final TierLimits memberLimits;

    public TierGatingService(TierLimits freeLimits, TierLimits memberLimits) {
        log.debug("TierGatingService() | freeLimits={}, memberLimits={}", freeLimits, memberLimits);
        this.freeLimits   = freeLimits;
        this.memberLimits = memberLimits;
    }

    /**
     * Returns the feature limits for the given tier.
     *
     * @param tier the subscription tier; {@code null} defaults to FREE.
     * @return the corresponding {@link TierLimits}; never {@code null}.
     */
    public TierLimits getLimits(SubscriptionTier tier) {
        log.debug("getLimits() | tier={}", tier);

        TierLimits result;
        if (tier == SubscriptionTier.MEMBER) {
            result = memberLimits;
        } else {
            result = freeLimits;
        }

        log.debug("getLimits() | return={}", result);
        return result;
    }

    /**
     * Returns {@code true} if the subscriber has remaining AI queries this month.
     *
     * @param usage the subscriber's current usage record for the month.
     * @return whether the subscriber is under their query limit.
     */
    public boolean canQuery(UsageRecord usage) {
        log.debug("canQuery() | email={}, count={}, limit={}", usage.email(), usage.queryCount(), usage.queryLimit());

        boolean result = usage.queryCount() < usage.queryLimit();

        log.debug("canQuery() | return={}", result);
        return result;
    }

    /**
     * Returns the archive depth (in days) for the given tier.
     * A return value of 0 means unlimited archive access.
     *
     * @param tier the subscription tier; {@code null} defaults to FREE.
     * @return number of days of archive access; 0 = unlimited.
     */
    public int archiveDaysFor(SubscriptionTier tier) {
        log.debug("archiveDaysFor() | tier={}", tier);

        int result = getLimits(tier).archiveDays();

        log.debug("archiveDaysFor() | return={}", result);
        return result;
    }

    /**
     * Returns the monthly query limit for the given tier.
     *
     * @param tier the subscription tier; {@code null} defaults to FREE.
     * @return maximum AI queries per month for this tier.
     */
    public int monthlyQueryLimitFor(SubscriptionTier tier) {
        log.debug("monthlyQueryLimitFor() | tier={}", tier);

        int result = getLimits(tier).monthlyQueryLimit();

        log.debug("monthlyQueryLimitFor() | return={}", result);
        return result;
    }
}
