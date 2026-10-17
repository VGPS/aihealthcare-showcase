package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PushApprovalRequestAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@DataJpaTest
@Import(PushApprovalRequestAdapter.class)
class PushApprovalRequestAdapterTest {

    @Autowired
    private PushApprovalRequestAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private PushApprovalRequest pending(String jobId) {
        return new PushApprovalRequest(jobId, "sched-1", "alice@test.com",
                NOW, PushApprovalStatus.PENDING, null, null);
    }

    @Test
    void save_thenFindByJobId_returnsSavedRequest() {
        adapter.save(pending("job-1"));

        Optional<PushApprovalRequest> found = adapter.findByJobId("job-1");

        assertThat(found).isPresent();
        assertThat(found.get().scheduleId()).isEqualTo("sched-1");
        assertThat(found.get().status()).isEqualTo(PushApprovalStatus.PENDING);
    }

    @Test
    void findByJobId_noRow_returnsEmpty() {
        assertThat(adapter.findByJobId("missing")).isEmpty();
    }

    @Test
    void findPending_onlyReturnsPendingRequests() {
        adapter.save(pending("job-1"));
        adapter.save(new PushApprovalRequest("job-2", "sched-1", "alice@test.com",
                NOW, PushApprovalStatus.APPROVED, NOW, "admin@test.com"));

        List<PushApprovalRequest> pending = adapter.findPending();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).jobId()).isEqualTo("job-1");
    }

    @Test
    void save_updatesExistingRow_doesNotCreateDuplicate() {
        adapter.save(pending("job-1"));
        adapter.save(new PushApprovalRequest("job-1", "sched-1", "alice@test.com",
                NOW, PushApprovalStatus.APPROVED, NOW, "admin@test.com"));

        Optional<PushApprovalRequest> found = adapter.findByJobId("job-1");

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(PushApprovalStatus.APPROVED);
        assertThat(adapter.findPending()).isEmpty();
    }
}
