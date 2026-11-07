package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiBundleExportResult;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OkfBundleExporterAdapter}.
 *
 * <p>All tests use a JUnit {@code @TempDir} as the bundle output directory
 * so the filesystem is cleaned up automatically — no production paths are written.
 * No Spring context, no database, no AI calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-07
 * @updated 2026-11-07
 */
@ExtendWith(MockitoExtension.class)
class OkfBundleExporterAdapterTest {

    @Mock
    private WikiQueryPort wikiQueryPort;

    @TempDir
    Path tempDir;

    private OkfBundleExporterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OkfBundleExporterAdapter(wikiQueryPort, tempDir.toString());
    }

    @Test
    void exportBundle_writesOneFilePerPage() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of(
                entityPage("fda-ai-guidance", "FDA AI Device Guidance",
                        "FDA issued guidance on AI-powered medical devices.", List.of("fda", "ai-devices"))
        ));
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        WikiBundleExportResult result = adapter.exportBundle();

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.contradictionCount()).isEqualTo(0);

        Path entityDir = tempDir.resolve("ENTITY");
        assertThat(entityDir).isDirectory();
        assertThat(entityDir.resolve("fda-ai-guidance.md")).exists();
    }

    @Test
    void exportBundle_frontmatterContainsRequiredOkfFields() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of(
                entityPage("epic-ehr-ai", "Epic EHR AI Integration",
                        "Epic has integrated AI tools into their EHR platform.", List.of("epic", "ehr"))
        ));
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        adapter.exportBundle();

        String content = Files.readString(tempDir.resolve("ENTITY/epic-ehr-ai.md"));
        assertThat(content).contains("type: AI Healthcare Wiki Page");
        assertThat(content).contains("title:");
        assertThat(content).contains("description:");
        assertThat(content).contains("resource: https://bigskylabs.ai/wiki/epic-ehr-ai");
        assertThat(content).contains("tags:");
        assertThat(content).contains("generated:");
        assertThat(content).contains("status:");
        assertThat(content).contains("stale_after:");
        assertThat(content).contains("revision:");
    }

    @Test
    void exportBundle_groupsByPageType() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of(
                entityPage("company-a", "Company A", "Company A description.", List.of()),
                conceptPage("machine-learning", "Machine Learning", "ML in healthcare.", List.of())
        ));
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        WikiBundleExportResult result = adapter.exportBundle();

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(tempDir.resolve("ENTITY/company-a.md")).exists();
        assertThat(tempDir.resolve("CONCEPT/machine-learning.md")).exists();
    }

    @Test
    void exportBundle_generatesIndexFilesAtEachLevel() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of(
                entityPage("vendor-x", "Vendor X", "Healthcare AI vendor.", List.of("vendor"))
        ));
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        adapter.exportBundle();

        assertThat(tempDir.resolve("index.md")).exists();
        assertThat(tempDir.resolve("ENTITY/index.md")).exists();
    }

    @Test
    void exportBundle_writesContradictionFiles() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of());
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of(
                contradiction("fda-ai-guidance", "Prior: FDA approved X", "New: FDA revoked X approval")
        ));

        WikiBundleExportResult result = adapter.exportBundle();

        assertThat(result.contradictionCount()).isEqualTo(1);
        assertThat(tempDir.resolve("CONTRADICTIONS")).isDirectory();
        assertThat(tempDir.resolve("CONTRADICTIONS/index.md")).exists();

        Path[] contradictionFiles = Files.list(tempDir.resolve("CONTRADICTIONS"))
                .filter(p -> p.getFileName().toString().startsWith("fda-ai-guidance"))
                .toArray(Path[]::new);
        assertThat(contradictionFiles).hasSize(1);
    }

    @Test
    void exportBundle_contradictionFileContainsBothClaims() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of());
        Instant detectedAt = Instant.parse("2026-11-01T10:00:00Z");
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of(
                new Contradiction("cms-rule-change", "Prior claim text here", "New claim text here",
                        List.of(), List.of(), detectedAt)
        ));

        adapter.exportBundle();

        String content = Files.readString(
                tempDir.resolve("CONTRADICTIONS/cms-rule-change-" + detectedAt.getEpochSecond() + ".md"));
        assertThat(content).contains("Prior claim text here");
        assertThat(content).contains("New claim text here");
        assertThat(content).contains("type: Contradiction");
    }

    @Test
    void exportBundle_emptyWikiProducesRootIndexOnly() throws IOException {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of());
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        WikiBundleExportResult result = adapter.exportBundle();

        assertThat(result.pageCount()).isEqualTo(0);
        assertThat(result.contradictionCount()).isEqualTo(0);
        assertThat(tempDir.resolve("index.md")).exists();
    }

    @Test
    void exportBundle_pageWithSourcesIncludesSourcesInFrontmatter() throws IOException {
        SourceRef src = new SourceRef("article-1", "FDA.gov", LocalDate.of(2026, 10, 1), "AI device excerpt");
        WikiPage page = new WikiPage("fda-clearance", "FDA Clearance", WikiPageType.ENTITY,
                List.of("fda"), "FDA cleared an AI diagnostic device.",
                List.of(src), List.of(), Instant.now(), null, 1);

        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of(page));
        when(wikiQueryPort.recentContradictions(Instant.EPOCH)).thenReturn(List.of());

        adapter.exportBundle();

        String content = Files.readString(tempDir.resolve("ENTITY/fda-clearance.md"));
        assertThat(content).contains("sources:");
        assertThat(content).contains("FDA.gov");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WikiPage entityPage(String slug, String title, String content, List<String> tags) {
        return new WikiPage(slug, title, WikiPageType.ENTITY, tags, content,
                List.of(), List.of(), Instant.now(), null, 1);
    }

    private WikiPage conceptPage(String slug, String title, String content, List<String> tags) {
        return new WikiPage(slug, title, WikiPageType.CONCEPT, tags, content,
                List.of(), List.of(), Instant.now(), null, 1);
    }

    private Contradiction contradiction(String pageSlug, String prior, String newClaim) {
        return new Contradiction(pageSlug, prior, newClaim, List.of(), List.of(), Instant.now());
    }
}
