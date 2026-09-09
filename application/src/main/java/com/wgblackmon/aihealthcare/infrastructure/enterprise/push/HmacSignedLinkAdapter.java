package com.wgblackmon.aihealthcare.infrastructure.enterprise.push;

import com.wgblackmon.aihealthcare.domain.port.outbound.SignedLinkPort;
import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * HMAC-SHA256 signed download token implementation.
 *
 * <p>Token format: {@code {base64url(jobId:expiry)}.{base64url(mac)}}.
 * Verification uses constant-time comparison ({@link MessageDigest#isEqual})
 * to prevent timing-based attacks on the MAC.
 *
 * <p>If the signing secret is blank at startup, a warning is logged and
 * {@link #createToken} throws — the delivery adapter falls back to
 * attachment-only mode.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class HmacSignedLinkAdapter implements SignedLinkPort {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secretKey;
    private final boolean secretAvailable;

    public HmacSignedLinkAdapter(EnterpriseDataProperties properties) {
        log.debug("HmacSignedLinkAdapter() | constructing");
        String secret = properties.getSigningSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("HmacSignedLinkAdapter() | signing-secret is blank — signed links will be unavailable");
            this.secretKey = new byte[0];
            this.secretAvailable = false;
        } else {
            this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
            this.secretAvailable = true;
        }
        log.debug("HmacSignedLinkAdapter() | secretAvailable={}", secretAvailable);
    }

    @Override
    public String createToken(String jobId, Instant expiresAt) {
        log.debug("createToken() | jobId={}", jobId);
        if (!secretAvailable) {
            throw new IllegalStateException(
                    "Cannot create signed tokens — aihealthcare.enterprise.data.signing-secret is not configured");
        }
        String payload = jobId + ":" + expiresAt.getEpochSecond();
        byte[] mac = computeHmac(payload.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String encodedMac = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac);
        String result = encodedPayload + "." + encodedMac;
        log.debug("createToken() | return=[REDACTED]");
        return result;
    }

    @Override
    public Optional<String> verifyToken(String token, Instant now) {
        log.debug("verifyToken() | token=[REDACTED]");
        if (!secretAvailable || token == null || !token.contains(".")) {
            log.debug("verifyToken() | return=empty (secret unavailable or malformed token)");
            return Optional.empty();
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            log.debug("verifyToken() | return=empty (wrong part count)");
            return Optional.empty();
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] providedMac = Base64.getUrlDecoder().decode(parts[1]);

            byte[] expectedMac = computeHmac(payloadBytes);
            if (!MessageDigest.isEqual(expectedMac, providedMac)) {
                log.debug("verifyToken() | return=empty (MAC mismatch)");
                return Optional.empty();
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            int colonIdx = payload.lastIndexOf(':');
            if (colonIdx <= 0) {
                log.debug("verifyToken() | return=empty (malformed payload)");
                return Optional.empty();
            }
            String jobId = payload.substring(0, colonIdx);
            long expiryEpoch = Long.parseLong(payload.substring(colonIdx + 1));
            Instant expiresAt = Instant.ofEpochSecond(expiryEpoch);

            if (now.isAfter(expiresAt)) {
                log.debug("verifyToken() | return=empty (expired)");
                return Optional.empty();
            }

            log.debug("verifyToken() | return=present");
            return Optional.of(jobId);
        } catch (IllegalArgumentException e) {
            log.debug("verifyToken() | return=empty (decode error: {})", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isSecretAvailable() {
        return secretAvailable;
    }

    private byte[] computeHmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
