package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataJobStatus#isTerminal()} across every enum value.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class DataJobStatusTest {

    @Test
    void queued_isNotTerminal() {
        assertThat(DataJobStatus.QUEUED.isTerminal()).isFalse();
    }

    @Test
    void running_isNotTerminal() {
        assertThat(DataJobStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void succeeded_isTerminal() {
        assertThat(DataJobStatus.SUCCEEDED.isTerminal()).isTrue();
    }

    @Test
    void failed_isTerminal() {
        assertThat(DataJobStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    void cancelled_isTerminal() {
        assertThat(DataJobStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void expired_isTerminal() {
        assertThat(DataJobStatus.EXPIRED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(DataJobStatus.class)
    void everyValue_isEitherTerminalOrNot(DataJobStatus status) {
        boolean terminal = status.isTerminal();
        if (status == DataJobStatus.QUEUED || status == DataJobStatus.RUNNING) {
            assertThat(terminal).isFalse();
        } else {
            assertThat(terminal).isTrue();
        }
    }

    @Test
    void enumHasSixValues() {
        assertThat(DataJobStatus.values()).hasSize(6);
    }
}
