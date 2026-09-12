package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.InvalidResetTokenException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.PasswordResetToken;
import com.wgblackmon.aihealthcare.domain.port.inbound.PasswordResetUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordResetPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service handling the self-service "forgot password" flow.
 *
 * <p>Issues single-use, time-limited tokens on request and validates them
 * when the user submits a new password. Never reveals whether a given email
 * is registered — {@link #requestReset(String)} silently no-ops for unknown
 * emails so this endpoint cannot be used to enumerate accounts.
 *
 * <p>This class carries no Spring annotations; it is wired as a {@code @Bean} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-09-11
 */
public class PasswordResetService implements PasswordResetUseCase {

    private static final DomainLogger log = new DomainLogger(PasswordResetService.class);

    private static final int TOKEN_TTL_MINUTES = 60;

    private final AppUserPort appUserPort;
    private final PasswordResetPort passwordResetPort;
    private final PasswordHashingPort passwordHashingPort;
    private final TransactionalEmailPort transactionalEmailPort;

    public PasswordResetService(AppUserPort appUserPort,
                                PasswordResetPort passwordResetPort,
                                PasswordHashingPort passwordHashingPort,
                                TransactionalEmailPort transactionalEmailPort) {
        log.debug("PasswordResetService() | appUserPort={}, passwordResetPort={}, passwordHashingPort={}, transactionalEmailPort={}",
                  appUserPort.getClass().getSimpleName(),
                  passwordResetPort.getClass().getSimpleName(),
                  passwordHashingPort.getClass().getSimpleName(),
                  transactionalEmailPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.passwordResetPort = passwordResetPort;
        this.passwordHashingPort = passwordHashingPort;
        this.transactionalEmailPort = transactionalEmailPort;
    }

    @Override
    public void requestReset(String email) {
        log.debug("requestReset() | email={}", email);

        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("requestReset() | No account for email, no-op (anti-enumeration)");
            log.debug("requestReset() | return=void");
            return;
        }

        AppUser user = userOpt.get();
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);

        passwordResetPort.save(new PasswordResetToken(token, email, expiresAt, false));
        transactionalEmailPort.sendPasswordReset(email, user.displayName(), token);
        log.info("requestReset() | Reset token issued: email={}", LogSanitizer.maskEmail(email));

        log.debug("requestReset() | return=void");
    }

    @Override
    public boolean validateToken(String token) {
        log.debug("validateToken() | token=[REDACTED]");

        boolean result = isValid(token);

        log.debug("validateToken() | return={}", result);
        return result;
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        log.debug("resetPassword() | token=[REDACTED]");

        Optional<PasswordResetToken> tokenOpt = passwordResetPort.findByToken(token);
        if (tokenOpt.isEmpty() || !isValid(tokenOpt.get())) {
            log.warn("resetPassword() | Invalid, expired, or already-used token");
            throw new InvalidResetTokenException();
        }
        PasswordResetToken resetToken = tokenOpt.get();

        Optional<AppUser> userOpt = appUserPort.findByEmail(resetToken.email());
        if (userOpt.isEmpty()) {
            log.warn("resetPassword() | Token valid but no matching account remains: email={}",
                    LogSanitizer.maskEmail(resetToken.email()));
            throw new InvalidResetTokenException();
        }
        AppUser user = userOpt.get();

        String newHash = passwordHashingPort.hash(newPassword);
        AppUser updated = new AppUser(
                user.email(), newHash, user.displayName(), user.role(),
                user.enabled(), user.tier(), user.demoExpiresAt());
        appUserPort.save(updated);

        passwordResetPort.save(new PasswordResetToken(
                resetToken.token(), resetToken.email(), resetToken.expiresAt(), true));

        log.info("resetPassword() | Password reset: email={}", LogSanitizer.maskEmail(user.email()));
        log.debug("resetPassword() | return=void");
    }

    private boolean isValid(String token) {
        log.debug("isValid() | token=[REDACTED]");

        Optional<PasswordResetToken> tokenOpt = passwordResetPort.findByToken(token);
        boolean result = tokenOpt.isPresent() && isValid(tokenOpt.get());

        log.debug("isValid() | return={}", result);
        return result;
    }

    private boolean isValid(PasswordResetToken resetToken) {
        return !resetToken.used() && resetToken.expiresAt().isAfter(Instant.now());
    }
}
