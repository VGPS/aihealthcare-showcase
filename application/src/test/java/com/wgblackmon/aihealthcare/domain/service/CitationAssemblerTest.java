package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CitationAssembler}.
 *
 * <p>No mocks — pure Java logic only.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
class CitationAssemblerTest {

    private CitationAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new CitationAssembler();
    }

    // -------------------------------------------------------------------------
    // assemble() — empty / null input
    // -------------------------------------------------------------------------

    @Test
    void assemble_emptyList_returnsEmptyList() {
        List<SourceCitation> result = assembler.assemble(new ArrayList<>());
        assertThat(result).isEmpty();
    }

    @Test
    void assemble_nullList_returnsEmptyList() {
        List<SourceCitation> result = assembler.assemble(null);
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // assemble() — basic numbering
    // -------------------------------------------------------------------------

    @Test
    void assemble_singleSource_returnsSingleCitationNumberedOne() {
        List<RetrievedSource> sources = List.of(
                source("id-1", "Title A", "https://example.com/a"));

        List<SourceCitation> result = assembler.assemble(sources);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).citationNumber()).isEqualTo(1);
        assertThat(result.get(0).title()).isEqualTo("Title A");
        assertThat(result.get(0).url()).isEqualTo("https://example.com/a");
    }

    @Test
    void assemble_multipleSources_assignsSequentialNumbers() {
        List<RetrievedSource> sources = List.of(
                source("id-1", "Alpha", "https://example.com/a"),
                source("id-2", "Beta",  "https://example.com/b"),
                source("id-3", "Gamma", "https://example.com/c"));

        List<SourceCitation> result = assembler.assemble(sources);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).citationNumber()).isEqualTo(1);
        assertThat(result.get(1).citationNumber()).isEqualTo(2);
        assertThat(result.get(2).citationNumber()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // assemble() — deduplication
    // -------------------------------------------------------------------------

    @Test
    void assemble_duplicateUrls_keepsFirstOccurrenceOnly() {
        List<RetrievedSource> sources = List.of(
                source("id-1", "Original",   "https://example.com/same"),
                source("id-2", "Duplicate",  "https://example.com/same"));

        List<SourceCitation> result = assembler.assemble(sources);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Original");
    }

    @Test
    void assemble_mixedDuplicatesAndUnique_deduplicatesCorrectly() {
        List<RetrievedSource> sources = List.of(
                source("id-1", "Unique A",  "https://example.com/a"),
                source("id-2", "Duplicate", "https://example.com/b"),
                source("id-3", "Unique C",  "https://example.com/c"),
                source("id-4", "Duplicate", "https://example.com/b"));

        List<SourceCitation> result = assembler.assemble(sources);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(SourceCitation::url)
                .containsExactly(
                        "https://example.com/a",
                        "https://example.com/b",
                        "https://example.com/c");
    }

    @Test
    void assemble_blankUrl_deduplicatesBySourceId() {
        List<RetrievedSource> sources = List.of(
                source("same-id", "Title 1", ""),
                source("same-id", "Title 2", ""));

        List<SourceCitation> result = assembler.assemble(sources);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Title 1");
    }

    // -------------------------------------------------------------------------
    // assemble() — retrievedAt is preserved
    // -------------------------------------------------------------------------

    @Test
    void assemble_sourceWithRetrievedAt_preservesTimestamp() {
        Instant ts = Instant.parse("2026-01-15T10:00:00Z");
        RetrievedSource src = new RetrievedSource("id-1", "Title", "https://x.com", "", "GOOGLE", ts);

        List<SourceCitation> result = assembler.assemble(List.of(src));

        assertThat(result.get(0).retrievedAt()).isEqualTo(ts);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private RetrievedSource source(String id, String title, String url) {
        return new RetrievedSource(id, title, url, "excerpt", "GOOGLE", Instant.now());
    }
}
