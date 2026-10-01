package com.wgblackmon.aihealthcare.infrastructure.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IndexNowAdapter}.
 *
 * <p>Uses a same-thread {@link Executor} so the (normally async) IndexNow
 * call runs synchronously within the test, and a mocked {@link RestClient}
 * so no real network call is made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
class IndexNowAdapterTest {

    private static final Executor SAME_THREAD = Runnable::run;

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void notifyUrlsChanged_whenDisabled_neverCallsRestClient() {
        IndexNowAdapter adapter = new IndexNowAdapter(restClient, SAME_THREAD,
                "https://app.bigskylabs.ai", "testkey123", false);

        adapter.notifyUrlsChanged(List.of("https://app.bigskylabs.ai/wiki/foo"));

        verify(restClient, never()).post();
    }

    @Test
    void notifyUrlsChanged_whenUrlListEmpty_neverCallsRestClient() {
        IndexNowAdapter adapter = new IndexNowAdapter(restClient, SAME_THREAD,
                "https://app.bigskylabs.ai", "testkey123", true);

        adapter.notifyUrlsChanged(List.of());

        verify(restClient, never()).post();
    }

    @Test
    void notifyUrlsChanged_whenEnabled_postsToIndexNowWithExpectedBody() {
        IndexNowAdapter adapter = new IndexNowAdapter(restClient, SAME_THREAD,
                "https://app.bigskylabs.ai", "testkey123", true);

        adapter.notifyUrlsChanged(List.of(
                "https://app.bigskylabs.ai/wiki/fda-ai-guidance",
                "https://app.bigskylabs.ai/wiki/another-page"));

        verify(requestBodyUriSpec).uri("https://api.indexnow.org/indexnow");

        @SuppressWarnings("unchecked")
        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();

        assertThat(body.get("host")).isEqualTo("app.bigskylabs.ai");
        assertThat(body.get("key")).isEqualTo("testkey123");
        assertThat(body.get("keyLocation")).isEqualTo("https://app.bigskylabs.ai/testkey123.txt");
        @SuppressWarnings("unchecked")
        List<String> urlList = (List<String>) body.get("urlList");
        assertThat(urlList).containsExactly(
                "https://app.bigskylabs.ai/wiki/fda-ai-guidance",
                "https://app.bigskylabs.ai/wiki/another-page");
    }

    @Test
    void notifyUrlsChanged_whenRestClientThrows_doesNotPropagate() {
        when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("network error"));
        IndexNowAdapter adapter = new IndexNowAdapter(restClient, SAME_THREAD,
                "https://app.bigskylabs.ai", "testkey123", true);

        adapter.notifyUrlsChanged(List.of("https://app.bigskylabs.ai/wiki/foo"));
        // no assertion needed beyond "doesn't throw" — failures are swallowed and logged
    }
}
