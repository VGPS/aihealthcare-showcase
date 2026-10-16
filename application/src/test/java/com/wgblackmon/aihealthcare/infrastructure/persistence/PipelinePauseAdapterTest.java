package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PipelinePauseAdapter}.
 *
 * <p>Verifies pause persistence, per-key isolation, and the missing-row
 * (not paused) default.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
@DataJpaTest
@Import(PipelinePauseAdapter.class)
class PipelinePauseAdapterTest {

    @Autowired
    private PipelinePauseAdapter adapter;

    @Test
    void isPaused_noRowExists_returnsFalse() {
        assertThat(adapter.isPaused("digest-send")).isFalse();
    }

    @Test
    void setPaused_true_thenIsPaused_returnsTrue() {
        adapter.setPaused("digest-send", true);
        assertThat(adapter.isPaused("digest-send")).isTrue();
    }

    @Test
    void setPaused_false_thenIsPaused_returnsFalse() {
        adapter.setPaused("digest-send", true);
        adapter.setPaused("digest-send", false);
        assertThat(adapter.isPaused("digest-send")).isFalse();
    }

    @Test
    void differentPipelineKeys_areIsolated() {
        adapter.setPaused("digest-send", true);
        assertThat(adapter.isPaused("enterprise-push")).isFalse();
    }

    @Test
    void setPaused_updatesExistingRow_doesNotCreateDuplicate() {
        adapter.setPaused("digest-send", true);
        adapter.setPaused("digest-send", false);
        adapter.setPaused("digest-send", true);
        assertThat(adapter.isPaused("digest-send")).isTrue();
    }
}
