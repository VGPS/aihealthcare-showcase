package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RegulatoryEventAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@DataJpaTest
@Import(RegulatoryEventAdapter.class)
class RegulatoryEventAdapterTest {

    @Autowired
    private RegulatoryEventAdapter adapter;

    @Test
    void saveAndFindById() {
        RegulatoryEvent event = event("e1", "K241234", "https://fda.gov/e1");
        adapter.save(event);

        Optional<RegulatoryEvent> result = adapter.findById("e1");

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("510(k) Clearance");
        assertThat(result.get().referenceNumber()).isEqualTo("K241234");
        assertThat(result.get().eventType()).isEqualTo(RegulatoryEventType.FDA_510K_CLEARANCE);
    }

    @Test
    void existsByReferenceNumber() {
        adapter.save(event("e1", "K241234", "https://fda.gov/e1"));

        assertThat(adapter.existsByReferenceNumber("K241234")).isTrue();
        assertThat(adapter.existsByReferenceNumber("K999999")).isFalse();
    }

    @Test
    void existsBySourceUrl() {
        adapter.save(event("e1", "K241234", "https://fda.gov/e1"));

        assertThat(adapter.existsBySourceUrl("https://fda.gov/e1")).isTrue();
        assertThat(adapter.existsBySourceUrl("https://fda.gov/missing")).isFalse();
    }

    @Test
    void findRecentReturnsLimited() {
        adapter.save(event("e1", "K001", "https://fda.gov/e1"));
        adapter.save(event("e2", "K002", "https://fda.gov/e2"));
        adapter.save(event("e3", "K003", "https://fda.gov/e3"));

        List<RegulatoryEvent> result = adapter.findRecent(2);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByTypeFilters() {
        adapter.save(event("e1", "K001", "https://fda.gov/e1"));
        adapter.save(cmsEvent("e2", "https://federalregister.gov/e2"));

        List<RegulatoryEvent> fdaEvents = adapter.findByType(RegulatoryEventType.FDA_510K_CLEARANCE, 10);
        List<RegulatoryEvent> cmsEvents = adapter.findByType(RegulatoryEventType.CMS_PROPOSED_RULE, 10);

        assertThat(fdaEvents).hasSize(1);
        assertThat(cmsEvents).hasSize(1);
    }

    @Test
    void findByBodyFilters() {
        adapter.save(event("e1", "K001", "https://fda.gov/e1"));
        adapter.save(cmsEvent("e2", "https://federalregister.gov/e2"));

        List<RegulatoryEvent> fdaEvents = adapter.findByBody(RegulatoryBody.FDA, 10);
        List<RegulatoryEvent> cmsEvents = adapter.findByBody(RegulatoryBody.CMS, 10);

        assertThat(fdaEvents).hasSize(1);
        assertThat(cmsEvents).hasSize(1);
    }

    @Test
    void saveAllBatchPersists() {
        List<RegulatoryEvent> events = List.of(
                event("e1", "K001", "https://fda.gov/e1"),
                event("e2", "K002", "https://fda.gov/e2")
        );
        adapter.saveAll(events);

        assertThat(adapter.findRecent(10)).hasSize(2);
    }

    @Test
    void keywordsRoundTrip() {
        RegulatoryEvent event = new RegulatoryEvent("e1", RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "AI Device", "Summary", "K241234",
                "Applicant", "Device", "https://fda.gov/e1", null,
                Instant.now(), Instant.now(), List.of("AI", "radiology", "deep learning"),
                null, null, null, null);
        adapter.save(event);

        Optional<RegulatoryEvent> result = adapter.findById("e1");
        assertThat(result).isPresent();
        assertThat(result.get().aiHealthcareKeywords()).containsExactly("AI", "radiology", "deep learning");
    }

    // --- Helpers ---

    private RegulatoryEvent event(String id, String refNumber, String sourceUrl) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, "510(k) Clearance",
                "Summary", refNumber, "Applicant Inc", "AI Device",
                sourceUrl, null, Instant.now(), Instant.now(),
                List.of("AI", "radiology"),
                null, null, null, null);
    }

    private RegulatoryEvent cmsEvent(String id, String sourceUrl) {
        return new RegulatoryEvent(id, RegulatoryEventType.CMS_PROPOSED_RULE,
                RegulatoryBody.CMS, "CMS Proposed Rule",
                "Abstract", null, null, null,
                sourceUrl, null, Instant.now(), Instant.now(),
                List.of("clinical decision support"),
                null, null, null, null);
    }
}
