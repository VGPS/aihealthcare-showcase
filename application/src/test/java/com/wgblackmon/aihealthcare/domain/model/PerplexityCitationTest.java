package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PerplexityCitation} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
class PerplexityCitationTest {

    @Test
    void validCitation_createsSuccessfully() {
        PerplexityCitation citation = new PerplexityCitation(
                "cit-1", "comp-1", "https://example.com", "context",
                CompanyCallType.DISCOVERY, Instant.now());
        assertThat(citation.citationId()).isEqualTo("cit-1");
        assertThat(citation.callType()).isEqualTo(CompanyCallType.DISCOVERY);
    }

    @Test
    void nullCitationId_throws() {
        assertThatThrownBy(() -> new PerplexityCitation(
                null, "comp", "https://url.com", null,
                CompanyCallType.EXTRACTION, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("citationId");
    }

    @Test
    void blankUrl_throws() {
        assertThatThrownBy(() -> new PerplexityCitation(
                "id", "comp", "", null,
                CompanyCallType.VALIDATION, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void nullCallType_throws() {
        assertThatThrownBy(() -> new PerplexityCitation(
                "id", "comp", "https://url.com", null,
                null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callType");
    }

    @Test
    void nullCompanyId_allowedForDiscoveryCitations() {
        PerplexityCitation citation = new PerplexityCitation(
                "cit-1", null, "https://example.com", null,
                CompanyCallType.DISCOVERY, Instant.now());
        assertThat(citation.companyId()).isNull();
    }
}
