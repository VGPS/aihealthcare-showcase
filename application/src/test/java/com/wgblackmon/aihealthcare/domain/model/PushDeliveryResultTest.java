package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PushDeliveryResult} construction and validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class PushDeliveryResultTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void successfulResult_constructsCorrectly() {
        PushDeliveryResult r = new PushDeliveryResult(
                "sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                List.of("a@b.com"), 1024L, NOW, true, null
        );
        assertThat(r.success()).isTrue();
        assertThat(r.errorMessage()).isNull();
        assertThat(r.mode()).isEqualTo(PushDeliveryMode.ATTACHMENT);
    }

    @Test
    void failedResult_carriesErrorMessage() {
        PushDeliveryResult r = new PushDeliveryResult(
                "sched-1", "job-1", PushDeliveryMode.SIGNED_LINK,
                List.of("a@b.com"), 0L, NOW, false, "SES rejected"
        );
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("SES rejected");
    }

    @Test
    void blankScheduleId_rejected() {
        assertThatThrownBy(() -> new PushDeliveryResult(
                "", "job-1", PushDeliveryMode.ATTACHMENT,
                List.of("a@b.com"), 1024L, NOW, true, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scheduleId");
    }

    @Test
    void blankJobId_rejected() {
        assertThatThrownBy(() -> new PushDeliveryResult(
                "sched-1", " ", PushDeliveryMode.ATTACHMENT,
                List.of("a@b.com"), 1024L, NOW, true, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("jobId");
    }

    @Test
    void nullMode_rejected() {
        assertThatThrownBy(() -> new PushDeliveryResult(
                "sched-1", "job-1", null,
                List.of("a@b.com"), 1024L, NOW, true, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("mode");
    }

    @Test
    void nullRecipients_rejected() {
        assertThatThrownBy(() -> new PushDeliveryResult(
                "sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                null, 1024L, NOW, true, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("recipients");
    }

    @Test
    void recipients_defensiveCopy() {
        var mutableList = new java.util.ArrayList<>(List.of("a@b.com"));
        PushDeliveryResult r = new PushDeliveryResult(
                "sched-1", "job-1", PushDeliveryMode.ATTACHMENT,
                mutableList, 1024L, NOW, true, null
        );
        mutableList.add("extra@x.com");
        assertThat(r.recipients()).hasSize(1);
    }

    @Test
    void multipleRecipients_preserved() {
        PushDeliveryResult r = new PushDeliveryResult(
                "sched-1", "job-1", PushDeliveryMode.SIGNED_LINK,
                List.of("a@b.com", "c@d.com"), 2048L, NOW, true, null
        );
        assertThat(r.recipients()).containsExactly("a@b.com", "c@d.com");
    }
}
