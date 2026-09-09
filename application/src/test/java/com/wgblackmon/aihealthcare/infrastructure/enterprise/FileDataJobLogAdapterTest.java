package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileDataJobLogAdapter} — structured line format,
 * tailing from offset, missing file handling, and email masking.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class FileDataJobLogAdapterTest {

    @TempDir
    Path tempDir;

    private FileDataJobLogAdapter adapter;

    @BeforeEach
    void setUp() {
        ConfinedFileStore store = new ConfinedFileStore(tempDir);
        adapter = new FileDataJobLogAdapter(store);
    }

    @Test
    void phase_writesStructuredLine() {
        DataJobLog log = adapter.open("job-1");
        log.phase("FETCH_START", "rows=42");

        String content = adapter.read("job-1", 0);
        assertThat(content).contains("INFO");
        assertThat(content).contains("job=job-1");
        assertThat(content).contains("phase=FETCH_START");
        assertThat(content).contains("rows=42");
    }

    @Test
    void warn_writesWarnLevel() {
        DataJobLog log = adapter.open("job-w");
        log.warn("RENDER", "slow query detected");

        String content = adapter.read("job-w", 0);
        assertThat(content).contains("WARN");
        assertThat(content).contains("phase=RENDER");
        assertThat(content).contains("slow query detected");
    }

    @Test
    void error_writesErrorLevelWithException() {
        DataJobLog log = adapter.open("job-e");
        log.error("FETCH", "connection failed", new RuntimeException("timeout"));

        String content = adapter.read("job-e", 0);
        assertThat(content).contains("ERROR");
        assertThat(content).contains("phase=FETCH");
        assertThat(content).contains("connection failed");
        assertThat(content).contains("RuntimeException: timeout");
    }

    @Test
    void read_fromOffset_returnsOnlyNewBytes() {
        DataJobLog log = adapter.open("job-tail");
        log.phase("STEP_1", "first line");
        long offsetAfterFirst = log.byteOffset();

        log.phase("STEP_2", "second line");

        String tail = adapter.read("job-tail", offsetAfterFirst);
        assertThat(tail).doesNotContain("STEP_1");
        assertThat(tail).contains("STEP_2");
    }

    @Test
    void read_missingFile_returnsEmptyString() {
        String result = adapter.read("nonexistent", 0);
        assertThat(result).isEmpty();
    }

    @Test
    void read_offsetBeyondFileSize_returnsEmptyString() {
        DataJobLog log = adapter.open("job-short");
        log.phase("ONLY", "one line");

        String result = adapter.read("job-short", 999999);
        assertThat(result).isEmpty();
    }

    @Test
    void emailInDetail_isMasked() {
        DataJobLog log = adapter.open("job-mask");
        log.phase("FETCH", "owner=alice@example.com fetched 42 rows");

        String content = adapter.read("job-mask", 0);
        assertThat(content).doesNotContain("alice@example.com");
        assertThat(content).contains("al***@e***.com");
    }

    @Test
    void delete_removesLogFile() {
        DataJobLog log = adapter.open("job-del");
        log.phase("INIT", "created");
        assertThat(adapter.read("job-del", 0)).isNotEmpty();

        adapter.delete("job-del");
        assertThat(adapter.read("job-del", 0)).isEmpty();
    }

    @Test
    void byteOffset_tracksFileGrowth() {
        DataJobLog log = adapter.open("job-offset");
        assertThat(log.byteOffset()).isZero();

        log.phase("STEP_1", "data");
        long afterFirst = log.byteOffset();
        assertThat(afterFirst).isGreaterThan(0);

        log.phase("STEP_2", "more data");
        assertThat(log.byteOffset()).isGreaterThan(afterFirst);
    }
}
