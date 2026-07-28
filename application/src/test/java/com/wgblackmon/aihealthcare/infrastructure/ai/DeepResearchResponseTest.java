package com.wgblackmon.aihealthcare.infrastructure.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DeepResearchResponse} record and its helper methods.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-28
 * @updated 2026-07-28
 */
class DeepResearchResponseTest {

    @Test
    void isTerminal_trueForCompleted() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "completed", List.of(), null, null);
        assertThat(response.isTerminal()).isTrue();
        assertThat(response.isCompleted()).isTrue();
    }

    @Test
    void isTerminal_trueForFailed() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "failed", List.of(), null, null);
        assertThat(response.isTerminal()).isTrue();
        assertThat(response.isCompleted()).isFalse();
    }

    @Test
    void isTerminal_falseForPending() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "pending", List.of(), null, null);
        assertThat(response.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_falseForInProgress() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "in_progress", List.of(), null, null);
        assertThat(response.isTerminal()).isFalse();
    }

    @Test
    void extractContent_returnsContentFromFirstChoice() {
        DeepResearchResponse.Message msg = new DeepResearchResponse.Message("assistant", "Report text.");
        DeepResearchResponse.Choice choice = new DeepResearchResponse.Choice(0, "stop", msg);
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "completed", List.of(choice), null, null);

        assertThat(response.extractContent()).isEqualTo("Report text.");
    }

    @Test
    void extractContent_returnsNull_whenNoChoices() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "completed", List.of(), null, null);
        assertThat(response.extractContent()).isNull();
    }

    @Test
    void extractContent_returnsNull_whenChoicesNull() {
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "completed", null, null, null);
        assertThat(response.extractContent()).isNull();
    }

    @Test
    void extractContent_returnsNull_whenMessageNull() {
        DeepResearchResponse.Choice choice = new DeepResearchResponse.Choice(0, "stop", null);
        DeepResearchResponse response = new DeepResearchResponse(
                "job-1", "sonar-deep-research", "completed", List.of(choice), null, null);
        assertThat(response.extractContent()).isNull();
    }
}
