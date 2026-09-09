package com.wgblackmon.aihealthcare.infrastructure.enterprise;

import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLogPort;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filesystem-backed adapter for per-job log files.
 *
 * <p>Each job gets a plain-text log file named {@code {jobId}.log}.
 * Lines follow the format:
 * {@code {ISO-8601}  {LEVEL}  job={jobId}  phase={PHASE}  {detail}}.
 * All detail content passes through {@link LogSanitizer#maskEmail(String)}
 * as a second safety net.
 *
 * <p>This class is a plain POJO constructed by {@code EnterpriseDataConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
public class FileDataJobLogAdapter implements DataJobLogPort {

    private final ConfinedFileStore store;

    public FileDataJobLogAdapter(ConfinedFileStore store) {
        this.store = store;
        log.debug("FileDataJobLogAdapter() | baseDir={}", store.getBaseDirectory());
    }

    @Override
    public DataJobLog open(String jobId) {
        log.debug("open() | jobId={}", jobId);
        DataJobLog result = new FileDataJobLog(jobId, store);
        log.debug("open() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Override
    public String read(String jobId, long fromByteOffset) {
        log.debug("read() | jobId={}, fromByteOffset={}", jobId, fromByteOffset);
        String fileName = logFileName(jobId);
        try {
            if (!store.exists(fileName)) {
                log.debug("read() | return= (file not found)");
                return "";
            }
            byte[] allBytes = store.readBytes(fileName);
            if (fromByteOffset >= allBytes.length) {
                log.debug("read() | return= (no new bytes)");
                return "";
            }
            int offset = (int) Math.max(0, fromByteOffset);
            String result = new String(allBytes, offset, allBytes.length - offset,
                    StandardCharsets.UTF_8);
            log.debug("read() | return={} bytes", result.length());
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read log: " + fileName, e);
        }
    }

    @Override
    public void delete(String jobId) {
        log.debug("delete() | jobId={}", jobId);
        try {
            store.delete(logFileName(jobId));
        } catch (IOException e) {
            log.warn("delete() | failed to delete log for jobId={}: {}", jobId, e.getMessage());
        }
        log.debug("delete() | return=void");
    }

    static String logFileName(String jobId) {
        return jobId + ".log";
    }

    /**
     * Per-job log appender. Writes structured lines and tracks byte offset
     * for tailing by the HTMX console pane.
     */
    static class FileDataJobLog implements DataJobLog {

        private static final Pattern EMAIL_PATTERN =
                Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

        private final String jobId;
        private final ConfinedFileStore store;
        private final String fileName;

        FileDataJobLog(String jobId, ConfinedFileStore store) {
            this.jobId = jobId;
            this.store = store;
            this.fileName = logFileName(jobId);
        }

        @Override
        public void phase(String phase, String detailKeyValuePairs) {
            writeLine("INFO", phase, sanitise(detailKeyValuePairs));
        }

        @Override
        public void warn(String phase, String detail) {
            writeLine("WARN", phase, sanitise(detail));
        }

        @Override
        public void error(String phase, String detail, Throwable cause) {
            String causeMsg = cause != null
                    ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "";
            writeLine("ERROR", phase, sanitise(detail) + "  " + causeMsg);
        }

        @Override
        public long byteOffset() {
            try {
                return store.size(fileName);
            } catch (IOException e) {
                return 0;
            }
        }

        private void writeLine(String level, String phase, String detail) {
            String line = String.format("%s  %s  job=%s  phase=%s  %s",
                    Instant.now(), level, jobId, phase, detail);
            try {
                store.append(fileName, line);
            } catch (IOException e) {
                log.error("Failed to append to job log: jobId={}, phase={}", jobId, phase, e);
            }
        }

        private String sanitise(String input) {
            if (input == null) {
                return "";
            }
            Matcher matcher = EMAIL_PATTERN.matcher(input);
            return matcher.replaceAll(m -> LogSanitizer.maskEmail(m.group()));
        }
    }
}
