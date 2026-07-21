package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppUserDetailsService}.
 *
 * <p>{@link AppUserPort} is mocked — no database or Spring context required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserPort appUserPort;

    private AppUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AppUserDetailsService(appUserPort);
    }

    @Test
    void loadUserByUsername_returnsUserDetails_whenFound() {
        AppUser user = new AppUser("demo@gmail.com", "$2b$10$hash", "Demo", "USER", true, null, null);
        when(appUserPort.findByEmail("demo@gmail.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("demo@gmail.com");

        assertThat(result.getUsername()).isEqualTo("demo@gmail.com");
        assertThat(result.getPassword()).isEqualTo("$2b$10$hash");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void loadUserByUsername_throwsException_whenNotFound() {
        when(appUserPort.findByEmail("unknown@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@gmail.com");
    }

    @Test
    void loadUserByUsername_reportsDisabled_whenUserNotEnabled() {
        AppUser user = new AppUser("disabled@gmail.com", "$2b$10$hash", "Disabled", "USER", false, null, null);
        when(appUserPort.findByEmail("disabled@gmail.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("disabled@gmail.com");

        assertThat(result.isEnabled()).isFalse();
    }
}
