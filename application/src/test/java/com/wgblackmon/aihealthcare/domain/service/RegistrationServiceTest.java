package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistrationService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
class RegistrationServiceTest {

    private AppUserPort appUserPort;
    private SubscriberPort subscriberPort;
    private PasswordHashingPort passwordHashingPort;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        appUserPort = mock(AppUserPort.class);
        subscriberPort = mock(SubscriberPort.class);
        passwordHashingPort = mock(PasswordHashingPort.class);
        service = new RegistrationService(appUserPort, subscriberPort, passwordHashingPort);

        when(appUserPort.findByEmail(anyString())).thenReturn(Optional.empty());
        when(subscriberPort.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordHashingPort.hash(anyString())).thenReturn("$2b$10$hashedPassword");
    }

    @Test
    void register_happyPath_createsAppUserWithDemoTier() {
        AppUser result = service.register("new@example.com", "New User", "password123");

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.displayName()).isEqualTo("New User");
        assertThat(result.tier()).isEqualTo(SubscriptionTier.DEMO);
        assertThat(result.demoExpiresAt()).isNotNull();
        assertThat(result.enabled()).isTrue();
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void register_happyPath_createsSubscriberWithDemoTier() {
        service.register("new@example.com", "New User", "password123");

        ArgumentCaptor<Subscriber> captor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscriberPort).save(captor.capture());
        Subscriber sub = captor.getValue();

        assertThat(sub.email()).isEqualTo("new@example.com");
        assertThat(sub.name()).isEqualTo("New User");
        assertThat(sub.active()).isTrue();
        assertThat(sub.tier()).isEqualTo(SubscriptionTier.DEMO);
    }

    @Test
    void register_happyPath_hashesPassword() {
        service.register("new@example.com", "New User", "password123");

        verify(passwordHashingPort).hash("password123");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("$2b$10$hashedPassword");
    }

    @Test
    void register_happyPath_setsDemoExpiration() {
        AppUser result = service.register("new@example.com", "New User", "password123");

        assertThat(result.demoExpiresAt()).isNotNull();
        // Demo should expire roughly 7 days from now (within a few seconds)
        long daysUntilExpiry = java.time.Duration.between(
                java.time.Instant.now(), result.demoExpiresAt()).toDays();
        assertThat(daysUntilExpiry).isBetween(6L, 7L);
    }

    @Test
    void register_duplicateAppUser_throwsDuplicateUserException() {
        AppUser existing = new AppUser("existing@example.com", "hash", "Existing", "USER", true, null, null);
        when(appUserPort.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register("existing@example.com", "New User", "password123"))
                .isInstanceOf(DuplicateUserException.class);

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_duplicateSubscriber_throwsDuplicateUserException() {
        Subscriber existing = new Subscriber("existing@example.com", "Existing", true,
                java.time.Instant.now(), SubscriptionTier.FREE);
        when(subscriberPort.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register("existing@example.com", "New User", "password123"))
                .isInstanceOf(DuplicateUserException.class);

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
