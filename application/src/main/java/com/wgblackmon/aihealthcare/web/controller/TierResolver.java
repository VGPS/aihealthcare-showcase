package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Shared component that resolves a user's subscription tier from a
 * {@link Principal} or email address, and checks for admin authority.
 *
 * <p>Centralises the tier-resolution and admin-check logic previously
 * duplicated across 20+ web controllers. All controllers should delegate
 * to this component instead of maintaining private copies.</p>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class TierResolver {

    private final SubscriberPort subscriberPort;

    public TierResolver(SubscriberPort subscriberPort) {
        this.subscriberPort = subscriberPort;
    }

    /**
     * Resolves the subscription tier for the given principal.
     * Admins are treated as {@link SubscriptionTier#SUBSCRIBER}.
     * Anonymous or unknown users default to {@link SubscriptionTier#FREE}.
     */
    public SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        if (isAdmin(principal)) {
            log.debug("resolveTier() | return={}", SubscriptionTier.SUBSCRIBER);
            return SubscriptionTier.SUBSCRIBER;
        }
        SubscriptionTier tier = subscriberPort.findByEmail(principal.getName())
                .map(Subscriber::tier)
                .orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", tier);
        return tier;
    }

    /**
     * Resolves the subscription tier by email address (no admin check).
     * Used by REST controllers that receive email via request header.
     */
    public SubscriptionTier resolveTier(String email) {
        log.debug("resolveTier() | email={}", email);
        if (email == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        SubscriptionTier tier = subscriberPort.findByEmail(email)
                .map(Subscriber::tier)
                .orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", tier);
        return tier;
    }

    /**
     * Returns {@code true} if the principal has full (paid-tier) access:
     * SUBSCRIBER, DEMO, ENTERPRISE, or ADMIN.
     */
    public boolean hasFullAccess(Principal principal) {
        log.debug("hasFullAccess() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("hasFullAccess() | return=false");
            return false;
        }
        if (isAdmin(principal)) {
            log.debug("hasFullAccess() | return=true (admin)");
            return true;
        }
        SubscriptionTier tier = resolveTier(principal);
        boolean result = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE;
        log.debug("hasFullAccess() | return={}", result);
        return result;
    }

    /**
     * Returns {@code true} if the principal has enterprise-level access:
     * ENTERPRISE or DEMO tier.
     */
    public boolean hasEnterpriseAccess(Principal principal) {
        log.debug("hasEnterpriseAccess() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("hasEnterpriseAccess() | return=false");
            return false;
        }
        if (isAdmin(principal)) {
            log.debug("hasEnterpriseAccess() | return=true (admin)");
            return true;
        }
        SubscriptionTier tier = resolveTier(principal);
        boolean result = tier == SubscriptionTier.ENTERPRISE
                || tier == SubscriptionTier.DEMO;
        log.debug("hasEnterpriseAccess() | return={}", result);
        return result;
    }

    /**
     * Returns {@code true} if the principal holds {@code ROLE_ADMIN}.
     */
    public boolean isAdmin(Principal principal) {
        log.debug("isAdmin() | principal={}", principal != null ? principal.getName() : "null");
        if (principal instanceof Authentication auth) {
            boolean result = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            log.debug("isAdmin() | return={}", result);
            return result;
        }
        log.debug("isAdmin() | return=false");
        return false;
    }
}
