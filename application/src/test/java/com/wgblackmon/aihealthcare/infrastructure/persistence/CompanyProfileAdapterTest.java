package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompanyProfileAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@DataJpaTest
@Import(CompanyProfileAdapter.class)
class CompanyProfileAdapterTest {

    @Autowired
    private CompanyProfileAdapter adapter;

    @Test
    void saveAndFindBySlug() {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Clinical data", List.of("imaging", "infra"),
                List.of("a1", "a2"), now, now, 2, TrendDirection.RISING);

        adapter.save(profile);
        Optional<CompanyProfile> result = adapter.findBySlug("tempus-ai");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Tempus AI");
        assertThat(result.get().categories()).containsExactly("imaging", "infra");
        assertThat(result.get().articleIds()).containsExactly("a1", "a2");
        assertThat(result.get().trendDirection()).isEqualTo(TrendDirection.RISING);
    }

    @Test
    void findBySlugReturnsEmptyWhenNotFound() {
        Optional<CompanyProfile> result = adapter.findBySlug("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findAllOrdersByArticleCountDesc() {
        Instant now = Instant.now();
        CompanyProfile small = new CompanyProfile("small", "Small Co",
                "https://small.com", "Small", List.of(), List.of("a1"),
                now, now, 1, TrendDirection.STABLE);
        CompanyProfile large = new CompanyProfile("large", "Large Co",
                "https://large.com", "Large", List.of(), List.of("a1", "a2", "a3"),
                now, now, 3, TrendDirection.RISING);

        adapter.save(small);
        adapter.save(large);

        List<CompanyProfile> result = adapter.findAll();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).slug()).isEqualTo("large");
        assertThat(result.get(1).slug()).isEqualTo("small");
    }

    @Test
    void saveUpdatesExistingProfile() {
        Instant now = Instant.now();
        CompanyProfile original = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Original", List.of(),
                List.of("a1"), now, now, 1, TrendDirection.NEW);
        adapter.save(original);

        CompanyProfile updated = new CompanyProfile("tempus-ai", "Tempus AI",
                "https://tempus.com", "Updated description", List.of("scribe"),
                List.of("a1", "a2"), now, now, 2, TrendDirection.RISING);
        adapter.save(updated);

        Optional<CompanyProfile> result = adapter.findBySlug("tempus-ai");
        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("Updated description");
        assertThat(result.get().articleCount()).isEqualTo(2);
    }

    @Test
    void findByCategoryFindsMatchingProfiles() {
        Instant now = Instant.now();
        CompanyProfile imaging = new CompanyProfile("img-co", "Img Co",
                "https://img.com", "Imaging", List.of("imaging"),
                List.of(), now, now, 0, TrendDirection.STABLE);
        CompanyProfile scribe = new CompanyProfile("scribe-co", "Scribe Co",
                "https://scribe.com", "Scribe", List.of("scribe"),
                List.of(), now, now, 0, TrendDirection.STABLE);

        adapter.save(imaging);
        adapter.save(scribe);

        List<CompanyProfile> result = adapter.findByCategory("imaging");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("img-co");
    }
}
