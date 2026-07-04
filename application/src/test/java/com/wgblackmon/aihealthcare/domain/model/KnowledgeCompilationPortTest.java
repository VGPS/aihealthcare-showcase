package com.wgblackmon.aihealthcare.domain.model;

import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test demonstrating that a caller can consume a
 * {@link KnowledgeCompilationPort} via a mock and read the
 * resulting {@link CompilationReport} fields.
 *
 * <p>No real AI or persistence calls — validates the port contract
 * can be exercised through Mockito alone.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class KnowledgeCompilationPortTest {

    @Test
    void mockedPort_returnsFixedReport_callerReadsFields() {
        // Arrange — build a fixed CompilationReport with one contradiction
        Contradiction contradiction = new Contradiction(
                "fda-ai-guidance",
                "All AI devices require premarket review",
                "Low-risk AI devices exempt from review",
                List.of(new SourceRef("a-001", "FDA", LocalDate.of(2026, 3, 15), null)),
                List.of(new SourceRef("a-042", "STAT", LocalDate.of(2026, 7, 1), null)),
                Instant.parse("2026-07-04T04:03:00Z"));

        CompilationReport fixedReport = new CompilationReport(
                Instant.parse("2026-07-04T04:00:00Z"),
                Instant.parse("2026-07-04T04:05:00Z"),
                3,
                List.of("fda-ai-guidance"),
                List.of("google-medpalm"),
                List.of(contradiction),
                List.of());

        KnowledgeCompilationPort port = mock(KnowledgeCompilationPort.class);

        List<NewsArticle> articles = List.of(
                new NewsArticle("a-001", "FDA Issues Guidance", URI.create("https://fda.gov/1"),
                        "body", "FDA News", null, null, "FDA", "REGULATORY", 0.8, null),
                new NewsArticle("a-042", "Low-Risk Exemption", URI.create("https://stat.com/2"),
                        "body", "STAT News", null, null, "STAT", "INDUSTRY", 0.5, null),
                new NewsArticle("a-100", "MedPaLM Update", URI.create("https://google.com/3"),
                        "body", "Google Health", null, null, "Google", "INDUSTRY", 0.5, null));

        when(port.compileNewSources(articles)).thenReturn(fixedReport);

        // Act
        CompilationReport result = port.compileNewSources(articles);

        // Assert — caller can read pagesCreated and contradictionsFlagged
        assertThat(result.pagesCreated()).containsExactly("fda-ai-guidance");
        assertThat(result.pagesUpdated()).containsExactly("google-medpalm");
        assertThat(result.contradictionsFlagged()).hasSize(1);
        assertThat(result.contradictionsFlagged().get(0).pageSlug()).isEqualTo("fda-ai-guidance");
        assertThat(result.contradictionsFlagged().get(0).priorClaim())
                .isEqualTo("All AI devices require premarket review");
        assertThat(result.contradictionsFlagged().get(0).newClaim())
                .isEqualTo("Low-risk AI devices exempt from review");
        assertThat(result.articlesProcessed()).isEqualTo(3);
    }
}
