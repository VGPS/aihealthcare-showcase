package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LlmCallUsageAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (repositories,
 * entity scanning) without the full Spring context.  The adapter is imported
 * via {@code @Import} to make it available for injection.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@DataJpaTest
@Import(LlmCallUsageAdapter.class)
class LlmCallUsageAdapterTest {

    @Autowired
    private LlmCallUsageAdapter adapter;

    @Autowired
    private LlmCallUsageRepository repository;

    @Test
    void save_persistsUsage() {
        LlmCallUsage usage = new LlmCallUsage(null, "legal-trends", "claude-sonnet-4-6",
                1000, 200, 1200, Instant.parse("2026-10-18T04:00:00Z"));

        adapter.save(usage);

        List<LlmCallUsageEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getPipelineId()).isEqualTo("legal-trends");
        assertThat(all.get(0).getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(all.get(0).getPromptTokens()).isEqualTo(1000);
        assertThat(all.get(0).getOutputTokens()).isEqualTo(200);
        assertThat(all.get(0).getTotalTokens()).isEqualTo(1200);
    }

    @Test
    void findSince_returnsRowsAfterInstantOrderedDesc() {
        Instant t1 = Instant.parse("2026-10-16T04:00:00Z");
        Instant t2 = Instant.parse("2026-10-17T04:00:00Z");
        Instant t3 = Instant.parse("2026-10-18T04:00:00Z");

        adapter.save(new LlmCallUsage(null, "legal-trends", "claude-sonnet-4-6", 100, 10, 110, t1));
        adapter.save(new LlmCallUsage(null, "deal-signals", "claude-sonnet-4-6", 100, 10, 110, t2));
        adapter.save(new LlmCallUsage(null, "market-digest", "claude-sonnet-4-6", 100, 10, 110, t3));

        List<LlmCallUsage> result = adapter.findSince(Instant.parse("2026-10-17T00:00:00Z"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).pipelineId()).isEqualTo("market-digest");
        assertThat(result.get(1).pipelineId()).isEqualTo("deal-signals");
    }

    @Test
    void findSince_excludesRowsBeforeTheGivenInstant() {
        adapter.save(new LlmCallUsage(null, "legal-trends", "claude-sonnet-4-6", 100, 10, 110,
                Instant.parse("2026-10-01T00:00:00Z")));

        List<LlmCallUsage> result = adapter.findSince(Instant.parse("2026-10-15T00:00:00Z"));

        assertThat(result).isEmpty();
    }
}
