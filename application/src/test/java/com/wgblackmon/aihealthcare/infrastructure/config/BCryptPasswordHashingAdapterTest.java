package com.wgblackmon.aihealthcare.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BCryptPasswordHashingAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
class BCryptPasswordHashingAdapterTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final BCryptPasswordHashingAdapter adapter = new BCryptPasswordHashingAdapter(encoder);

    @Test
    void hash_returnsBCryptEncodedString() {
        String result = adapter.hash("password123");

        assertThat(result).startsWith("$2a$");
        assertThat(encoder.matches("password123", result)).isTrue();
    }

    @Test
    void hash_differentCallsProduceDifferentHashes() {
        String hash1 = adapter.hash("password123");
        String hash2 = adapter.hash("password123");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
