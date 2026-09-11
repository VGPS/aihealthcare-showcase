package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Optional;

/**
 * Global model attribute provider for the shared footer fragment.
 *
 * <p>Injects {@code footerTier} into every Thymeleaf model so the footer
 * can render tier-aware content (upgrade CTA vs manage subscription) without
 * each controller needing to set the attribute individually.
 *
 * <p>Uses {@link ObjectProvider} for the {@link AppUserPort} dependency so
 * this advice loads gracefully in {@code @WebMvcTest} slices where the port
 * bean is not available.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-05
 * @updated 2026-08-05
 */
@Slf4j
@ControllerAdvice
public class FooterModelAdvice {

    private final AppUserPort appUserPort;

    public FooterModelAdvice(ObjectProvider<AppUserPort> appUserPortProvider) {
        this.appUserPort = appUserPortProvider.getIfAvailable();
        log.debug("FooterModelAdvice() | appUserPort={}", appUserPort != null ? appUserPort.getClass().getSimpleName() : "null");
    }

    @ModelAttribute("footerTier")
    public String footerTier(Principal principal) {
        log.debug("footerTier() | principal={}", principal != null ? principal.getName() : "anonymous");

        if (principal == null || appUserPort == null) {
            log.debug("footerTier() | return=ANONYMOUS");
            return "ANONYMOUS";
        }

        Optional<AppUser> userOpt = appUserPort.findByEmail(principal.getName());
        SubscriptionTier tier = userOpt.map(AppUser::tier).orElse(SubscriptionTier.FREE);
        if (tier == null) {
            tier = SubscriptionTier.FREE;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                && tier.ordinal() < SubscriptionTier.SUBSCRIBER.ordinal()) {
            tier = SubscriptionTier.SUBSCRIBER;
        }

        String result = tier.name();
        log.debug("footerTier() | return={}", result);
        return result;
    }
}
