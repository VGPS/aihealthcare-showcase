package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataPushScheduleAdapter} — round-trip persistence,
 * pipe-delimited recipients, ownership isolation, due-schedule querying, and
 * the claim concurrency primitive.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@DataJpaTest
class DataPushScheduleAdapterTest {

    @Autowired
    private EnterprisePushScheduleRepository repository;

    private DataPushScheduleAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant NEXT_RUN = Instant.parse("2026-09-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        adapter = new DataPushScheduleAdapter(repository);
    }

    private DataPushSchedule schedule(String scheduleId, String ownerEmail, boolean active,
                                       Instant nextRunAt, List<String> recipients) {
        return new DataPushSchedule(
                scheduleId, ownerEmail, "Daily Export", "articles",
                null, null, Map.of("topic", "AI"), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                recipients, active, nextRunAt, null, null, null, 0, NOW, NOW
        );
    }

    @Test
    void save_roundTrip_singleRecipient() {
        DataPushSchedule saved = adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN,
                List.of("recipient@test.com")));
        assertThat(saved.scheduleId()).isEqualTo("s1");
        assertThat(saved.ownerEmail()).isEqualTo("alice@test.com");
        assertThat(saved.recipients()).containsExactly("recipient@test.com");
        assertThat(saved.format()).isEqualTo(ExportFormat.CSV);
        assertThat(saved.cronExpression()).isEqualTo("0 0 7 * * MON-FRI");
    }

    @Test
    void save_roundTrip_multipleRecipients() {
        DataPushSchedule saved = adapter.save(schedule("s2", "alice@test.com", true, NEXT_RUN,
                List.of("a@b.com", "c@d.com", "e@f.com")));
        assertThat(saved.recipients()).containsExactly("a@b.com", "c@d.com", "e@f.com");
    }

    @Test
    void save_roundTrip_parametersPreserved() {
        DataPushSchedule saved = adapter.save(schedule("s3", "alice@test.com", true, NEXT_RUN,
                List.of("a@b.com")));
        assertThat(saved.parameters()).containsEntry("topic", "AI");
    }

    @Test
    void findByScheduleIdAndOwnerEmail_returnsOwnedSchedule() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));
        Optional<DataPushSchedule> found = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(found).isPresent();
        assertThat(found.get().label()).isEqualTo("Daily Export");
    }

    @Test
    void findByScheduleIdAndOwnerEmail_crossOwnerReturnsEmpty() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));
        Optional<DataPushSchedule> found = adapter.findByScheduleIdAndOwnerEmail("s1", "bob@test.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findDue_excludesInactive() {
        adapter.save(schedule("s-active", "alice@test.com", true, NOW.minusSeconds(60), List.of("a@b.com")));
        adapter.save(schedule("s-inactive", "alice@test.com", false, NOW.minusSeconds(60), List.of("a@b.com")));

        List<DataPushSchedule> due = adapter.findDue(NOW, 10);
        assertThat(due).hasSize(1);
        assertThat(due.get(0).scheduleId()).isEqualTo("s-active");
    }

    @Test
    void findDue_excludesFuture() {
        adapter.save(schedule("s-past", "alice@test.com", true, NOW.minusSeconds(60), List.of("a@b.com")));
        adapter.save(schedule("s-future", "alice@test.com", true, NOW.plusSeconds(3600), List.of("a@b.com")));

        List<DataPushSchedule> due = adapter.findDue(NOW, 10);
        assertThat(due).hasSize(1);
        assertThat(due.get(0).scheduleId()).isEqualTo("s-past");
    }

    @Test
    void findDue_respectsLimitAndOrdering() {
        adapter.save(schedule("s-later", "alice@test.com", true, NOW.minusSeconds(30), List.of("a@b.com")));
        adapter.save(schedule("s-earlier", "alice@test.com", true, NOW.minusSeconds(120), List.of("a@b.com")));
        adapter.save(schedule("s-middle", "alice@test.com", true, NOW.minusSeconds(60), List.of("a@b.com")));

        List<DataPushSchedule> due = adapter.findDue(NOW, 2);
        assertThat(due).hasSize(2);
        assertThat(due.get(0).scheduleId()).isEqualTo("s-earlier");
        assertThat(due.get(1).scheduleId()).isEqualTo("s-middle");
    }

    @Test
    void claim_returnsTrueOnce_falseOnSecondCall() {
        Instant observedNext = NOW.minusSeconds(60);
        adapter.save(schedule("s1", "alice@test.com", true, observedNext, List.of("a@b.com")));

        boolean first = adapter.claim("s1", observedNext, NEXT_RUN, NOW);
        assertThat(first).isTrue();

        boolean second = adapter.claim("s1", observedNext, NEXT_RUN.plusSeconds(3600), NOW);
        assertThat(second).isFalse();
    }

    @Test
    void claim_returnsFalseForInactiveSchedule() {
        Instant observedNext = NOW.minusSeconds(60);
        adapter.save(schedule("s-inactive", "alice@test.com", false, observedNext, List.of("a@b.com")));

        boolean claimed = adapter.claim("s-inactive", observedNext, NEXT_RUN, NOW);
        assertThat(claimed).isFalse();
    }

    @Test
    void claim_advancesNextRunAt() {
        Instant observedNext = NOW.minusSeconds(60);
        adapter.save(schedule("s1", "alice@test.com", true, observedNext, List.of("a@b.com")));

        adapter.claim("s1", observedNext, NEXT_RUN, NOW);

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isPresent();
        assertThat(after.get().nextRunAt()).isEqualTo(NEXT_RUN);
        assertThat(after.get().lastRunAt()).isEqualTo(NOW);
    }

    @Test
    void recordOutcome_setsStatusAndResetsFailuresOnSuccess() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));

        adapter.recordOutcome("s1", "job-1", DataJobStatus.SUCCEEDED, NOW);

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isPresent();
        assertThat(after.get().lastStatus()).isEqualTo(DataJobStatus.SUCCEEDED);
        assertThat(after.get().lastJobId()).isEqualTo("job-1");
        assertThat(after.get().consecutiveFailures()).isZero();
    }

    @Test
    void recordOutcome_incrementsFailuresOnFailed() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));

        adapter.recordOutcome("s1", "job-1", DataJobStatus.FAILED, NOW);

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isPresent();
        assertThat(after.get().consecutiveFailures()).isEqualTo(1);

        adapter.recordOutcome("s1", "job-2", DataJobStatus.FAILED, NOW.plusSeconds(60));
        after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after.get().consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void deactivate_setsActiveToFalse() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));

        adapter.deactivate("s1", "too many failures");

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isPresent();
        assertThat(after.get().active()).isFalse();
    }

    @Test
    void delete_removesSchedule() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));

        adapter.delete("s1", "alice@test.com");

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isEmpty();
    }

    @Test
    void delete_doesNotAffectOtherOwner() {
        adapter.save(schedule("s1", "alice@test.com", true, NEXT_RUN, List.of("a@b.com")));

        adapter.delete("s1", "bob@test.com");

        Optional<DataPushSchedule> after = adapter.findByScheduleIdAndOwnerEmail("s1", "alice@test.com");
        assertThat(after).isPresent();
    }
}
