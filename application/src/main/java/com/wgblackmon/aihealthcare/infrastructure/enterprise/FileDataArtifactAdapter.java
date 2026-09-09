package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Filesystem-backed adapter for enterprise data artifacts.
 *
 * <p>Delegates all I/O to a {@link ConfinedFileStore} scoped to the
 * artifact directory. File names are composed as {@code {jobId}.{ext}} —
 * no customer-supplied string reaches the filesystem.
 *
 * <p>This class is a plain POJO constructed by {@code EnterpriseDataConfig},
 * not a Spring {@code @Component}, because the {@code ConfinedFileStore}
 * instance and {@code maxArtifactBytes} value must be injected explicitly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
public class FileDataArtifactAdapter implements DataArtifactPort {

    private final ConfinedFileStore store;
    private final long maxArtifactBytes;
    private final int retentionDays;

    public FileDataArtifactAdapter(ConfinedFileStore store, long maxArtifactBytes,
                                   int retentionDays) {
        this.store = store;
        this.maxArtifactBytes = maxArtifactBytes;
        this.retentionDays = retentionDays;
        log.debug("FileDataArtifactAdapter() | baseDir={}, maxBytes={}, retentionDays={}",
                store.getBaseDirectory(), maxArtifactBytes, retentionDays);
    }

    @Override
    public DataArtifact write(String jobId, ExportFormat format, byte[] content) {
        log.debug("write() | jobId={}, format={}, contentLength={}", jobId, format, content.length);
        if (content.length > maxArtifactBytes) {
            throw new IllegalArgumentException(
                    "Artifact size " + content.length + " exceeds limit " + maxArtifactBytes);
        }
        String fileName = fileName(jobId, format);
        try {
            store.write(fileName, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write artifact: " + fileName, e);
        }
        String sha256 = sha256Hex(content);
        Instant now = Instant.now();
        DataArtifact result = new DataArtifact(
                jobId, format, fileName, content.length, sha256,
                now, now.plus(retentionDays, ChronoUnit.DAYS));
        log.debug("write() | return={}", result);
        return result;
    }

    @Override
    public InputStream read(String jobId) {
        log.debug("read() | jobId={}", jobId);
        String fileName = findExistingFile(jobId);
        if (fileName == null) {
            log.debug("read() | return=null (not found)");
            return null;
        }
        try {
            InputStream result = store.readStream(fileName);
            log.debug("read() | return=InputStream");
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read artifact: " + fileName, e);
        }
    }

    @Override
    public void delete(String jobId) {
        log.debug("delete() | jobId={}", jobId);
        for (ExportFormat fmt : ExportFormat.values()) {
            String fn = fileName(jobId, fmt);
            try {
                store.delete(fn);
            } catch (IOException e) {
                log.warn("delete() | failed to delete {}: {}", fn, e.getMessage());
            }
        }
        log.debug("delete() | return=void");
    }

    @Override
    public boolean exists(String jobId) {
        log.debug("exists() | jobId={}", jobId);
        boolean result = findExistingFile(jobId) != null;
        log.debug("exists() | return={}", result);
        return result;
    }

    private String findExistingFile(String jobId) {
        for (ExportFormat fmt : ExportFormat.values()) {
            String fn = fileName(jobId, fmt);
            if (store.exists(fn)) {
                return fn;
            }
        }
        return null;
    }

    private String fileName(String jobId, ExportFormat format) {
        return jobId + "." + format.name().toLowerCase();
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
