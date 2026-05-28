package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link AppUserPort}.
 *
 * <p>Translates between the immutable {@link AppUser} domain record and
 * the mutable {@link AppUserEntity} JPA entity.  All persistence operations
 * delegate to {@link AppUserRepository}; no business logic lives here.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
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
    public void save(AppUser user) {
        log.debug("save() | email={}", user.email());

        AppUserEntity entity = toEntity(user);
        repository.save(entity);

        log.debug("save() | return=void");
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

        log.debug("toEntity() | return={}", entity.getEmail());
        return entity;
    }

    private AppUser toDomain(AppUserEntity entity) {
        log.debug("toDomain() | email={}", entity.getEmail());

        AppUser result = new AppUser(
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getRole(),
                entity.isEnabled()
        );

        log.debug("toDomain() | return={}", result.email());
        return result;
    }
}
