package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PasswordResetToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PasswordResetAdapter} using an embedded H2 database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
@DataJpaTest
@Import(PasswordResetAdapter.class)
class PasswordResetAdapterTest {

    @Autowired
    private PasswordResetAdapter adapter;

    @Test
    void save_thenFindByToken_returnsToken() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(1, ChronoUnit.HOURS), false);

        adapter.save(token);
        Optional<PasswordResetToken> result = adapter.findByToken("tok-1");

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("user@example.com");
        assertThat(result.get().used()).isFalse();
    }

    @Test
    void findByToken_unknownToken_returnsEmpty() {
        Optional<PasswordResetToken> result = adapter.findByToken("ghost");

        assertThat(result).isEmpty();
    }

    @Test
    void save_reSaveSameToken_overwritesUsedFlag() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(1, ChronoUnit.HOURS), false);
        adapter.save(token);

        adapter.save(new PasswordResetToken("tok-1", "user@example.com", token.expiresAt(), true));

        Optional<PasswordResetToken> result = adapter.findByToken("tok-1");
        assertThat(result).isPresent();
        assertThat(result.get().used()).isTrue();
    }
}
