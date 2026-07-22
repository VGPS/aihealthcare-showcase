package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RegulatoryEvent} record validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class RegulatoryEventTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validEventCreatesSuccessfully() {
        RegulatoryEvent event = event("e1", "FDA clears AI device");

        assertThat(event.eventId()).isEqualTo("e1");
        assertThat(event.title()).isEqualTo("FDA clears AI device");
        assertThat(event.eventType()).isEqualTo(RegulatoryEventType.FDA_510K_CLEARANCE);
        assertThat(event.regulatoryBody()).isEqualTo(RegulatoryBody.FDA);
    }

    @Test
    void nullEventIdThrows() {
        assertThatThrownBy(() -> event(null, "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void blankEventIdThrows() {
        assertThatThrownBy(() -> event("  ", "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void nullEventTypeThrows() {
        assertThatThrownBy(() -> new RegulatoryEvent("e1", null, RegulatoryBody.FDA,
                "Title", null, null, null, null,
                "https://fda.gov/e1", null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void nullRegulatoryBodyThrows() {
        assertThatThrownBy(() -> new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                null, "Title", null, null, null, null,
                "https://fda.gov/e1", null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regulatoryBody");
    }

    @Test
    void nullTitleThrows() {
        assertThatThrownBy(() -> event("e1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void blankTitleThrows() {
        assertThatThrownBy(() -> event("e1", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullSourceUrlThrows() {
        assertThatThrownBy(() -> new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Title", null, null, null, null,
                null, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceUrl");
    }

    @Test
    void nullDiscoveredAtThrows() {
        assertThatThrownBy(() -> new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Title", null, null, null, null,
                "https://fda.gov/e1", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discoveredAt");
    }

    @Test
    void nullKeywordsDefaultsToEmptyList() {
        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Title", null, null, null, null,
                "https://fda.gov/e1", null, null, NOW, null);

        assertThat(event.aiHealthcareKeywords()).isEmpty();
    }

    @Test
    void keywordsAreDefensivelyCopied() {
        List<String> keywords = new java.util.ArrayList<>();
        keywords.add("AI");
        keywords.add("radiology");

        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "Title", null, null, null, null,
                "https://fda.gov/e1", null, null, NOW, keywords);

        assertThat(event.aiHealthcareKeywords()).hasSize(2);
        assertThatThrownBy(() -> event.aiHealthcareKeywords().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionalFieldsCanBeNull() {
        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.CMS_PROPOSED_RULE,
                RegulatoryBody.CMS, "CMS proposes AI rule", null, null, null, null,
                "https://federalregister.gov/r1", null, null, NOW, null);

        assertThat(event.summary()).isNull();
        assertThat(event.referenceNumber()).isNull();
        assertThat(event.applicantName()).isNull();
        assertThat(event.deviceName()).isNull();
        assertThat(event.linkedArticleId()).isNull();
        assertThat(event.publishedAt()).isNull();
    }

    @Test
    void allFieldsPopulated() {
        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "510(k) Clearance: AI Radiology Tool",
                "Device cleared for lung nodule detection",
                "K241234", "Tempus AI", "AI Lung Scanner",
                "https://fda.gov/K241234", "article-123",
                Instant.parse("2026-07-20T00:00:00Z"), NOW,
                List.of("radiology", "lung", "AI"));

        assertThat(event.referenceNumber()).isEqualTo("K241234");
        assertThat(event.applicantName()).isEqualTo("Tempus AI");
        assertThat(event.deviceName()).isEqualTo("AI Lung Scanner");
        assertThat(event.linkedArticleId()).isEqualTo("article-123");
        assertThat(event.aiHealthcareKeywords()).containsExactly("radiology", "lung", "AI");
    }

    // --- Helper ---

    private RegulatoryEvent event(String eventId, String title) {
        return new RegulatoryEvent(eventId, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, title, null, null, null, null,
                "https://fda.gov/" + eventId, null, null, NOW, null);
    }
}
