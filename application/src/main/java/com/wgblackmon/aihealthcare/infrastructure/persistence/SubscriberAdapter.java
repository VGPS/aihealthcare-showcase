package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link SubscriberPort}.
 *
 * <p>Translates between the immutable {@link Subscriber} domain record and
 * the mutable {@link SubscriberEntity} JPA entity.  All persistence operations
 * delegate to {@link SubscriberRepository}; no business logic lives here.
 *
 * <p>{@link #save} performs an upsert: if a record with the same email already
 * exists it is overwritten.  Duplicate detection is the responsibility of the
 * application layer ({@link com.wgblackmon.aihealthcare.domain.service.DeliveryService}),
 * which calls {@link #findByEmail} before invoking {@link #save}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-05-23
 */
@Slf4j
@Component
public class SubscriberAdapter implements SubscriberPort {

    private final SubscriberRepository repository;

    public SubscriberAdapter(SubscriberRepository repository) {
        log.debug("SubscriberAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(Subscriber subscriber) {
        log.debug("save() | email={}", subscriber.email());

        SubscriberEntity entity = toEntity(subscriber);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<Subscriber> findAll() {
        log.debug("findAll() | (no args)");

        List<SubscriberEntity> entities = repository.findAll();
        List<Subscriber> result = new ArrayList<>();
        for (SubscriberEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} subscribers", result.size());
        return result;
    }

    @Override
    public Optional<Subscriber> findByEmail(String email) {
        log.debug("findByEmail() | email={}", email);

        Optional<SubscriberEntity> entity = repository.findByEmail(email);
        Optional<Subscriber> result = entity.map(this::toDomain);

        log.debug("findByEmail() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public List<Subscriber> findAllActiveByTier(SubscriptionTier tier) {
        log.debug("findAllActiveByTier() | tier={}", tier);

        List<SubscriberEntity> entities = repository.findAllByActiveTrueAndTier(tier.name());
        List<Subscriber> result = new ArrayList<>();
        for (SubscriberEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAllActiveByTier() | return={} subscribers", result.size());
        return result;
    }

    @Override
    public void deleteByEmail(String email) {
        log.debug("deleteByEmail() | email={}", email);

        repository.deleteById(email);

        log.debug("deleteByEmail() | return=void");
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private SubscriberEntity toEntity(Subscriber subscriber) {
        log.debug("toEntity() | email={}", subscriber.email());

        SubscriberEntity entity = new SubscriberEntity();
        entity.setEmail(subscriber.email());
        entity.setName(subscriber.name());
        entity.setActive(subscriber.active());
        entity.setSubscribedAt(subscriber.subscribedAt());
        entity.setTier(subscriber.tier().name());

        log.debug("toEntity() | return={}", entity.getEmail());
        return entity;
    }

    private Subscriber toDomain(SubscriberEntity entity) {
        log.debug("toDomain() | email={}", entity.getEmail());

        SubscriptionTier tier;
        try {
            tier = SubscriptionTier.valueOf(entity.getTier());
        } catch (IllegalArgumentException e) {
            log.warn("toDomain() | Unknown tier='{}' for email={}, defaulting to FREE",
                     entity.getTier(), entity.getEmail());
            tier = SubscriptionTier.FREE;
        }

        Subscriber result = new Subscriber(
                entity.getEmail(),
                entity.getName(),
                entity.isActive(),
                entity.getSubscribedAt(),
                tier
        );

        log.debug("toDomain() | return={}", result.email());
        return result;
    }
}
