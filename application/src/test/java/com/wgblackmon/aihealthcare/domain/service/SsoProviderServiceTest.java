package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SsoProviderService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
class SsoProviderServiceTest {

    private SsoIdentityProviderPort providerPort;
    private SsoProviderService service;

    private static final Instant NOW = Instant.now();

    private SsoIdentityProvider testProvider() {
        return new SsoIdentityProvider(
                "kaiser-idp", "Kaiser Permanente",
                "https://idp.kaiser.org", "https://idp.kaiser.org/sso",
                "CERT", null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW);
    }

    @BeforeEach
    void setUp() {
        providerPort = Mockito.mock(SsoIdentityProviderPort.class);
        service = new SsoProviderService(providerPort);
    }

    @Test
    void createSavesNewProvider() {
        SsoIdentityProvider provider = testProvider();
        when(providerPort.existsById("kaiser-idp")).thenReturn(false);
        when(providerPort.save(any())).thenReturn(provider);

        SsoIdentityProvider result = service.create(provider);

        assertThat(result.registrationId()).isEqualTo("kaiser-idp");
        verify(providerPort).save(provider);
    }

    @Test
    void createThrowsOnDuplicate() {
        when(providerPort.existsById("kaiser-idp")).thenReturn(true);

        assertThatThrownBy(() -> service.create(testProvider()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSavesExistingProvider() {
        SsoIdentityProvider provider = testProvider();
        when(providerPort.existsById("kaiser-idp")).thenReturn(true);
        when(providerPort.save(any())).thenReturn(provider);

        SsoIdentityProvider result = service.update(provider);

        assertThat(result.registrationId()).isEqualTo("kaiser-idp");
    }

    @Test
    void updateThrowsWhenNotFound() {
        when(providerPort.existsById("kaiser-idp")).thenReturn(false);

        assertThatThrownBy(() -> service.update(testProvider()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteRemovesProvider() {
        when(providerPort.existsById("kaiser-idp")).thenReturn(true);

        service.delete("kaiser-idp");

        verify(providerPort).deleteById("kaiser-idp");
    }

    @Test
    void getAllActiveDelegatesToPort() {
        when(providerPort.findAllActive()).thenReturn(List.of(testProvider()));

        List<SsoIdentityProvider> result = service.getAllActive();

        assertThat(result).hasSize(1);
        verify(providerPort).findAllActive();
    }
}
