package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PushApprovalGateAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@DataJpaTest
@Import(PushApprovalGateAdapter.class)
class PushApprovalGateAdapterTest {

    @Autowired
    private PushApprovalGateAdapter adapter;

    @Test
    void isRequired_noRowExists_returnsFalse() {
        assertThat(adapter.isRequired("sched-1")).isFalse();
    }

    @Test
    void setRequired_true_thenIsRequired_returnsTrue() {
        adapter.setRequired("sched-1", true);
        assertThat(adapter.isRequired("sched-1")).isTrue();
    }

    @Test
    void setRequired_false_thenIsRequired_returnsFalse() {
        adapter.setRequired("sched-1", true);
        adapter.setRequired("sched-1", false);
        assertThat(adapter.isRequired("sched-1")).isFalse();
    }

    @Test
    void differentScheduleIds_areIsolated() {
        adapter.setRequired("sched-1", true);
        assertThat(adapter.isRequired("sched-2")).isFalse();
    }

    @Test
    void setRequired_updatesExistingRow_doesNotCreateDuplicate() {
        adapter.setRequired("sched-1", true);
        adapter.setRequired("sched-1", false);
        adapter.setRequired("sched-1", true);
        assertThat(adapter.isRequired("sched-1")).isTrue();
    }
}
