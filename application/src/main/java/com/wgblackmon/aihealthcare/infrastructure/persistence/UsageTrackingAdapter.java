package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link UsageTrackingPort}.
 *
 * <p>Translates between the immutable {@link UsageRecord} domain record and
 * the mutable {@link UsageRecordEntity} JPA entity.  When creating a new usage
 * record, the adapter looks up the subscriber's tier via {@link SubscriberPort}
 * and sets the query limit from {@link TierGatingService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Slf4j
@Component
public class UsageTrackingAdapter implements UsageTrackingPort {

    private final UsageRecordRepository repository;
    private final SubscriberPort        subscriberPort;
    private final TierGatingService     tierGatingService;

    public UsageTrackingAdapter(UsageRecordRepository repository,
                                SubscriberPort subscriberPort,
                                TierGatingService tierGatingService) {
        log.debug("UsageTrackingAdapter() | repository={}, subscriberPort={}, tierGatingService={}",
                  repository.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  tierGatingService.getClass().getSimpleName());
        this.repository       = repository;
        this.subscriberPort   = subscriberPort;
        this.tierGatingService = tierGatingService;
    }

    @Override
    public UsageRecord getOrCreateUsage(String email, String yearMonth) {
        log.debug("getOrCreateUsage() | email={}, yearMonth={}", email, yearMonth);

        Optional<UsageRecordEntity> existing = repository.findByEmailAndYearMonth(email, yearMonth);
        if (existing.isPresent()) {
            UsageRecord result = toDomain(existing.get());
            log.debug("getOrCreateUsage() | return={} (existing)", result);
            return result;
        }

        int limit = resolveQueryLimit(email);
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.setEmail(email);
        entity.setYearMonth(yearMonth);
        entity.setQueryCount(0);
        entity.setQueryLimit(limit);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        UsageRecord result = toDomain(entity);
        log.debug("getOrCreateUsage() | return={} (created)", result);
        return result;
    }

    @Override
    public UsageRecord incrementAndGet(String email, String yearMonth) {
        log.debug("incrementAndGet() | email={}, yearMonth={}", email, yearMonth);

        Optional<UsageRecordEntity> existing = repository.findByEmailAndYearMonth(email, yearMonth);
        UsageRecordEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            int limit = resolveQueryLimit(email);
            entity = new UsageRecordEntity();
            entity.setEmail(email);
            entity.setYearMonth(yearMonth);
            entity.setQueryCount(0);
            entity.setQueryLimit(limit);
        }

        entity.setQueryCount(entity.getQueryCount() + 1);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        UsageRecord result = toDomain(entity);
        log.debug("incrementAndGet() | return={}", result);
        return result;
    }

    @Override
    public List<UsageRecord> findAllByYearMonth(String yearMonth) {
        log.debug("findAllByYearMonth() | yearMonth={}", yearMonth);

        List<UsageRecordEntity> entities = repository.findAllByYearMonth(yearMonth);
        List<UsageRecord> result = new ArrayList<>();
        for (UsageRecordEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAllByYearMonth() | return={} records", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Looks up the subscriber's tier and returns the corresponding monthly query limit.
     * Defaults to FREE limits if the subscriber is not found.
     */
    private int resolveQueryLimit(String email) {
        log.debug("resolveQueryLimit() | email={}", email);

        SubscriptionTier tier = SubscriptionTier.FREE;
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(email);
        if (subscriber.isPresent()) {
            tier = subscriber.get().tier();
        }

        int result = tierGatingService.monthlyQueryLimitFor(tier);
        log.debug("resolveQueryLimit() | return={} (tier={})", result, tier);
        return result;
    }

    private UsageRecord toDomain(UsageRecordEntity entity) {
        return new UsageRecord(
                entity.getEmail(),
                entity.getYearMonth(),
                entity.getQueryCount(),
                entity.getQueryLimit()
        );
    }
}
