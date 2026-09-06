package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link RobotsTxtGate}.
 *
 * <p>Uses a spy to override {@code fetchAndParseRobotsTxt()} so that
 * tests never make real HTTP calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@ExtendWith(MockitoExtension.class)
class RobotsTxtGateTest {

    private RobotsTxtGate gate;

    @BeforeEach
    void setUp() {
        gate = spy(new RobotsTxtGate(List.of("blocked-domain.com")));
    }

    @Test
    @DisplayName("isAllowed() returns true for path allowed by robots.txt")
    void isAllowed_allowedPath_returnsTrue() {
        doReturn(List.of("/private/")).when(gate)
                .fetchAndParseRobotsTxt("example.com");

        boolean result = gate.isAllowed("https://example.com/public/page");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAllowed() returns false for path disallowed by robots.txt")
    void isAllowed_disallowedPath_returnsFalse() {
        doReturn(List.of("/health/")).when(gate)
                .fetchAndParseRobotsTxt("example.com");

        boolean result = gate.isAllowed("https://example.com/health/ai-article");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isAllowed() returns false for domain on denylist")
    void isAllowed_deniedDomain_returnsFalse() {
        boolean result = gate.isAllowed("https://blocked-domain.com/some-page");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isAllowed() denylist overrides robots.txt allow")
    void isAllowed_denylistOverridesRobots() {
        boolean result = gate.isAllowed("https://blocked-domain.com/allowed-by-robots");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isAllowed() returns true when robots.txt is missing (fail open)")
    void isAllowed_missingRobotsTxt_returnsTrue() {
        doReturn(List.of()).when(gate)
                .fetchAndParseRobotsTxt("no-robots.com");

        boolean result = gate.isAllowed("https://no-robots.com/any-page");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAllowed() caches robots.txt results for same domain")
    void isAllowed_cacheHit_doesNotRefetch() {
        doReturn(List.of("/blocked/")).when(gate)
                .fetchAndParseRobotsTxt("cached-domain.com");

        gate.isAllowed("https://cached-domain.com/page1");
        gate.isAllowed("https://cached-domain.com/page2");

        org.mockito.Mockito.verify(gate, org.mockito.Mockito.times(1))
                .fetchAndParseRobotsTxt("cached-domain.com");
    }
}
