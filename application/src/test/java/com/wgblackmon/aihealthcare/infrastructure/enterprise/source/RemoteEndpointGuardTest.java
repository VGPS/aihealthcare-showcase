package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.source.RemoteEndpointGuard.EndpointRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF defence tests for {@link RemoteEndpointGuard}.
 *
 * <p>This is the most important test class in the enterprise data slice.
 * Every rejection case from §9A.1 of the design doc is exercised.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class RemoteEndpointGuardTest {

    private static final List<String> NO_ALLOW_LIST = List.of();
    private static final List<String> ALLOW_LIST = List.of("api.customer.com", "data.partner.org");
    private static final Function<String, String> NO_SECRETS = ref -> null;

    private RemoteEndpointGuard openGuard;

    @BeforeEach
    void setUp() {
        openGuard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                NO_SECRETS
        );
    }

    // --- Scheme validation ---

    @Test
    void rejectsHttpScheme() {
        assertThatThrownBy(() -> openGuard.validateUrl("http://example.com/data", null))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("HTTPS required");
    }

    @Test
    void rejectsNullUrl() {
        assertThatThrownBy(() -> openGuard.validateUrl(null, null))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("HTTPS required");
    }

    @Test
    void rejectsFtpScheme() {
        assertThatThrownBy(() -> openGuard.validateUrl("ftp://example.com/data", null))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("HTTPS required");
    }

    @Test
    void acceptsHttpsScheme() {
        var uri = openGuard.validateUrl("https://example.com/data", null);
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("example.com");
    }

    @Test
    void appendsPathToUrl() {
        var uri = openGuard.validateUrl("https://example.com", "/api/v1/data");
        assertThat(uri.getPath()).isEqualTo("/api/v1/data");
    }

    // --- Allow-list ---

    @Test
    void allowListRejectsUnlistedHost() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                NO_SECRETS
        );
        assertThatThrownBy(() -> guard.checkAllowList("evil.com"))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("not in allow-list");
    }

    @Test
    void allowListAcceptsListedHost() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                NO_SECRETS
        );
        guard.checkAllowList("api.customer.com");
    }

    @Test
    void emptyAllowListAcceptsAnyHost() {
        openGuard.checkAllowList("any-host.example.com");
    }

    // --- IP blocklist ---

    @Test
    void rejectsLoopbackIpv4() {
        assertBlocked("127.0.0.1");
    }

    @Test
    void rejectsLoopbackIpv6() {
        assertBlocked("::1");
    }

    @Test
    void rejectsMetadataEndpoint() {
        assertBlocked("169.254.169.254");
    }

    @Test
    void rejectsLinkLocal() {
        assertBlocked("169.254.1.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "10.255.255.255"})
    void rejectsTenSlashEight(String ip) {
        assertBlocked(ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"172.16.0.1", "172.31.255.255"})
    void rejectsOneSeventyTwoPrivate(String ip) {
        assertBlocked(ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.1", "192.168.255.255"})
    void rejectsOneNinetyTwoPrivate(String ip) {
        assertBlocked(ip);
    }

    @Test
    void rejectsMulticast() {
        assertBlocked("224.0.0.1");
    }

    @Test
    void rejectsIpv6UniqueLocal() throws Exception {
        InetAddress addr = InetAddress.getByName("fc00::1");
        assertThat(RemoteEndpointGuard.isBlockedAddress(addr)).isTrue();
    }

    @Test
    void rejectsIpv6LinkLocal() throws Exception {
        InetAddress addr = InetAddress.getByName("fe80::1");
        assertThat(RemoteEndpointGuard.isBlockedAddress(addr)).isTrue();
    }

    @Test
    void acceptsPublicIp() throws Exception {
        InetAddress addr = InetAddress.getByName("93.184.216.34");
        assertThat(RemoteEndpointGuard.isBlockedAddress(addr)).isFalse();
    }

    @Test
    void rejectsHostResolvingToPrivateIp() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{InetAddress.getByName("10.0.0.5")},
                NO_SECRETS
        );
        assertThatThrownBy(() -> guard.resolveAndValidate("malicious-host.com"))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void rejectsDnsResolutionFailure() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> { throw new UnknownHostException("nxdomain"); },
                NO_SECRETS
        );
        assertThatThrownBy(() -> guard.resolveAndValidate("nonexistent.invalid"))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("DNS resolution failed");
    }

    @Test
    void rejectsEmptyDnsResult() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[0],
                NO_SECRETS
        );
        assertThatThrownBy(() -> guard.resolveAndValidate("empty.invalid"))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("no addresses");
    }

    // --- DNS rebinding defence ---

    @Test
    void dnsRebindingUsesFirstValidatedAddress() throws Exception {
        InetAddress publicIp = InetAddress.getByName("93.184.216.34");
        InetAddress metadataIp = InetAddress.getByName("169.254.169.254");

        AtomicInteger callCount = new AtomicInteger(0);
        RemoteEndpointGuard.DnsResolver rebindingResolver = hostname -> {
            int call = callCount.getAndIncrement();
            if (call == 0) {
                return new InetAddress[]{publicIp};
            }
            return new InetAddress[]{metadataIp};
        };

        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                rebindingResolver, NO_SECRETS
        );

        InetAddress pinned = guard.resolveAndValidate("rebinding-host.com");
        assertThat(pinned).isEqualTo(publicIp);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void skipsBlockedAddressesInMultipleResults() throws Exception {
        InetAddress privateIp = InetAddress.getByName("10.0.0.1");
        InetAddress publicIp = InetAddress.getByName("93.184.216.34");

        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{privateIp, publicIp},
                NO_SECRETS
        );

        InetAddress pinned = guard.resolveAndValidate("multi-result.com");
        assertThat(pinned).isEqualTo(publicIp);
    }

    // --- Auth / secret resolution ---

    @Test
    void apiKeyHeaderRequiresHeaderName() {
        assertThatThrownBy(() -> openGuard.fetch(
                "https://example.com/data", null,
                RemoteAuthType.API_KEY_HEADER, null, "MY_SECRET"))
                .isInstanceOf(EndpointRejectedException.class);
    }

    @Test
    void authRequiresSecretRef() {
        assertThatThrownBy(() -> openGuard.fetch(
                "https://example.com/data", null,
                RemoteAuthType.BEARER, null, null))
                .isInstanceOf(EndpointRejectedException.class);
    }

    @Test
    void missingEnvVarIsRejected() {
        assertThatThrownBy(() -> openGuard.fetch(
                "https://example.com/data", null,
                RemoteAuthType.BEARER, null, "NONEXISTENT_KEY"))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("Secret not found");
    }

    @Test
    void secretRefResolvedFromLookup() {
        RemoteEndpointGuard guard = new RemoteEndpointGuard(
                NO_ALLOW_LIST, 5000, 30000, 1_000_000,
                hostname -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                ref -> "test-secret-value"
        );
        String resolved = guard.resolveSecret("MY_API_KEY");
        assertThat(resolved).isEqualTo("test-secret-value");
    }

    @Test
    void nullSecretRefReturnsNull() {
        assertThat(openGuard.resolveSecret(null)).isNull();
        assertThat(openGuard.resolveSecret("")).isNull();
    }

    private void assertBlocked(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            assertThat(RemoteEndpointGuard.isBlockedAddress(addr))
                    .as("Expected %s to be blocked", ip)
                    .isTrue();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Test setup failed for IP: " + ip, e);
        }
    }
}
