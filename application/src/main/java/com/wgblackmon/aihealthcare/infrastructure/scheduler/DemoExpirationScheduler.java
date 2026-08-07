package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Nightly scheduler that transitions expired DEMO users to FREE_PENDING.
 *
 * <p>Catches users whose demo expired but who never returned to trigger the
 * {@code DemoExpirationFilter} during a web request. Runs daily at 02:00 UTC.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-08-07
 */
@Slf4j
@Component
public class DemoExpirationScheduler {

    private final AppUserPort appUserPort;
    private final SubscriberPort subscriberPort;
    private final TransactionalEmailPort transactionalEmailPort;

    public DemoExpirationScheduler(AppUserPort appUserPort,
                                   SubscriberPort subscriberPort,
                                   TransactionalEmailPort transactionalEmailPort) {
        log.debug("DemoExpirationScheduler() | appUserPort={}, subscriberPort={}, transactionalEmailPort={}",
                  appUserPort.getClass().getSimpleName(), subscriberPort.getClass().getSimpleName(),
                  transactionalEmailPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.subscriberPort = subscriberPort;
        this.transactionalEmailPort = transactionalEmailPort;
    }

    /**
     * Transitions all expired DEMO users to FREE_PENDING tier.
     */
    @Scheduled(cron = "${aihealthcare.demo.expiration-cron:0 0 2 * * *}")
    public void expireExpiredDemos() {
        log.debug("expireExpiredDemos() | (no args)");
        try {
            Instant now = Instant.now();
            List<AppUser> expiredUsers = appUserPort.findByTierAndDemoExpiresAtBefore("DEMO", now);

            log.info("expireExpiredDemos() | found {} expired DEMO users", expiredUsers.size());

            int transitioned = 0;
            for (AppUser user : expiredUsers) {
                try {
                    AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                            user.role(), user.enabled(), SubscriptionTier.FREE_PENDING, user.demoExpiresAt());
                    appUserPort.save(updated);

                    Optional<Subscriber> subOpt = subscriberPort.findByEmail(user.email());
                    if (subOpt.isPresent()) {
                        Subscriber sub = subOpt.get();
                        Subscriber updatedSub = new Subscriber(sub.email(), sub.name(), sub.active(),
                                sub.subscribedAt(), SubscriptionTier.FREE_PENDING,
                                sub.unsubscribeToken(), sub.stripeCustomerId(), sub.stripeSubscriptionId());
                        subscriberPort.save(updatedSub);
                    }

                    transactionalEmailPort.sendDemoExpiration(user.email(), user.displayName());
                    transitioned++;
                } catch (Exception e) {
                    log.error("expireExpiredDemos() | failed to transition user: {}", LogSanitizer.maskEmail(user.email()), e);
                }
            }

            log.info("expireExpiredDemos() | transitioned {} users to FREE_PENDING", transitioned);
        } catch (Exception e) {
            log.error("expireExpiredDemos() | scheduler exception", e);
        }
        log.debug("expireExpiredDemos() | return=void");
    }
}
