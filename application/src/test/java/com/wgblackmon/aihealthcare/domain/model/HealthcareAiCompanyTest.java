package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HealthcareAiCompany} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
class HealthcareAiCompanyTest {

    @Test
    void validCompany_createsSuccessfully() {
        HealthcareAiCompany company = company("id-1", "Tempus AI", "tempus ai");
        assertThat(company.companyId()).isEqualTo("id-1");
        assertThat(company.name()).isEqualTo("Tempus AI");
        assertThat(company.nameNormalized()).isEqualTo("tempus ai");
    }

    @Test
    void nullCompanyId_throws() {
        assertThatThrownBy(() -> company(null, "Name", "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void blankName_throws() {
        assertThatThrownBy(() -> company("id", "", "norm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void blankNameNormalized_throws() {
        assertThatThrownBy(() -> company("id", "Name", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nameNormalized");
    }

    @Test
    void nullDiscoveredAt_throws() {
        assertThatThrownBy(() -> new HealthcareAiCompany(
                "id", "Name", "name", null, null, null, null,
                null, null, null, null, null, null, null, false, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discoveredAt");
    }

    @Test
    void nullSourceUrls_defaultsToEmptyList() {
        HealthcareAiCompany company = new HealthcareAiCompany(
                "id", "Name", "name", null, null, null, null,
                null, null, null, null, null, null, null, false, null,
                Instant.now(), null);
        assertThat(company.sourceUrls()).isEmpty();
        assertThat(company.validationSources()).isEmpty();
    }

    @Test
    void sourceUrls_defensivelyCopied() {
        List<String> urls = new java.util.ArrayList<>();
        urls.add("https://example.com");
        HealthcareAiCompany company = new HealthcareAiCompany(
                "id", "Name", "name", null, null, null, null,
                null, null, null, null, null, null, urls, false, null,
                Instant.now(), null);
        assertThat(company.sourceUrls()).hasSize(1);
        assertThatThrownBy(() -> company.sourceUrls().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private HealthcareAiCompany company(String id, String name, String normalized) {
        return new HealthcareAiCompany(
                id, name, normalized, "example.com", "A company",
                "San Francisco, CA", 2020, "Healthcare AI", "diagnostics",
                null, "Series B", "$50M", null, List.of("https://src.com"),
                true, List.of("https://val.com"), Instant.now(), Instant.now());
    }
}
