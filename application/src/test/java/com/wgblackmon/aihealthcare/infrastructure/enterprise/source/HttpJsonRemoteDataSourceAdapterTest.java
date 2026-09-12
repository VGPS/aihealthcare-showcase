package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.RemoteConnectionPort;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.source.RemoteEndpointGuard.EndpointRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpJsonRemoteDataSourceAdapter}.
 *
 * <p>Mocks the {@link RemoteEndpointGuard} and {@link RemoteConnectionPort}
 * to test JSON parsing and DataSet construction without network calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class HttpJsonRemoteDataSourceAdapterTest {

    private RemoteConnectionPort connectionPort;
    private RemoteEndpointGuard guard;
    private DataJobLog jobLog;
    private HttpJsonRemoteDataSourceAdapter adapter;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        connectionPort = mock(RemoteConnectionPort.class);
        guard = mock(RemoteEndpointGuard.class);
        jobLog = mock(DataJobLog.class);
        adapter = new HttpJsonRemoteDataSourceAdapter(connectionPort, guard, MAPPER);
    }

    @Test
    void feedIdAndKind() {
        assertThat(adapter.feedId()).isEqualTo("customer-remote");
        assertThat(adapter.kind()).isEqualTo(DataSourceKind.CUSTOMER_REMOTE);
    }

    @Test
    void describeReturnsValidFeed() {
        DataFeed feed = adapter.describe();
        assertThat(feed.feedId()).isEqualTo("customer-remote");
        assertThat(feed.active()).isFalse();
        assertThat(feed.parameters()).hasSize(2);
    }

    @Test
    void supportsMatchesFeedId() {
        assertThat(adapter.supports(makeRequest("customer-remote", "conn-1", 100))).isTrue();
        assertThat(adapter.supports(makeRequest("articles", null, 100))).isFalse();
    }

    @Test
    void fetchJsonArrayToDataSet() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "[{\"id\":\"1\",\"name\":\"Alice\"},{\"id\":\"2\",\"name\":\"Bob\"}]";
        when(guard.fetch(eq("https://api.customer.com/v1"), isNull(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 100), jobLog);

        assertThat(result.columns()).hasSize(2);
        assertThat(result.columns().get(0).name()).isEqualTo("id");
        assertThat(result.columns().get(1).name()).isEqualTo("name");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0)).containsExactly("1", "Alice");
        assertThat(result.rows().get(1)).containsExactly("2", "Bob");
        verify(jobLog).phase(eq("FETCH_START"), anyString());
        verify(jobLog).phase(eq("FETCH_END"), anyString());
    }

    @Test
    void fetchWrappedJsonArray() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "{\"total\":2,\"results\":[{\"id\":\"1\"},{\"id\":\"2\"}]}";
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 100), jobLog);

        assertThat(result.columns()).hasSize(1);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0)).containsExactly("1");
    }

    @Test
    void fetchSingleObject() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "{\"status\":\"ok\",\"count\":\"42\"}";
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 100), jobLog);

        assertThat(result.columns()).hasSize(2);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).containsExactly("ok", "42");
    }

    @Test
    void rowLimitTruncates() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "[{\"id\":\"1\"},{\"id\":\"2\"},{\"id\":\"3\"}]";
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 2), jobLog);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.truncatedAtRows()).isEqualTo(3);
    }

    @Test
    void nullValuesBecomEmptyStrings() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "[{\"id\":\"1\",\"name\":null}]";
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 100), jobLog);

        assertThat(result.rows().get(0).get(1)).isEmpty();
    }

    @Test
    void missingConnectionThrows() {
        when(connectionPort.findByConnectionIdAndOwnerEmail("bad-conn", "user@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.fetch(
                makeRequest("customer-remote", "bad-conn", 100), jobLog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void inactiveConnectionThrows() {
        RemoteConnection inactive = new RemoteConnection(
                "conn-1", "user@test.com", "Inactive Conn",
                RemoteConnectionKind.HTTPS_JSON, "https://api.customer.com/v1",
                RemoteAuthType.NONE, null, null, false);
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> adapter.fetch(
                makeRequest("customer-remote", "conn-1", 100), jobLog))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void guardRejectionPropagates() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenThrow(new EndpointRejectedException("Redirect rejected (status 302)"));

        assertThatThrownBy(() -> adapter.fetch(
                makeRequest("customer-remote", "conn-1", 100), jobLog))
                .isInstanceOf(EndpointRejectedException.class)
                .hasMessageContaining("302");
    }

    @Test
    void missingConnectionIdThrows() {
        assertThatThrownBy(() -> adapter.fetch(
                makeRequest("customer-remote", null, 100), jobLog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionId is required");
    }

    @Test
    void pathParamPassedToGuard() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn("[]".getBytes(StandardCharsets.UTF_8));

        DataRequest req = new DataRequest(
                "job-1", "user@test.com", null,
                DataJobMode.PULL, "customer-remote", null, null,
                Map.of("connectionId", "conn-1", "path", "/api/extra"),
                ExportFormat.JSON, 100, "conn-1", null, Instant.now(), null
        );

        adapter.fetch(req, jobLog);

        verify(guard).fetch(eq("https://api.customer.com/v1"), eq("/api/extra"),
                eq(RemoteAuthType.API_KEY_HEADER), eq("X-Api-Key"), eq("CUSTOMER_SECRET"));
    }

    @Test
    void rowWidthMatchesColumnCount() {
        RemoteConnection conn = sampleConnection("conn-1");
        when(connectionPort.findByConnectionIdAndOwnerEmail("conn-1", "user@test.com"))
                .thenReturn(Optional.of(conn));

        String json = "[{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}]";
        when(guard.fetch(any(), any(), any(), any(), any()))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        DataSet result = adapter.fetch(makeRequest("customer-remote", "conn-1", 100), jobLog);

        assertThat(result.rows().get(0)).hasSize(result.columns().size());
    }

    @Test
    void malformedJsonReturnsWarning() {
        DataSet result = adapter.parseJsonToDataSet("not json".getBytes(StandardCharsets.UTF_8), 100);
        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("Failed to parse");
    }

    private RemoteConnection sampleConnection(String id) {
        return new RemoteConnection(
                id, "user@test.com", "Customer API",
                RemoteConnectionKind.HTTPS_JSON, "https://api.customer.com/v1",
                RemoteAuthType.API_KEY_HEADER, "X-Api-Key", "CUSTOMER_SECRET", true);
    }

    private DataRequest makeRequest(String feedId, String connectionId, int rowLimit) {
        return new DataRequest(
                "job-1", "user@test.com", null,
                DataJobMode.PULL, feedId, null, null,
                connectionId != null ? Map.of("connectionId", connectionId) : Map.of(),
                ExportFormat.JSON, rowLimit,
                connectionId, null, Instant.now(), null
        );
    }
}
