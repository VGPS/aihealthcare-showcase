package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataRequest} compact-constructor validation and
 * defensive copying.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataRequestTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void validRequest_createsSuccessfully() {
        DataRequest req = new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, Map.of("topic", "AI"), ExportFormat.CSV, 100, null, null, NOW, null);

        assertThat(req.jobId()).isEqualTo("job-1");
        assertThat(req.ownerEmail()).isEqualTo("user@example.com");
        assertThat(req.feedId()).isEqualTo("articles");
        assertThat(req.parameters()).containsEntry("topic", "AI");
    }

    @Test
    void nullJobId_throws() {
        assertThatThrownBy(() -> new DataRequest(
                null, "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, 10, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobId");
    }

    @Test
    void blankJobId_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "  ", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, 10, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankOwnerEmail_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "job-1", "", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, 10, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerEmail");
    }

    @Test
    void blankFeedId_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "  ",
                null, null, null, ExportFormat.CSV, 10, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedId");
    }

    @Test
    void nullFormat_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, null, 10, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format");
    }

    @Test
    void zeroRowLimit_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, 0, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rowLimit");
    }

    @Test
    void negativeRowLimit_throws() {
        assertThatThrownBy(() -> new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, -5, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullParameters_becomesEmptyMap() {
        DataRequest req = new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, null, ExportFormat.CSV, 10, null, null, NOW, null);

        assertThat(req.parameters()).isEmpty();
    }

    @Test
    void parametersMap_isDefensivelyCopied() {
        HashMap<String, String> mutable = new HashMap<>();
        mutable.put("state", "MT");

        DataRequest req = new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "legislation",
                null, null, mutable, ExportFormat.CSV, 10, null, null, NOW, null);

        mutable.put("injected", "value");

        assertThat(req.parameters()).doesNotContainKey("injected");
        assertThat(req.parameters()).containsOnlyKeys("state");
    }

    @Test
    void parametersMap_isUnmodifiable() {
        DataRequest req = new DataRequest(
                "job-1", "user@example.com", null, DataJobMode.PULL, "articles",
                null, null, Map.of("k", "v"), ExportFormat.CSV, 10, null, null, NOW, null);

        assertThatThrownBy(() -> req.parameters().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
