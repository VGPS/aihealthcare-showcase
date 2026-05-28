package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Spring Security {@link UserDetailsService} backed by the {@link AppUserPort}
 * outbound port.
 *
 * <p>Loads user credentials from the {@code app_users} table via the hexagonal
 * port abstraction.  Spring Security calls {@link #loadUserByUsername(String)}
 * during form-login authentication; the "username" is the user's email address.
 *
 * <p>Disabled accounts ({@code enabled = false}) are reported to Spring Security
 * via {@link UserDetails#isEnabled()}, which causes authentication to fail with
 * a "User is disabled" message.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@Slf4j
@Component
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserPort appUserPort;

    public AppUserDetailsService(AppUserPort appUserPort) {
        log.debug("AppUserDetailsService() | appUserPort={}", appUserPort.getClass().getSimpleName());
        this.appUserPort = appUserPort;
    }

    /**
     * Loads the user by email address for Spring Security authentication.
     *
     * @param email The email address entered on the login form.
     * @return A {@link UserDetails} instance with credentials and authorities.
     * @throws UsernameNotFoundException if no user exists with the given email.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("loadUserByUsername() | email={}", email);

        AppUser appUser = appUserPort.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("loadUserByUsername() | user not found: {}", email);
                    return new UsernameNotFoundException("User not found: " + email);
                });

        UserDetails result = User.builder()
                .username(appUser.email())
                .password(appUser.passwordHash())
                .roles(appUser.role())
                .disabled(!appUser.enabled())
                .build();

        log.debug("loadUserByUsername() | return=UserDetails[email={}, role={}, enabled={}]",
                  appUser.email(), appUser.role(), appUser.enabled());
        return result;
    }
}
