package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Request filter that enforces DEMO tier expiration on every authenticated request.
 *
 * <p>For DEMO users whose {@code demoExpiresAt} has passed, this filter:
 * <ol>
 *   <li>Updates both {@code app_users} and {@code subscribers} tier to FREE_PENDING</li>
 *   <li>Redirects the user to {@code /choose-path}</li>
 * </ol>
 *
 * <p>Skips checking for unauthenticated requests, static resources, and paths
 * that must remain accessible during the FREE_PENDING state.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-08-07
 */
@Slf4j
public class DemoExpirationFilter extends OncePerRequestFilter {

    private final AppUserPort appUserPort;
    private final SubscriberPort subscriberPort;

    public DemoExpirationFilter(AppUserPort appUserPort, SubscriberPort subscriberPort) {
        log.debug("DemoExpirationFilter() | appUserPort={}, subscriberPort={}",
                  appUserPort.getClass().getSimpleName(), subscriberPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.subscriberPort = subscriberPort;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        log.debug("shouldNotFilter() | path={}", path);

        boolean skip = path.startsWith("/choose-path")
                || path.startsWith("/register")
                || path.startsWith("/login")
                || path.startsWith("/logout")
                || path.startsWith("/api/")
                || path.startsWith("/monitoring/")
                || path.startsWith("/stripe/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/webjars/")
                || path.startsWith("/pricing")
                || path.startsWith("/wiki")
                || path.equals("/error");

        log.debug("shouldNotFilter() | return={}", skip);
        return skip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        log.debug("doFilterInternal() | path={}", request.getRequestURI());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.debug("doFilterInternal() | not authenticated, skipping");
            filterChain.doFilter(request, response);
            return;
        }

        String email = auth.getName();
        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("doFilterInternal() | user not found: {}", email);
            filterChain.doFilter(request, response);
            return;
        }

        AppUser user = userOpt.get();

        // Redirect FREE_PENDING users to choose-path
        if (user.tier() == SubscriptionTier.FREE_PENDING) {
            log.info("doFilterInternal() | FREE_PENDING user redirected to choose-path: {}", LogSanitizer.maskEmail(email));
            response.sendRedirect(request.getContextPath() + "/choose-path");
            return;
        }

        // Check DEMO expiration
        if (user.tier() == SubscriptionTier.DEMO
                && user.demoExpiresAt() != null
                && Instant.now().isAfter(user.demoExpiresAt())) {

            log.info("doFilterInternal() | DEMO expired for user: {}", LogSanitizer.maskEmail(email));

            // Transition app user to FREE_PENDING
            AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                    user.role(), user.enabled(), SubscriptionTier.FREE_PENDING, user.demoExpiresAt());
            appUserPort.save(updated);

            // Transition subscriber to FREE_PENDING
            Optional<Subscriber> subOpt = subscriberPort.findByEmail(email);
            if (subOpt.isPresent()) {
                Subscriber sub = subOpt.get();
                Subscriber updatedSub = new Subscriber(sub.email(), sub.name(), sub.active(),
                        sub.subscribedAt(), SubscriptionTier.FREE_PENDING,
                        sub.unsubscribeToken(), sub.stripeCustomerId(), sub.stripeSubscriptionId());
                subscriberPort.save(updatedSub);
            }

            response.sendRedirect(request.getContextPath() + "/choose-path");
            return;
        }

        log.debug("doFilterInternal() | return=continue");
        filterChain.doFilter(request, response);
    }
}
