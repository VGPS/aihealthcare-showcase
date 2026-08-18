package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PasswordResetToken;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordResetPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link PasswordResetPort}.
 *
 * <p>Translates between the immutable {@link PasswordResetToken} domain record
 * and the mutable {@link PasswordResetTokenEntity} JPA entity.  All persistence
 * operations delegate to {@link PasswordResetTokenRepository}; no business
 * logic lives here.
 *
 * <p>{@link #save} performs an upsert: if a record with the same token already
 * exists it is overwritten. This is how {@link com.wgblackmon.aihealthcare.domain.service.PasswordResetService}
 * marks a token as used — it re-saves the same token with {@code used=true}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
@Slf4j
@Component
public class PasswordResetAdapter implements PasswordResetPort {

    private final PasswordResetTokenRepository repository;

    public PasswordResetAdapter(PasswordResetTokenRepository repository) {
        log.debug("PasswordResetAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(PasswordResetToken token) {
        log.debug("save() | token=[REDACTED]");

        repository.save(toEntity(token));

        log.debug("save() | return=void");
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        log.debug("findByToken() | token=[REDACTED]");

        Optional<PasswordResetTokenEntity> entity = repository.findById(token);
        Optional<PasswordResetToken> result = entity.map(this::toDomain);

        log.debug("findByToken() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private PasswordResetTokenEntity toEntity(PasswordResetToken token) {
        log.debug("toEntity() | token=[REDACTED]");

        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setToken(token.token());
        entity.setEmail(token.email());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsed(token.used());

        log.debug("toEntity() | return=entity");
        return entity;
    }

    private PasswordResetToken toDomain(PasswordResetTokenEntity entity) {
        log.debug("toDomain() | token=[REDACTED]");

        PasswordResetToken result = new PasswordResetToken(
                entity.getToken(), entity.getEmail(), entity.getExpiresAt(), entity.isUsed());

        log.debug("toDomain() | return=token");
        return result;
    }
}
