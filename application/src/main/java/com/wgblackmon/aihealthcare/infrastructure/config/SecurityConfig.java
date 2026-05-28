package com.wgblackmon.aihealthcare.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for session-based form login.
 *
 * <p>Protects all Thymeleaf UI pages behind authentication while leaving
 * REST API endpoints ({@code /api/**}), webhook receivers ({@code /stripe/**}),
 * internal monitoring triggers ({@code /monitoring/**}), and the public
 * pricing page ({@code /pricing}) open.
 *
 * <p>CSRF protection is enabled for browser-originated requests (the login form,
 * Thymeleaf pages) but disabled for machine-to-machine paths where no browser
 * session exists ({@code /api/**}, {@code /monitoring/**}, {@code /stripe/**}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the HTTP security filter chain.
     *
     * @param http The {@link HttpSecurity} builder provided by Spring Security.
     * @return The built {@link SecurityFilterChain}.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.debug("filterChain() | configuring security filter chain");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**",
                                 "/pricing", "/error").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/monitoring/**").permitAll()
                .requestMatchers("/stripe/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/monitoring/**", "/stripe/**")
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
