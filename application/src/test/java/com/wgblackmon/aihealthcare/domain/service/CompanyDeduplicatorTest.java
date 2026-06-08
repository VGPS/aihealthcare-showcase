package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompanyDeduplicator} name/domain deduplication.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
class CompanyDeduplicatorTest {

    private CompanyDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new CompanyDeduplicator();
    }

    @Test
    void dedupe_noDuplicates_returnsAll() {
        List<Company> companies = List.of(
                makeCompany("Alpha", "First company", null),
                makeCompany("Beta", "Second company", null)
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(2);
    }

    @Test
    void dedupe_duplicateNames_keepsLongerDescription() {
        List<Company> companies = List.of(
                makeCompany("Alpha", "Short", null),
                makeCompany("Alpha", "A much longer description for this company", null)
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).contains("much longer");
    }

    @Test
    void dedupe_caseInsensitiveNames() {
        List<Company> companies = List.of(
                makeCompany("ALPHA", "First", null),
                makeCompany("alpha", "Second longer description", null)
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(1);
    }

    @Test
    void dedupe_sameDomain_deduplicates() {
        List<Company> companies = List.of(
                makeCompany("AlphaCo", "First", "https://www.alpha.com"),
                makeCompany("Alpha Inc", "Second longer description", "https://alpha.com")
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).contains("longer");
    }

    @Test
    void dedupe_prefersEntryWithCompanySite() {
        List<Company> companies = List.of(
                makeCompany("Alpha", "Same length desc!", null),
                makeCompany("Alpha", "Same length desc!", "https://alpha.com")
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companySite()).isEqualTo("https://alpha.com");
    }

    @Test
    void dedupe_emptyList_returnsEmpty() {
        List<Company> result = deduplicator.dedupe(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void dedupe_preservesInsertionOrder() {
        List<Company> companies = List.of(
                makeCompany("Charlie", "C company", null),
                makeCompany("Alpha", "A company", null),
                makeCompany("Beta", "B company", null)
        );

        List<Company> result = deduplicator.dedupe(companies);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("Charlie");
        assertThat(result.get(1).name()).isEqualTo("Alpha");
        assertThat(result.get(2).name()).isEqualTo("Beta");
    }

    private Company makeCompany(String name, String description, String companySite) {
        return new Company(name, "test", null, companySite, description, CompanyTags.none(), false, false);
    }
}
