package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SourceCheckResult}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
class SourceCheckResultTest {

    @Test
    void validResult_allFieldsAccessible() {
        Instant now = Instant.now();
        SourceCheckResult result = new SourceCheckResult(200, "abc123", true, now, null);

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentHash()).isEqualTo("abc123");
        assertThat(result.changed()).isTrue();
        assertThat(result.fetchedAt()).isEqualTo(now);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void errorResult_capturesMessage() {
        Instant now = Instant.now();
        SourceCheckResult result = new SourceCheckResult(0, null, true, now, "Connection refused");

        assertThat(result.httpStatus()).isZero();
        assertThat(result.contentHash()).isNull();
        assertThat(result.changed()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void nullFetchedAt_throwsException() {
        assertThatThrownBy(() -> new SourceCheckResult(200, "abc", false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fetchedAt");
    }

    @Test
    void unchangedResult_changedIsFalse() {
        Instant now = Instant.now();
        SourceCheckResult result = new SourceCheckResult(200, "same-hash", false, now, null);

        assertThat(result.changed()).isFalse();
    }
}
