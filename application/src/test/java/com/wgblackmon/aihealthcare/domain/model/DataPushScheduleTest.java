package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataPushSchedule} compact constructor validation
 * and defensive copying.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataPushScheduleTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    private DataPushSchedule validSchedule() {
        return new DataPushSchedule(
                "sched-1", "user@example.com", "Daily Export",
                "articles", "prompt-1", null,
                Map.of("topic", "AI"), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                List.of("recipient@example.com"), true,
                NOW, null, null, null, 0, NOW, NOW
        );
    }

    @Test
    void validSchedule_constructsSuccessfully() {
        DataPushSchedule s = validSchedule();
        assertThat(s.scheduleId()).isEqualTo("sched-1");
        assertThat(s.ownerEmail()).isEqualTo("user@example.com");
        assertThat(s.recipients()).containsExactly("recipient@example.com");
    }

    @Test
    void blankScheduleId_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                " ", "user@example.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scheduleId");
    }

    @Test
    void nullOwnerEmail_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", null, "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ownerEmail");
    }

    @Test
    void blankLabel_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("label");
    }

    @Test
    void blankFeedId_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", " ", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("feedId");
    }

    @Test
    void blankCronExpression_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cronExpression");
    }

    @Test
    void blankZoneId_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("zoneId");
    }

    @Test
    void nullFormat_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), null, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("format");
    }

    @Test
    void emptyRecipients_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of(), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("recipients");
    }

    @Test
    void nullRecipients_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                null, true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("recipients");
    }

    @Test
    void malformedRecipient_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("not-an-email"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("invalid recipient");
    }

    @Test
    void nullRecipientInList_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                Arrays.asList("a@b.com", null, "c@d.com"), true, null, null, null, null, 0, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("invalid recipient");
    }

    @Test
    void negativeConsecutiveFailures_rejected() {
        assertThatThrownBy(() -> new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, -1, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("consecutiveFailures");
    }

    @Test
    void multipleValidRecipients_accepted() {
        DataPushSchedule s = new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("a@b.com", "c@d.com", "e@f.com"), true,
                null, null, null, null, 0, NOW, NOW
        );
        assertThat(s.recipients()).hasSize(3);
    }

    @Test
    void recipients_defensiveCopy() {
        var mutableList = new java.util.ArrayList<>(List.of("a@b.com"));
        DataPushSchedule s = new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                mutableList, true, null, null, null, null, 0, NOW, NOW
        );
        mutableList.add("c@d.com");
        assertThat(s.recipients()).hasSize(1);
    }

    @Test
    void parameters_defensiveCopy() {
        var mutableMap = new HashMap<>(Map.of("key", "val"));
        DataPushSchedule s = new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                mutableMap, ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        );
        mutableMap.put("extra", "extra");
        assertThat(s.parameters()).hasSize(1);
    }

    @Test
    void nullParameters_defaultsToEmptyMap() {
        DataPushSchedule s = new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                null, ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 0, NOW, NOW
        );
        assertThat(s.parameters()).isEmpty();
    }

    @Test
    void consecutiveFailures_zeroAccepted() {
        DataPushSchedule s = validSchedule();
        assertThat(s.consecutiveFailures()).isZero();
    }

    @Test
    void consecutiveFailures_positiveAccepted() {
        DataPushSchedule s = new DataPushSchedule(
                "s1", "u@x.com", "label", "feed", null, null,
                Map.of(), ExportFormat.CSV, "0 0 7 * * *", "UTC",
                List.of("r@x.com"), true, null, null, null, null, 5, NOW, NOW
        );
        assertThat(s.consecutiveFailures()).isEqualTo(5);
    }
}
