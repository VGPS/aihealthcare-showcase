package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Function;

/**
 * SSRF-safe HTTP client for customer-side HTTPS endpoints.
 *
 * <p>Implements all three network egress defences from §9A.1 of the design doc:
 * <ol>
 *   <li>Resolve DNS, validate every IP against a blocklist, then <strong>pin</strong>
 *       the validated address — the HTTP connection uses the approved IP, never
 *       re-resolving.</li>
 *   <li>Refuse redirects — a 302 to the metadata endpoint bypasses Defence 1.</li>
 *   <li>Host allow-list — when non-empty, only listed hosts may be contacted.</li>
 * </ol>
 *
 * <p>This class is a POJO, not a Spring bean. It is created by
 * {@code EnterpriseDataConfig} with injected configuration.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
public class RemoteEndpointGuard {

    private final List<String> allowedHosts;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final long maxResponseBytes;
    private final DnsResolver dnsResolver;
    private final Function<String, String> secretLookup;

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String hostname) throws UnknownHostException;
    }

    public RemoteEndpointGuard(List<String> allowedHosts,
                               int connectTimeoutMs,
                               int readTimeoutMs,
                               long maxResponseBytes) {
        this(allowedHosts, connectTimeoutMs, readTimeoutMs, maxResponseBytes,
                InetAddress::getAllByName, System::getenv);
    }

    RemoteEndpointGuard(List<String> allowedHosts,
                        int connectTimeoutMs,
                        int readTimeoutMs,
                        long maxResponseBytes,
                        DnsResolver dnsResolver,
                        Function<String, String> secretLookup) {
        this.allowedHosts = List.copyOf(allowedHosts);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
        this.dnsResolver = dnsResolver;
        this.secretLookup = secretLookup;
        log.debug("RemoteEndpointGuard() | allowedHosts={}, connectTimeoutMs={}, readTimeoutMs={}, maxResponseBytes={}",
                allowedHosts.size(), connectTimeoutMs, readTimeoutMs, maxResponseBytes);
    }

    /**
     * Validate the endpoint and fetch the response body over pinned HTTPS.
     *
     * @param baseUrl   must be {@code https://}
     * @param path      appended to the base URL (nullable)
     * @param authType  how to attach credentials
     * @param headerName header name for API_KEY_HEADER auth (nullable)
     * @param secretRef env var name holding the credential (nullable)
     * @return response body bytes
     * @throws EndpointRejectedException for any SSRF violation or HTTP error
     */
    public byte[] fetch(String baseUrl, String path,
                        RemoteAuthType authType, String headerName, String secretRef) {
        log.debug("fetch() | baseUrl=[REDACTED], path={}, authType={}", path, authType);

        URI uri = validateUrl(baseUrl, path);
        String hostname = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;

        checkAllowList(hostname);
        InetAddress pinnedAddress = resolveAndValidate(hostname);

        try {
            byte[] result = executePinnedRequest(uri, hostname, port, pinnedAddress, authType, headerName, secretRef);
            log.debug("fetch() | return={} bytes", result.length);
            return result;
        } catch (EndpointRejectedException e) {
            throw e;
        } catch (IOException e) {
            throw new EndpointRejectedException("Connection failed: " + e.getMessage(), e);
        }
    }

    URI validateUrl(String baseUrl, String path) {
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            throw new EndpointRejectedException("HTTPS required, got: "
                    + (baseUrl != null ? baseUrl.substring(0, Math.min(baseUrl.length(), 10)) : "null"));
        }
        String full = path != null && !path.isBlank() ? baseUrl + path : baseUrl;
        URI uri;
        try {
            uri = URI.create(full);
        } catch (IllegalArgumentException e) {
            throw new EndpointRejectedException("Invalid URL");
        }
        if (!"https".equals(uri.getScheme())) {
            throw new EndpointRejectedException("HTTPS required");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new EndpointRejectedException("No host in URL");
        }
        return uri;
    }

    void checkAllowList(String hostname) {
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(hostname.toLowerCase())) {
            throw new EndpointRejectedException("Host not in allow-list: " + hostname);
        }
    }

    InetAddress resolveAndValidate(String hostname) {
        InetAddress[] addresses;
        try {
            addresses = dnsResolver.resolve(hostname);
        } catch (UnknownHostException e) {
            throw new EndpointRejectedException("DNS resolution failed for: " + hostname);
        }
        if (addresses == null || addresses.length == 0) {
            throw new EndpointRejectedException("DNS returned no addresses for: " + hostname);
        }
        for (InetAddress addr : addresses) {
            if (!isBlockedAddress(addr)) {
                log.debug("resolveAndValidate() | hostname={}, pinnedTo={}", hostname, addr.getHostAddress());
                return addr;
            }
        }
        throw new EndpointRejectedException(
                "All resolved addresses are blocked for: " + hostname);
    }

    static boolean isBlockedAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()
                || isMetadataRange(addr);
    }

    private static boolean isMetadataRange(InetAddress addr) {
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            // 169.254.0.0/16 (link-local, covers AWS metadata 169.254.169.254)
            return (raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254;
        }
        if (raw.length == 16) {
            // fc00::/7 — unique local addresses
            return (raw[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    private byte[] executePinnedRequest(URI uri, String hostname, int port,
                                        InetAddress pinnedAddress,
                                        RemoteAuthType authType, String headerName,
                                        String secretRef) throws IOException {
        URL connUrl = buildPinnedUrl(pinnedAddress, port, uri.getRawPath(), uri.getRawQuery());
        HttpsURLConnection conn = (HttpsURLConnection) connUrl.openConnection();

        try {
            conn.setRequestProperty("Host", hostname);
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);

            conn.setSSLSocketFactory(createSniSocketFactory(hostname));
            conn.setHostnameVerifier(createPinnedHostnameVerifier(hostname));

            applyAuth(conn, authType, headerName, secretRef);

            int status = conn.getResponseCode();
            log.debug("executePinnedRequest() | status={}", status);

            if (status >= 300 && status < 400) {
                throw new EndpointRejectedException(
                        "Redirect rejected (status " + status + ") — potential SSRF bypass");
            }
            if (status < 200 || status >= 300) {
                throw new EndpointRejectedException("HTTP " + status + " from remote endpoint");
            }

            return readLimited(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private URL buildPinnedUrl(InetAddress addr, int port, String path, String query)
            throws MalformedURLException {
        String ipStr = addr.getHostAddress();
        if (addr instanceof Inet6Address) {
            ipStr = "[" + ipStr + "]";
        }
        String pathPart = (path != null ? path : "/");
        if (query != null && !query.isEmpty()) {
            pathPart = pathPart + "?" + query;
        }
        return new URL("https", ipStr, port, pathPart);
    }

    private SSLSocketFactory createSniSocketFactory(String hostname) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, new SecureRandom());
            SSLSocketFactory base = ctx.getSocketFactory();
            return new SniPinningSocketFactory(base, hostname);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new EndpointRejectedException("TLS setup failed: " + e.getMessage());
        }
    }

    private HostnameVerifier createPinnedHostnameVerifier(String expectedHostname) {
        HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        return (urlHostname, session) -> defaultVerifier.verify(expectedHostname, session);
    }

    private void applyAuth(HttpURLConnection conn, RemoteAuthType authType,
                           String headerName, String secretRef) {
        if (authType == null || authType == RemoteAuthType.NONE) {
            return;
        }
        if (secretRef == null || secretRef.isBlank()) {
            throw new EndpointRejectedException("Auth type " + authType + " requires a secretRef");
        }
        String secret = secretLookup.apply(secretRef);
        if (secret == null || secret.isBlank()) {
            throw new EndpointRejectedException(
                    "Secret not found for ref: " + secretRef + " (env var not set)");
        }
        switch (authType) {
            case API_KEY_HEADER -> {
                if (headerName == null || headerName.isBlank()) {
                    throw new EndpointRejectedException("API_KEY_HEADER auth requires a headerName");
                }
                conn.setRequestProperty(headerName, secret);
            }
            case BEARER -> conn.setRequestProperty("Authorization", "Bearer " + secret);
            default -> { }
        }
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > maxResponseBytes) {
                throw new EndpointRejectedException(
                        "Response exceeds " + maxResponseBytes + " bytes limit");
            }
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    String resolveSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        return secretLookup.apply(secretRef);
    }

    /**
     * SSLSocketFactory wrapper that injects SNI hostname into every created socket,
     * so TLS certificate validation works when connecting to an IP address.
     */
    private static class SniPinningSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String sniHostname;

        SniPinningSocketFactory(SSLSocketFactory delegate, String sniHostname) {
            this.delegate = delegate;
            this.sniHostname = sniHostname;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket(s, sniHostname, port, autoClose);
            applySni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket(host, port);
            applySni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket(host, port, localAddr, localPort);
            applySni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress addr, int port) throws IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket(addr, port);
            applySni(socket);
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress addr, int port, InetAddress localAddr, int localPort) throws IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket(addr, port, localAddr, localPort);
            applySni(socket);
            return socket;
        }

        private void applySni(SSLSocket socket) {
            SSLParameters params = socket.getSSLParameters();
            params.setServerNames(List.of(new SNIHostName(sniHostname)));
            socket.setSSLParameters(params);
        }
    }

    /**
     * Thrown when a remote endpoint fails any SSRF validation check,
     * returns a redirect, or exceeds size limits.
     */
    public static class EndpointRejectedException extends RuntimeException {
        public EndpointRejectedException(String message) {
            super(message);
        }

        public EndpointRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
