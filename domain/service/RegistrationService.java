package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.RegisterUserUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Domain service that handles self-registration of new DEMO users.
 *
 * <p>Creates both an {@link AppUser} record (for app login with DEMO tier and
 * 7-day expiration) and a {@link Subscriber} record (for newsletter delivery).
 * Checks for duplicate emails across both tables before proceeding.
 *
 * <p>This class carries no Spring annotations; it is wired as a {@code @Bean} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-08-07
 */
@Slf4j
public class RegistrationService implements RegisterUserUseCase {

    private static final int DEMO_DURATION_DAYS = 7;

    private final AppUserPort appUserPort;
    private final SubscriberPort subscriberPort;
    private final PasswordHashingPort passwordHashingPort;
    private final TransactionalEmailPort transactionalEmailPort;

    public RegistrationService(AppUserPort appUserPort,
                               SubscriberPort subscriberPort,
                               PasswordHashingPort passwordHashingPort,
                               TransactionalEmailPort transactionalEmailPort) {
        log.debug("RegistrationService() | appUserPort={}, subscriberPort={}, passwordHashingPort={}, transactionalEmailPort={}",
                  appUserPort.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  passwordHashingPort.getClass().getSimpleName(),
                  transactionalEmailPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.subscriberPort = subscriberPort;
        this.passwordHashingPort = passwordHashingPort;
        this.transactionalEmailPort = transactionalEmailPort;
    }

    @Override
    public AppUser register(String email, String displayName, String rawPassword) {
        log.debug("register() | email={}, displayName={}", email, displayName);

        if (appUserPort.findByEmail(email).isPresent()) {
            log.warn("register() | duplicate app user: {}", LogSanitizer.maskEmail(email));
            throw new DuplicateUserException(email);
        }
        if (subscriberPort.findByEmail(email).isPresent()) {
            log.warn("register() | duplicate subscriber: {}", LogSanitizer.maskEmail(email));
            throw new DuplicateUserException(email);
        }

        String passwordHash = passwordHashingPort.hash(rawPassword);
        Instant demoExpiresAt = Instant.now().plus(DEMO_DURATION_DAYS, ChronoUnit.DAYS);

        AppUser appUser = new AppUser(
                email, passwordHash, displayName, "USER", true,
                SubscriptionTier.DEMO, demoExpiresAt);
        appUserPort.save(appUser);

        Subscriber subscriber = new Subscriber(
                email, displayName, true, Instant.now(), SubscriptionTier.DEMO,
                UUID.randomUUID().toString(), null, null);
        subscriberPort.save(subscriber);

        transactionalEmailPort.sendWelcome(email, displayName, DEMO_DURATION_DAYS);
        transactionalEmailPort.notifyAdminNewRegistration(email, displayName, "DEMO");

        log.debug("register() | return={}", appUser.email());
        return appUser;
    }
}
