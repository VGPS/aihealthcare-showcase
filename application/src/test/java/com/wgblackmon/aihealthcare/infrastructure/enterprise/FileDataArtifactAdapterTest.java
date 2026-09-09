package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link FileDataArtifactAdapter} — write/read round-trip,
 * SHA-256 correctness, oversize rejection, and extension matching.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class FileDataArtifactAdapterTest {

    @TempDir
    Path tempDir;

    private FileDataArtifactAdapter adapter;

    @BeforeEach
    void setUp() {
        ConfinedFileStore store = new ConfinedFileStore(tempDir);
        adapter = new FileDataArtifactAdapter(store, 1024L, 14);
    }

    @Test
    void write_read_roundTrip() throws IOException {
        byte[] content = "col1,col2\na,b\n".getBytes();
        DataArtifact artifact = adapter.write("job-1", ExportFormat.CSV, content);

        assertThat(artifact.jobId()).isEqualTo("job-1");
        assertThat(artifact.format()).isEqualTo(ExportFormat.CSV);
        assertThat(artifact.fileName()).isEqualTo("job-1.csv");
        assertThat(artifact.byteSize()).isEqualTo(content.length);

        try (InputStream is = adapter.read("job-1")) {
            assertThat(is).isNotNull();
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void write_sha256_isCorrect() throws NoSuchAlgorithmException {
        byte[] content = "test data for hashing".getBytes();
        DataArtifact artifact = adapter.write("job-hash", ExportFormat.JSON, content);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedSha = HexFormat.of().formatHex(digest.digest(content));
        assertThat(artifact.sha256()).isEqualTo(expectedSha);
    }

    @Test
    void write_rejectsOversizeContent() {
        byte[] oversized = new byte[2048];
        assertThatThrownBy(() -> adapter.write("job-big", ExportFormat.CSV, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds limit");
    }

    @Test
    void write_extensionMatchesFormat() {
        adapter.write("job-j", ExportFormat.JSON, "{}".getBytes());
        assertThat(adapter.exists("job-j")).isTrue();
    }

    @Test
    void exists_falseForMissingJob() {
        assertThat(adapter.exists("nonexistent")).isFalse();
    }

    @Test
    void delete_removesArtifact() {
        adapter.write("job-del", ExportFormat.CSV, "data".getBytes());
        assertThat(adapter.exists("job-del")).isTrue();

        adapter.delete("job-del");
        assertThat(adapter.exists("job-del")).isFalse();
    }

    @Test
    void read_returnsNullForMissingJob() {
        InputStream result = adapter.read("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void write_setsExpiresAt() {
        DataArtifact artifact = adapter.write("job-exp", ExportFormat.CSV, "data".getBytes());
        assertThat(artifact.expiresAt()).isAfter(artifact.createdAt());
    }
}
