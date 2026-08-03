package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.wgblackmon.aihealthcare.domain.model.SourceCitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IntelReportAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@DataJpaTest
@Import(IntelReportAdapter.class)
class IntelReportAdapterTest {

    @Autowired
    private IntelReportAdapter adapter;

    @Test
    void saveAndFindById() {
        IntelReport report = report("rpt-001", "Anthropic healthcare", 5);
        adapter.save(report);

        Optional<IntelReport> result = adapter.findById("rpt-001");

        assertThat(result).isPresent();
        assertThat(result.get().query()).isEqualTo("Anthropic healthcare");
        assertThat(result.get().sourceCount()).isEqualTo(5);
        assertThat(result.get().userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void findById_returnsEmptyForMissing() {
        Optional<IntelReport> result = adapter.findById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsOrderedByGeneratedAtDesc() {
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-08-01T00:00:00Z");

        adapter.save(new IntelReport("rpt-old", "old query", "<p>old</p>", 3, "u@t.com", older, List.of()));
        adapter.save(new IntelReport("rpt-new", "new query", "<p>new</p>", 7, "u@t.com", newer, List.of()));

        List<IntelReport> result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).reportId()).isEqualTo("rpt-new");
        assertThat(result.get(1).reportId()).isEqualTo("rpt-old");
    }

    @Test
    void findAll_returnsEmptyWhenNoReports() {
        List<IntelReport> result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void saveAndFindById_withSources_roundTrips() {
        List<SourceCitation> sources = List.of(
                new SourceCitation(1, "AI Article", "https://example.com/1", Instant.parse("2026-06-01T12:00:00Z")),
                new SourceCitation(2, "ML Paper", "https://example.com/2", null));

        IntelReport report = new IntelReport("rpt-src", "sources test", "<p>content</p>",
                2, "user@test.com", Instant.now(), sources);
        adapter.save(report);

        Optional<IntelReport> result = adapter.findById("rpt-src");

        assertThat(result).isPresent();
        assertThat(result.get().sources()).hasSize(2);
        assertThat(result.get().sources().get(0).title()).isEqualTo("AI Article");
        assertThat(result.get().sources().get(1).citationNumber()).isEqualTo(2);
    }

    private IntelReport report(String id, String query, int sourceCount) {
        return new IntelReport(id, query, "<h2>Summary</h2><p>Content</p>",
                sourceCount, "user@test.com", Instant.now(), List.of());
    }
}
