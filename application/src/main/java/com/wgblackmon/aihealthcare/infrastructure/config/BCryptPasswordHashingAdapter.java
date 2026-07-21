package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt-based adapter implementing {@link PasswordHashingPort}.
 *
 * <p>Delegates to Spring Security's {@link PasswordEncoder} bean (configured
 * as {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}
 * in {@link SecurityConfig}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
@Slf4j
@Component
public class BCryptPasswordHashingAdapter implements PasswordHashingPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHashingAdapter(PasswordEncoder passwordEncoder) {
        log.debug("BCryptPasswordHashingAdapter() | passwordEncoder={}", passwordEncoder.getClass().getSimpleName());
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        log.debug("hash() | rawPassword=[REDACTED]");

        String result = passwordEncoder.encode(rawPassword);

        log.debug("hash() | return=[REDACTED]");
        return result;
    }
}
