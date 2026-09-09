package com.wgblackmon.aihealthcare.infrastructure.enterprise.push;

import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HmacSignedLinkAdapter} — token round-trip,
 * tampering detection, expiry, and blank-secret behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class HmacSignedLinkAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-09-09T12:00:00Z");

    private HmacSignedLinkAdapter adapterWithSecret(String secret) {
        EnterpriseDataProperties props = new EnterpriseDataProperties(null) {
            @Override
            public String getSigningSecret() { return secret; }
        };
        return new HmacSignedLinkAdapter(props);
    }

    @Test
    void roundTrip_validTokenReturnsJobId() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret-key-256-bits-long!!");
        String token = adapter.createToken("job-42", FUTURE);
        Optional<String> result = adapter.verifyToken(token, NOW);
        assertThat(result).contains("job-42");
    }

    @Test
    void tamperedPayload_rejected() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret-key-256-bits-long!!");
        String token = adapter.createToken("job-42", FUTURE);
        String tampered = "dGFtcGVyZWQ." + token.split("\\.")[1];
        Optional<String> result = adapter.verifyToken(tampered, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void tamperedMac_rejected() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret-key-256-bits-long!!");
        String token = adapter.createToken("job-42", FUTURE);
        String tampered = token.split("\\.")[0] + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        Optional<String> result = adapter.verifyToken(tampered, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void expiredToken_rejected() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret-key-256-bits-long!!");
        Instant expiresAt = Instant.parse("2026-09-08T11:00:00Z");
        String token = adapter.createToken("job-42", expiresAt);
        Optional<String> result = adapter.verifyToken(token, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void differentSecret_rejected() {
        HmacSignedLinkAdapter creator = adapterWithSecret("secret-A");
        HmacSignedLinkAdapter verifier = adapterWithSecret("secret-B");
        String token = creator.createToken("job-42", FUTURE);
        Optional<String> result = verifier.verifyToken(token, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void blankSecret_createTokenThrows() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("");
        assertThatThrownBy(() -> adapter.createToken("job-42", FUTURE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing-secret");
    }

    @Test
    void blankSecret_verifyReturnsEmpty() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("");
        Optional<String> result = adapter.verifyToken("any.token", NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void nullToken_verifyReturnsEmpty() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret");
        Optional<String> result = adapter.verifyToken(null, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void malformedToken_verifyReturnsEmpty() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("test-secret");
        Optional<String> result = adapter.verifyToken("not-a-valid-token", NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void isSecretAvailable_trueWithSecret() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("some-secret");
        assertThat(adapter.isSecretAvailable()).isTrue();
    }

    @Test
    void isSecretAvailable_falseWithBlank() {
        HmacSignedLinkAdapter adapter = adapterWithSecret("");
        assertThat(adapter.isSecretAvailable()).isFalse();
    }
}
