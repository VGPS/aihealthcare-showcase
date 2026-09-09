package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for {@link ConfinedFileStore}.
 *
 * <p>This is the most important test class in the enterprise data feature.
 * Every rejected name must leave <b>zero files</b> anywhere on disk.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class ConfinedFileStoreTest {

    @TempDir
    Path tempDir;

    private ConfinedFileStore store;

    @BeforeEach
    void setUp() {
        store = new ConfinedFileStore(tempDir);
    }

    // ---- accepted names ----

    @Test
    void write_read_roundTrip() throws IOException {
        byte[] content = "hello world".getBytes();
        store.write("a1b2c3d4.csv", content);
        assertThat(store.readBytes("a1b2c3d4.csv")).isEqualTo(content);
    }

    @Test
    void exists_returnsTrueForExistingFile() throws IOException {
        store.write("test.json", new byte[]{1, 2, 3});
        assertThat(store.exists("test.json")).isTrue();
    }

    @Test
    void exists_returnsFalseForMissing() {
        assertThat(store.exists("nope.csv")).isFalse();
    }

    @Test
    void delete_removesFile() throws IOException {
        store.write("doomed.csv", "data".getBytes());
        store.delete("doomed.csv");
        assertThat(store.exists("doomed.csv")).isFalse();
    }

    @Test
    void size_returnsFileSize() throws IOException {
        byte[] data = "twelve chars".getBytes();
        store.write("sized.txt", data);
        assertThat(store.size("sized.txt")).isEqualTo(data.length);
    }

    @Test
    void size_returnsZeroForMissingFile() throws IOException {
        assertThat(store.size("missing.txt")).isEqualTo(0);
    }

    @Test
    void append_createsAndAppendsToFile() throws IOException {
        store.append("log.txt", "line1");
        store.append("log.txt", "line2");
        String content = new String(store.readBytes("log.txt"));
        assertThat(content).contains("line1").contains("line2");
    }

    // ---- rejected names: path traversal ----

    @ParameterizedTest
    @ValueSource(strings = {
            "../secrets.txt",
            "..\\..\\windows\\system32\\x",
            "/etc/passwd",
            "C:\\Windows\\x",
            "~/.aws/credentials",
            "foo/../bar.csv",
            "..\\bar.csv"
    })
    void rejects_pathTraversal(String maliciousName) {
        assertThatThrownBy(() -> store.write(maliciousName, "pwned".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    // ---- rejected names: invalid characters ----

    @Test
    void rejects_nullByte() {
        assertThatThrownBy(() -> store.write("evil\0.csv", "pwned".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    @Test
    void rejects_emptyName() {
        assertThatThrownBy(() -> store.write("", "data".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    @Test
    void rejects_nullName() {
        assertThatThrownBy(() -> store.write(null, "data".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    @Test
    void rejects_tooLongName() {
        String longName = "a".repeat(200) + ".csv";
        assertThatThrownBy(() -> store.write(longName, "data".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    @Test
    void rejects_spacesInName() {
        assertThatThrownBy(() -> store.write("bad file.csv", "data".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertNoFilesCreated();
    }

    // ---- rejected: symbolic links ----

    @Test
    void rejects_symbolicLink() throws IOException {
        Path outsideDir = tempDir.resolve("outside");
        Files.createDirectories(outsideDir);
        Path secretFile = outsideDir.resolve("secret.txt");
        Files.writeString(secretFile, "sensitive data");

        Path linkTarget = tempDir.resolve("link.txt");
        try {
            Files.createSymbolicLink(linkTarget, secretFile);
        } catch (IOException | UnsupportedOperationException e) {
            return;
        }

        assertThatThrownBy(() -> store.readBytes("link.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbolic link");
    }

    // ---- rejected: constructor validation ----

    @Test
    void rejects_nullBaseDirectory() {
        assertThatThrownBy(() -> new ConfinedFileStore(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helper ----

    private void assertNoFilesCreated() {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            long fileCount = walk.filter(Files::isRegularFile).count();
            assertThat(fileCount).as("No files should be created after a rejected name").isZero();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
