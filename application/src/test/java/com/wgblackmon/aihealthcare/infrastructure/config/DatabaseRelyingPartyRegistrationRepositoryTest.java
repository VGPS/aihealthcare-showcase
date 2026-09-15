package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseRelyingPartyRegistrationRepository}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
class DatabaseRelyingPartyRegistrationRepositoryTest {

    private SsoIdentityProviderPort providerPort;
    private DatabaseRelyingPartyRegistrationRepository repo;

    private static final Instant NOW = Instant.now();
    private static final String SP_ENTITY_ID = "https://app.bigskylabs.ai";

    private static final String TEST_CERT =
            "MIIC4jCCAcqgAwIBAgIJAK+qkq4ndnjKMA0GCSqGSIb3DQEBDAUAMB8xHTAbBgNV" +
            "BAMTFHRlc3QtaWRwLmV4YW1wbGUuY29tMB4XDTI2MDkxNTE2MjYxNVoXDTM2MDkx" +
            "MjE2MjYxNVowHzEdMBsGA1UEAxMUdGVzdC1pZHAuZXhhbXBsZS5jb20wggEiMA0G" +
            "CSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCUOtirDkHkHKdfzlkBYp2y1zEmiXFu" +
            "FsTsqvo/5xwC4c79t5PksoozJ9yPJNpw2m9sVdEVoUsRbdx/QG5/QexGrSGM0or4" +
            "+AP53LZJDKtoVjZzh2i9nsO6fR0GmCoUrzf/4notfcuDC9jh60motyQuLiu3EeU2/" +
            "L6tg8cSrW9EPkGtrVI96mI6Glu2B3cY2NVJYAnrZ8OFg27JOtCS8mt439/7PGOvL" +
            "nY1f+Yc/rKnFbzRXcyckbTn9bYWAUJ832YGbOryaFE6oxEGUiUjXXzfafJTf5rcn" +
            "9O7cHP2gCkRcdDbsPg54C8OwGBy3mr0sg3kS963UuzReCSIGvOcnI4LAgMBAAGjIT" +
            "AfMB0GA1UdDgQWBBTHj2MDn4OgfTaj5xUj+wOzbyiJmTANBgkqhkiG9w0BAQwFAA" +
            "OCAQEAde9CCsSnnqyFvlYmnXgqZNbXvcmLxrSvaL4aAz2Kavp62WmhP8PQgfxgd+" +
            "EUPS7ZajHb5lHh66Nrh/4z4frRtNTbLaBaTBzSoS/UGOjCpPlQG6Kgg7Ipub8k9V" +
            "BA0uQuSd5omv2o5V63iEC4vnSpuC9Jv3phyl6fy7rf+MMVGCGrkX97+H7JOHB43L" +
            "jcABD4mchap/mJqkHBqdNuV443sLmSYLoUze5aL5Kg+LOT/NakReiG24L44P/ujE" +
            "c4y8a5QiGXaFo4Vciuv50a5ekUWfcMLBWChDiwM8fStBqLxgmIsqoY2oNpebfPJn" +
            "hmB2l/wl1ZhClfmyBEzpk3TofCjQ==";

    private SsoIdentityProvider testIdp(String regId, boolean active) {
        return new SsoIdentityProvider(
                regId, "Test IdP " + regId,
                "https://idp.test/" + regId, "https://idp.test/" + regId + "/sso",
                TEST_CERT, null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, active, NOW, NOW);
    }

    @BeforeEach
    void setUp() {
        providerPort = Mockito.mock(SsoIdentityProviderPort.class);
        repo = new DatabaseRelyingPartyRegistrationRepository(providerPort, SP_ENTITY_ID);
    }

    @Test
    void findByRegistrationIdReturnsRegistration() {
        when(providerPort.findById("acme-health")).thenReturn(Optional.of(testIdp("acme-health", true)));

        RelyingPartyRegistration result = repo.findByRegistrationId("acme-health");

        assertThat(result).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo("acme-health");
        assertThat(result.getEntityId()).isEqualTo(SP_ENTITY_ID);
        assertThat(result.getAssertingPartyMetadata().getEntityId()).isEqualTo("https://idp.test/acme-health");
        assertThat(result.getAssertingPartyMetadata().getSingleSignOnServiceLocation())
                .isEqualTo("https://idp.test/acme-health/sso");
    }

    @Test
    void findByRegistrationIdReturnsNullForInactive() {
        when(providerPort.findById("inactive")).thenReturn(Optional.of(testIdp("inactive", false)));

        RelyingPartyRegistration result = repo.findByRegistrationId("inactive");

        assertThat(result).isNull();
    }

    @Test
    void findByRegistrationIdReturnsNullForUnknown() {
        when(providerPort.findById("unknown")).thenReturn(Optional.empty());

        RelyingPartyRegistration result = repo.findByRegistrationId("unknown");

        assertThat(result).isNull();
    }

    @Test
    void iteratorReturnsActiveProviders() {
        when(providerPort.findAllActive()).thenReturn(List.of(
                testIdp("mayo-clinic", true),
                testIdp("kaiser", true)));

        var iterator = repo.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            RelyingPartyRegistration reg = iterator.next();
            assertThat(reg.getRegistrationId()).isIn("mayo-clinic", "kaiser");
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void invalidCertificateThrows() {
        SsoIdentityProvider badCert = new SsoIdentityProvider(
                "bad-cert", "Bad Cert",
                "https://idp.test/bad", "https://idp.test/bad/sso",
                "NOT-A-VALID-CERT", null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW);
        when(providerPort.findById("bad-cert")).thenReturn(Optional.of(badCert));

        assertThatThrownBy(() -> repo.findByRegistrationId("bad-cert"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse IdP certificate");
    }
}
