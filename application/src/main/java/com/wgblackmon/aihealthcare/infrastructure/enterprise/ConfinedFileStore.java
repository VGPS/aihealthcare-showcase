package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Filesystem boundary — all enterprise data I/O is confined to a single
 * directory tree. No method on this class accepts a {@link Path} or allows
 * a customer-supplied string to influence the resolved file path.
 *
 * <p><b>Security invariants:</b>
 * <ul>
 *   <li>File names must match {@code ^[A-Za-z0-9._-]{1,128}$}.</li>
 *   <li>The resolved path must be a child of the base directory after
 *       canonicalisation — path traversal is rejected.</li>
 *   <li>Symbolic links are rejected (checked via {@code NOFOLLOW_LINKS}).</li>
 *   <li>Null bytes are rejected before any filesystem call.</li>
 * </ul>
 *
 * <p>This class is intentionally <b>not</b> a Spring bean. Instances are
 * created by {@code EnterpriseDataConfig} and injected into the adapters
 * that need them.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public class ConfinedFileStore {

    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final Path baseDirectory;

    public ConfinedFileStore(Path baseDirectory) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("baseDirectory must not be null");
        }
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    public void write(String name, byte[] content) throws IOException {
        Path resolved = resolve(name);
        Files.write(resolved, content);
    }

    public byte[] readBytes(String name) throws IOException {
        Path resolved = resolve(name);
        return Files.readAllBytes(resolved);
    }

    public InputStream readStream(String name) throws IOException {
        Path resolved = resolve(name);
        return Files.newInputStream(resolved);
    }

    public void append(String name, String line) throws IOException {
        Path resolved = resolve(name);
        Files.writeString(resolved, line + "\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    public long size(String name) throws IOException {
        Path resolved = resolve(name);
        if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        return Files.size(resolved);
    }

    public boolean exists(String name) {
        try {
            Path resolved = resolve(name);
            return Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(resolved);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void delete(String name) throws IOException {
        Path resolved = resolve(name);
        Files.deleteIfExists(resolved);
    }

    Path resolve(String name) {
        validateName(name);
        Path candidate = baseDirectory.resolve(name).normalize();
        if (!candidate.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Path traversal rejected: " + name);
        }
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(candidate)) {
            throw new IllegalArgumentException("Symbolic link rejected: " + name);
        }
        return candidate;
    }

    private void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("File name must not be null or empty");
        }
        if (name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Null byte in file name rejected");
        }
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("File name does not match safe pattern: " + name);
        }
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}
