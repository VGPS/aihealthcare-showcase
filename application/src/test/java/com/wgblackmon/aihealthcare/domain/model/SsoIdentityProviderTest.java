package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link SsoIdentityProvider} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
class SsoIdentityProviderTest {

    private static final Instant NOW = Instant.now();

    private SsoIdentityProvider validProvider() {
        return new SsoIdentityProvider(
                "mayo-clinic", "Mayo Clinic",
                "https://idp.mayo.edu/saml2", "https://idp.mayo.edu/sso",
                "-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----",
                null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW);
    }

    @Test
    void validProviderCreatesSuccessfully() {
        SsoIdentityProvider p = validProvider();
        assertThat(p.registrationId()).isEqualTo("mayo-clinic");
        assertThat(p.label()).isEqualTo("Mayo Clinic");
        assertThat(p.active()).isTrue();
        assertThat(p.defaultTier()).isEqualTo(SubscriptionTier.ENTERPRISE);
    }

    @Test
    void blankRegistrationIdThrows() {
        assertThatThrownBy(() -> new SsoIdentityProvider(
                "", "Label", "eid", "sso", "cert", null, "email", "dn",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registrationId");
    }

    @Test
    void invalidRegistrationIdFormatThrows() {
        assertThatThrownBy(() -> new SsoIdentityProvider(
                "Mayo_Clinic", "Label", "eid", "sso", "cert", null, "email", "dn",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase alphanumeric");
    }

    @Test
    void singleCharRegistrationIdThrows() {
        assertThatThrownBy(() -> new SsoIdentityProvider(
                "a", "Label", "eid", "sso", "cert", null, "email", "dn",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase alphanumeric");
    }

    @Test
    void nullEmailAttributeDefaultsToEmail() {
        SsoIdentityProvider p = new SsoIdentityProvider(
                "test-idp", "Test", "eid", "sso", "cert", null, null, null,
                null, true, NOW, NOW);
        assertThat(p.emailAttribute()).isEqualTo("email");
        assertThat(p.displayNameAttribute()).isEqualTo("displayName");
        assertThat(p.defaultTier()).isEqualTo(SubscriptionTier.ENTERPRISE);
    }

    @Test
    void blankLabelThrows() {
        assertThatThrownBy(() -> new SsoIdentityProvider(
                "test-idp", "", "eid", "sso", "cert", null, "email", "dn",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
