package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyCallType;
import com.wgblackmon.aihealthcare.domain.model.PerplexityCitation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} tests for {@link PerplexityCitationAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@DataJpaTest
@Import(PerplexityCitationAdapter.class)
class PerplexityCitationAdapterTest {

    @Autowired
    private PerplexityCitationAdapter adapter;

    @Test
    void save_andFindByCompanyId() {
        PerplexityCitation citation = new PerplexityCitation(
                "cit-1", "comp-1", "https://example.com", "context text",
                CompanyCallType.DISCOVERY, Instant.now());

        adapter.save(citation);

        List<PerplexityCitation> found = adapter.findByCompanyId("comp-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).url()).isEqualTo("https://example.com");
        assertThat(found.get(0).callType()).isEqualTo(CompanyCallType.DISCOVERY);
    }

    @Test
    void saveAll_persistsMultipleCitations() {
        List<PerplexityCitation> citations = List.of(
                new PerplexityCitation("c1", "comp-1", "https://a.com", null,
                        CompanyCallType.EXTRACTION, Instant.now()),
                new PerplexityCitation("c2", "comp-1", "https://b.com", null,
                        CompanyCallType.VALIDATION, Instant.now()));

        adapter.saveAll(citations);

        List<PerplexityCitation> found = adapter.findByCompanyId("comp-1");
        assertThat(found).hasSize(2);
    }

    @Test
    void findByCompanyId_returnsEmptyForUnknown() {
        List<PerplexityCitation> found = adapter.findByCompanyId("nonexistent");
        assertThat(found).isEmpty();
    }
}
