package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration for session-based form login with role-based
 * access control.
 *
 * <p>Protects all Thymeleaf UI pages behind authentication while leaving
 * REST API endpoints ({@code /api/**}), webhook receivers ({@code /stripe/**}),
 * internal monitoring triggers ({@code /monitoring/**}), and the public
 * pricing page ({@code /pricing}) open.
 *
 * <p>Admin-only pages ({@code /admin/**}, {@code /newsletter/runs/**}) require
 * the {@code ADMIN} role. All other authenticated pages are accessible to any
 * logged-in user.
 *
 * <p>CSRF protection is enabled for browser-originated requests (the login form,
 * Thymeleaf pages) but disabled for machine-to-machine paths where no browser
 * session exists ({@code /api/**}, {@code /monitoring/**}, {@code /stripe/**}).
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-05-28
 * @updated 2026-08-04
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyPort apiKeyPort;

    public SecurityConfig(ApiKeyPort apiKeyPort) {
        log.debug("SecurityConfig() | apiKeyPort={}", apiKeyPort.getClass().getSimpleName());
        this.apiKeyPort = apiKeyPort;
    }

    /**
     * Defines the HTTP security filter chain.
     *
     * <p>Registers the {@link ApiKeyAuthenticationFilter} before Spring's
     * username/password filter so that {@code X-API-Key} header authentication
     * is attempted first on {@code /api/**} paths.
     *
     * @param http The {@link HttpSecurity} builder provided by Spring Security.
     * @return The built {@link SecurityFilterChain}.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.debug("filterChain() | configuring security filter chain");

        http
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyPort),
                             UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/choose-path", "/unsubscribe", "/unsubscribe/downgrade",
                                 "/css/**", "/js/**", "/webjars/**",
                                 "/pricing", "/error").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/monitoring/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/developer").permitAll()
                .requestMatchers("/wiki", "/wiki/**").permitAll()
                .requestMatchers("/stripe/**").permitAll()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/newsletter/runs/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/monitoring/**", "/stripe/**",
                                        "/swagger-ui/**", "/v3/api-docs/**")
            );

        SecurityFilterChain result = http.build();
        log.debug("filterChain() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Provides the BCrypt password encoder used for hashing and verifying
     * user passwords.
     *
     * @return A {@link BCryptPasswordEncoder} instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("passwordEncoder() | creating BCryptPasswordEncoder");
        PasswordEncoder result = new BCryptPasswordEncoder();
        log.debug("passwordEncoder() | return={}", result.getClass().getSimpleName());
        return result;
    }
}
