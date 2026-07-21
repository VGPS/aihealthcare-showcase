package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link AppUserPort}.
 *
 * <p>Translates between the immutable {@link AppUser} domain record and
 * the mutable {@link AppUserEntity} JPA entity.  All persistence operations
 * delegate to {@link AppUserRepository}; no business logic lives here.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-28
 * @updated 2026-05-31
 */
@Slf4j
@Component
public class AppUserAdapter implements AppUserPort {

    private final AppUserRepository repository;

    public AppUserAdapter(AppUserRepository repository) {
        log.debug("AppUserAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        log.debug("findByEmail() | email={}", email);

        Optional<AppUserEntity> entity = repository.findByEmail(email);
        Optional<AppUser> result = entity.map(this::toDomain);

        log.debug("findByEmail() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public List<AppUser> findAll() {
        log.debug("findAll() | (no args)");

        List<AppUserEntity> entities = repository.findAll();
        List<AppUser> result = new ArrayList<>();
        for (AppUserEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} users", result.size());
        return result;
    }

    @Override
    public void save(AppUser user) {
        log.debug("save() | email={}", user.email());

        AppUserEntity entity = toEntity(user);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<AppUser> findByTierAndDemoExpiresAtBefore(String tier, Instant before) {
        log.debug("findByTierAndDemoExpiresAtBefore() | tier={}, before={}", tier, before);

        List<AppUserEntity> entities = repository.findAllByTierAndDemoExpiresAtBefore(tier, before);
        List<AppUser> result = new ArrayList<>();
        for (AppUserEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findByTierAndDemoExpiresAtBefore() | return={} users", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private AppUserEntity toEntity(AppUser user) {
        log.debug("toEntity() | email={}", user.email());

        AppUserEntity entity = new AppUserEntity();
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setDisplayName(user.displayName());
        entity.setRole(user.role());
        entity.setEnabled(user.enabled());
        entity.setTier(user.tier() != null ? user.tier().name() : null);
        entity.setDemoExpiresAt(user.demoExpiresAt());

        log.debug("toEntity() | return={}", entity.getEmail());
        return entity;
    }

    private AppUser toDomain(AppUserEntity entity) {
        log.debug("toDomain() | email={}", entity.getEmail());

        SubscriptionTier tier = null;
        if (entity.getTier() != null) {
            tier = SubscriptionTier.valueOf(entity.getTier());
        }

        AppUser result = new AppUser(
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getRole(),
                entity.isEnabled(),
                tier,
                entity.getDemoExpiresAt()
        );

        log.debug("toDomain() | return={}", result.email());
        return result;
    }
}
