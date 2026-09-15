package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningAction;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningEvent;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoProvisioningEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for Just-In-Time (JIT) SSO user provisioning.
 *
 * <p>Called after each successful SAML authentication to ensure the user
 * has an {@link AppUser} and {@link Subscriber} record. Three cases:
 * <ul>
 *   <li><b>New user</b> — creates both records with ENTERPRISE tier and
 *       a "{SSO}" password sentinel (prevents form login).</li>
 *   <li><b>Existing user, first SSO login</b> — upgrades tier to ENTERPRISE,
 *       records a LINKED event.</li>
 *   <li><b>Returning SSO user</b> — records a LOGIN event only.</li>
 * </ul>
 *
 * <p>This class carries no Spring annotations; it is wired as a {@code @Bean}
 * in AppConfig.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public class SsoProvisioningService {

    private static final DomainLogger log = new DomainLogger(SsoProvisioningService.class);

    static final String SSO_PASSWORD_SENTINEL = "{SSO}";

    private final AppUserPort appUserPort;
    private final SubscriberPort subscriberPort;
    private final SsoIdentityProviderPort providerPort;
    private final SsoProvisioningEventPort eventPort;
    private final TransactionalEmailPort emailPort;

    public SsoProvisioningService(AppUserPort appUserPort,
                                  SubscriberPort subscriberPort,
                                  SsoIdentityProviderPort providerPort,
                                  SsoProvisioningEventPort eventPort,
                                  TransactionalEmailPort emailPort) {
        log.debug("SsoProvisioningService()");
        this.appUserPort = appUserPort;
        this.subscriberPort = subscriberPort;
        this.providerPort = providerPort;
        this.eventPort = eventPort;
        this.emailPort = emailPort;
    }

    /**
     * Provisions or links an SSO-authenticated user.
     *
     * @param email            Email from the SAML assertion.
     * @param displayName      Display name from the SAML assertion.
     * @param registrationId   The IdP registration slug.
     * @return The provisioned or existing {@link AppUser}.
     */
    public AppUser provisionOrLink(String email, String displayName, String registrationId) {
        log.debug("provisionOrLink() | email={}, displayName={}, registrationId={}",
                  LogSanitizer.maskEmail(email), displayName, registrationId);

        SsoIdentityProvider provider = providerPort.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SSO provider not found: " + registrationId));

        SubscriptionTier tier = provider.defaultTier();
        Optional<AppUser> existing = appUserPort.findByEmail(email);

        AppUser user;
        SsoProvisioningAction action;

        if (existing.isPresent()) {
            AppUser current = existing.get();
            if (hasPriorSsoEvent(email, registrationId)) {
                action = SsoProvisioningAction.LOGIN;
                user = current;
            } else {
                user = new AppUser(
                        current.email(),
                        current.passwordHash(),
                        current.displayName(),
                        current.role(),
                        true,
                        tier,
                        current.demoExpiresAt());
                appUserPort.save(user);

                subscriberPort.findByEmail(email).ifPresent(sub -> {
                    Subscriber updated = new Subscriber(
                            sub.email(), sub.name(), sub.active(), sub.subscribedAt(),
                            tier, sub.unsubscribeToken(),
                            sub.stripeCustomerId(), sub.stripeSubscriptionId());
                    subscriberPort.save(updated);
                });

                action = SsoProvisioningAction.LINKED;
                log.info("provisionOrLink() | linked existing user to SSO: email={}, idp={}",
                         LogSanitizer.maskEmail(email), registrationId);
            }
        } else {
            user = new AppUser(email, SSO_PASSWORD_SENTINEL, displayName, "USER", true, tier, null);
            appUserPort.save(user);

            Subscriber subscriber = new Subscriber(
                    email, displayName, true, Instant.now(), tier,
                    UUID.randomUUID().toString(), null, null);
            subscriberPort.save(subscriber);

            action = SsoProvisioningAction.CREATED;
            log.info("provisionOrLink() | created new SSO user: email={}, idp={}",
                     LogSanitizer.maskEmail(email), registrationId);

            emailPort.notifyAdminNewRegistration(email, displayName, "ENTERPRISE (SSO)");
        }

        SsoProvisioningEvent event = new SsoProvisioningEvent(
                UUID.randomUUID().toString(), registrationId, email, action, Instant.now());
        eventPort.record(event);

        log.debug("provisionOrLink() | return={}, action={}", user.email(), action);
        return user;
    }

    private boolean hasPriorSsoEvent(String email, String registrationId) {
        return eventPort.findByEmail(email).stream()
                .anyMatch(e -> e.registrationId().equals(registrationId)
                        && (e.action() == SsoProvisioningAction.CREATED
                            || e.action() == SsoProvisioningAction.LINKED));
    }
}
