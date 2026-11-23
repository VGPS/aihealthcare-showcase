package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enum coverage test for {@link DeploymentContext}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-24
 * @updated 2026-11-24
 */
class DeploymentContextTest {

    @Test
    void allValues_arePresent() {
        assertThat(DeploymentContext.values()).containsExactlyInAnyOrder(
                DeploymentContext.CLINICAL,
                DeploymentContext.OPERATIONAL,
                DeploymentContext.RESEARCH,
                DeploymentContext.GENERAL
        );
    }

    @Test
    void valueOf_roundTrips() {
        for (DeploymentContext dc : DeploymentContext.values()) {
            assertThat(DeploymentContext.valueOf(dc.name())).isEqualTo(dc);
        }
    }
}
