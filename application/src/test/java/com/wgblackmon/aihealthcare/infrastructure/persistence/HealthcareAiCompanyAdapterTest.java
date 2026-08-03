package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} tests for {@link HealthcareAiCompanyAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@DataJpaTest
@Import(HealthcareAiCompanyAdapter.class)
class HealthcareAiCompanyAdapterTest {

    @Autowired
    private HealthcareAiCompanyAdapter adapter;

    @Test
    void save_andFindByNameNormalized() {
        HealthcareAiCompany company = company("id-1", "Tempus AI", "tempus ai", "tempus.com");
        adapter.save(company);

        Optional<HealthcareAiCompany> found = adapter.findByNameNormalized("tempus ai");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Tempus AI");
        assertThat(found.get().sourceUrls()).containsExactly("https://src.com");
    }

    @Test
    void findByDomain() {
        adapter.save(company("id-1", "Viz.ai", "viz.ai", "viz.ai"));

        Optional<HealthcareAiCompany> found = adapter.findByDomain("viz.ai");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Viz.ai");
    }

    @Test
    void existsByNameOrDomain_matchesName() {
        adapter.save(company("id-1", "Abridge", "abridge", "abridge.com"));

        assertThat(adapter.existsByNameOrDomain("abridge", null)).isTrue();
        assertThat(adapter.existsByNameOrDomain("unknown", null)).isFalse();
    }

    @Test
    void existsByNameOrDomain_matchesDomain() {
        adapter.save(company("id-1", "Abridge", "abridge", "abridge.com"));

        assertThat(adapter.existsByNameOrDomain("unknown", "abridge.com")).isTrue();
        assertThat(adapter.existsByNameOrDomain("unknown", "other.com")).isFalse();
    }

    @Test
    void findAll_orderedByDiscoveredAtDesc() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-06-01T00:00:00Z");

        adapter.save(new HealthcareAiCompany("id-1", "First Co", "first co", null,
                null, null, null, null, null, null, null, null,
                List.of(), false, List.of(), earlier, null));
        adapter.save(new HealthcareAiCompany("id-2", "Second Co", "second co", null,
                null, null, null, null, null, null, null, null,
                List.of(), false, List.of(), later, null));

        List<HealthcareAiCompany> all = adapter.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).name()).isEqualTo("Second Co"); // later first
    }

    @Test
    void pipeDelimited_roundTrip() {
        HealthcareAiCompany company = new HealthcareAiCompany(
                "id-1", "Multi Source", "multi source", "multi.com",
                "desc", "NYC", 2020, "AI", "imaging", "Series A", "$10M",
                "[{\"name\":\"CEO\"}]",
                List.of("https://a.com", "https://b.com"),
                true,
                List.of("https://v1.com", "https://v2.com"),
                Instant.now(), Instant.now());

        adapter.save(company);

        HealthcareAiCompany found = adapter.findByNameNormalized("multi source").orElseThrow();
        assertThat(found.sourceUrls()).containsExactly("https://a.com", "https://b.com");
        assertThat(found.validationSources()).containsExactly("https://v1.com", "https://v2.com");
        assertThat(found.foundersJson()).isEqualTo("[{\"name\":\"CEO\"}]");
    }

    private HealthcareAiCompany company(String id, String name, String normalized, String domain) {
        return new HealthcareAiCompany(
                id, name, normalized, domain, "AI company", "San Francisco", 2020,
                "Healthcare AI", "diagnostics", "Series B", "$50M", null,
                List.of("https://src.com"), true, List.of("https://val.com"),
                Instant.now(), Instant.now());
    }
}
