package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AppUserAdapter} backed by an in-memory H2 database.
 *
 * <p>{@code @DataJpaTest} auto-configures JPA and an embedded H2 instance.
 * {@link AppUserAdapter} is imported manually because {@code @DataJpaTest}
 * does not component-scan beyond JPA repositories.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@DataJpaTest
@Import(AppUserAdapter.class)
class AppUserAdapterTest {

    @Autowired
    private AppUserAdapter adapter;

    private static final AppUser SAMPLE_USER = new AppUser(
            "test@gmail.com",
            "$2b$10$hashedPassword",
            "Test User",
            "USER",
            true
    );

    @Test
    void save_persistsUser() {
        adapter.save(SAMPLE_USER);

        Optional<AppUser> found = adapter.findByEmail("test@gmail.com");
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("test@gmail.com");
    }

    @Test
    void findByEmail_returnsEmpty_whenNotFound() {
        Optional<AppUser> found = adapter.findByEmail("nonexistent@gmail.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_mapsFieldsCorrectly() {
        adapter.save(SAMPLE_USER);

        AppUser user = adapter.findByEmail("test@gmail.com").orElseThrow();
        assertThat(user.email()).isEqualTo("test@gmail.com");
        assertThat(user.passwordHash()).isEqualTo("$2b$10$hashedPassword");
        assertThat(user.displayName()).isEqualTo("Test User");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(user.enabled()).isTrue();
    }
}
