package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.SourceCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpSourceMonitorAdapter}.
 *
 * <p>Uses a mock {@link HttpClient} to simulate HTTP responses without
 * making real network calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
class HttpSourceMonitorAdapterTest {

    private HttpClient httpClient;
    private HttpSourceMonitorAdapter adapter;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        adapter = new HttpSourceMonitorAdapter(httpClient);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void checkUrl_firstCheck_noStoredHash_notChanged() throws Exception {
        HttpResponse<String> response = mockResponse(200, "Hello World");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        SourceCheckResult result = adapter.checkUrl("https://example.com/law", null);

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentHash()).isNotBlank();
        assertThat(result.changed()).isFalse();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void checkUrl_sameContent_notChanged() throws Exception {
        String body = "The quick brown fox";
        HttpResponse<String> response = mockResponse(200, body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        // First check to get the hash
        SourceCheckResult first = adapter.checkUrl("https://example.com/law", null);
        String hash = first.contentHash();

        // Second check with same content
        SourceCheckResult second = adapter.checkUrl("https://example.com/law", hash);

        assertThat(second.changed()).isFalse();
        assertThat(second.contentHash()).isEqualTo(hash);
    }

    @Test
    void checkUrl_differentContent_changed() throws Exception {
        HttpResponse<String> response1 = mockResponse(200, "Original content");
        HttpResponse<String> response2 = mockResponse(200, "Updated content");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response1).thenReturn(response2);

        SourceCheckResult first = adapter.checkUrl("https://example.com/law", null);
        SourceCheckResult second = adapter.checkUrl("https://example.com/law", first.contentHash());

        assertThat(second.changed()).isTrue();
        assertThat(second.contentHash()).isNotEqualTo(first.contentHash());
    }

    @Test
    void checkUrl_httpError_marksChanged() throws Exception {
        HttpResponse<String> response = mockResponse(404, "Not Found");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        SourceCheckResult result = adapter.checkUrl("https://example.com/law", "old-hash");

        assertThat(result.httpStatus()).isEqualTo(404);
        assertThat(result.changed()).isTrue();
        assertThat(result.contentHash()).isNull();
        assertThat(result.errorMessage()).contains("HTTP 404");
    }

    @Test
    void checkUrl_httpError_noStoredHash_notChanged() throws Exception {
        HttpResponse<String> response = mockResponse(500, "Server Error");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        SourceCheckResult result = adapter.checkUrl("https://example.com/law", null);

        assertThat(result.changed()).isFalse();
    }

    @Test
    void checkUrl_networkError_capturesErrorMessage() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        SourceCheckResult result = adapter.checkUrl("https://example.com/law", "old-hash");

        assertThat(result.httpStatus()).isZero();
        assertThat(result.changed()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void checkUrl_hashIsSha256Format() throws Exception {
        HttpResponse<String> response = mockResponse(200, "test content");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        SourceCheckResult result = adapter.checkUrl("https://example.com/law", null);

        assertThat(result.contentHash()).hasSize(64);
        assertThat(result.contentHash()).matches("[0-9a-f]{64}");
    }
}
