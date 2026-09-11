package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DataColumn;
import com.wgblackmon.aihealthcare.domain.model.DataSet;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataSetRenderer}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-11
 */
class DataSetRendererTest {

    @Test
    void csvRoundTrip() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("id", "ID", "STRING"),
                        new DataColumn("name", "Name", "STRING")),
                List.of(List.of("1", "Alice"), List.of("2", "Bob")),
                null, List.of(), List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("ID,Name");
        assertThat(text).contains("1,Alice");
        assertThat(text).contains("2,Bob");
    }

    @Test
    void jsonRoundTrip() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("id", "ID", "STRING")),
                List.of(List.of("42")),
                null, List.of(), List.of(), 0);

        byte[] json = DataSetRenderer.render(ds, ExportFormat.JSON);
        String text = new String(json, StandardCharsets.UTF_8);

        assertThat(text).contains("\"columns\":");
        assertThat(text).contains("\"rows\":[[\"42\"]]");
    }

    @Test
    void csvNullCellsBecomEmpty() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("")),
                null, List.of(), List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("A\n\n");
    }

    @Test
    void jsonNullCellsBecomEmpty() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("")),
                null, List.of(), List.of(), 0);

        byte[] json = DataSetRenderer.render(ds, ExportFormat.JSON);
        String text = new String(json, StandardCharsets.UTF_8);

        assertThat(text).contains("\"rows\":[[\"\"]]");
    }

    @Test
    void csvSpecialCharacterEscaping() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("val", "Value", "STRING")),
                List.of(List.of("has,comma"), List.of("has\"quote"), List.of("has\nnewline")),
                null, List.of(), List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("\"has,comma\"");
        assertThat(text).contains("\"has\"\"quote\"");
        assertThat(text).contains("\"has\nnewline\"");
    }

    @Test
    void jsonSpecialCharacterEscaping() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("val", "Value", "STRING")),
                List.of(List.of("has\"quote"), List.of("has\nnewline")),
                null, List.of(), List.of(), 0);

        byte[] json = DataSetRenderer.render(ds, ExportFormat.JSON);
        String text = new String(json, StandardCharsets.UTF_8);

        assertThat(text).contains("has\\\"quote");
        assertThat(text).contains("has\\nnewline");
    }

    @Test
    void csvNarrativeAppearsAfterData() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("1")),
                "This is a narrative\nWith two lines",
                List.of(), List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).startsWith("A\n1\n");
        assertThat(text).contains("# Analysis\n# This is a narrative\n# With two lines\n");
    }

    @Test
    void jsonNarrativeAppearsAsField() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("1")),
                "Summary text",
                List.of(), List.of(), 0);

        byte[] json = DataSetRenderer.render(ds, ExportFormat.JSON);
        String text = new String(json, StandardCharsets.UTF_8);

        assertThat(text).contains("\"narrative\":\"Summary text\"");
    }

    @Test
    void csvCitationsAppendedAtEnd() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("1")),
                null,
                List.of(new SourceCitation(1, "Source One", "https://example.com/1", Instant.now())),
                List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("# Sources");
        assertThat(text).contains("[1] Source One");
    }

    @Test
    void jsonCitationsIncluded() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("a", "A", "STRING")),
                List.of(List.of("1")),
                null,
                List.of(new SourceCitation(1, "Src", "https://example.com", Instant.now())),
                List.of(), 0);

        byte[] json = DataSetRenderer.render(ds, ExportFormat.JSON);
        String text = new String(json, StandardCharsets.UTF_8);

        assertThat(text).contains("\"citations\":");
        assertThat(text).contains("\"number\":1");
    }

    @Test
    void pdfFormatThrows() {
        DataSet ds = new DataSet(List.of(), List.of(), null, List.of(), List.of(), 0);
        assertThatThrownBy(() -> DataSetRenderer.render(ds, ExportFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void nullFormatThrows() {
        DataSet ds = new DataSet(List.of(), List.of(), null, List.of(), List.of(), 0);
        assertThatThrownBy(() -> DataSetRenderer.render(ds, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapeCsvHandlesNull() {
        assertThat(DataSetRenderer.escapeCsv(null)).isEmpty();
    }

    @Test
    void escapeJsonHandlesNull() {
        assertThat(DataSetRenderer.escapeJson(null)).isEmpty();
    }

    @Test
    void escapeHtmlHandlesSpecialChars() {
        assertThat(DataSetRenderer.escapeHtml("<script>&\"test\"</script>"))
                .isEqualTo("&lt;script&gt;&amp;&quot;test&quot;&lt;/script&gt;");
    }

    @Test
    void emptyDataSetRendersHeaderOnly() {
        DataSet ds = new DataSet(
                List.of(new DataColumn("id", "ID", "STRING")),
                List.of(), null, List.of(), List.of(), 0);

        byte[] csv = DataSetRenderer.render(ds, ExportFormat.CSV);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text.trim()).isEqualTo("ID");
    }
}
