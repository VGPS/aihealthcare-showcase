package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PipelineHealthService}.
 *
 * <p>Tests pre-flight validation, run tracking, and dry-run mode.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class PipelineHealthServiceTest {

    @Mock
    private Environment env;

    @Mock
    private VectorStore vectorStore;

    /** Service with vector store, H2, NO API keys */
    private PipelineHealthService serviceNoKeys;
    /** Service with vector store, H2, WITH Anthropic key */
    private PipelineHealthService serviceWithAnthropicKey;
    /** Service WITHOUT vector store, NO API keys */
    private PipelineHealthService serviceWithoutVectorStore;
    /** Service with vector store, H2, WITH OpenAI key */
    private PipelineHealthService serviceWithOpenAiKey;

    @BeforeEach
    void setUp() {
        when(env.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:test");

        ObjectProvider<VectorStore> withVs = new ObjectProvider<>() {
            @Override public VectorStore getObject() { return vectorStore; }
            @Override public VectorStore getIfAvailable() { return vectorStore; }
            @Override public VectorStore getIfUnique() { return vectorStore; }
        };

        ObjectProvider<VectorStore> withoutVs = new ObjectProvider<>() {
            @Override public VectorStore getObject() { return null; }
            @Override public VectorStore getIfAvailable() { return null; }
            @Override public VectorStore getIfUnique() { return null; }
        };

        // serviceNoKeys — no API keys
        when(env.getProperty("ANTHROPIC_API_KEY", "")).thenReturn("");
        when(env.getProperty("spring.ai.anthropic.api-key", "")).thenReturn("");
        when(env.getProperty("OPENAI_API_KEY", "")).thenReturn("");
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("");
        when(env.getProperty("PERPLEXITY_API_KEY", "")).thenReturn("");
        when(env.getProperty("aihealthcare.perplexity.api-key", "")).thenReturn("");
        serviceNoKeys = new PipelineHealthService(env, withVs, null, null);

        // serviceWithAnthropicKey — Anthropic key present
        when(env.getProperty("ANTHROPIC_API_KEY", "")).thenReturn("sk-ant-real-key-123");
        serviceWithAnthropicKey = new PipelineHealthService(env, withVs, null, null);

        // serviceWithoutVectorStore — no keys, no vector store
        when(env.getProperty("ANTHROPIC_API_KEY", "")).thenReturn("");
        serviceWithoutVectorStore = new PipelineHealthService(env, withoutVs, null, null);

        // serviceWithOpenAiKey — OpenAI key present
        when(env.getProperty("OPENAI_API_KEY", "")).thenReturn("sk-real-openai-key");
        serviceWithOpenAiKey = new PipelineHealthService(env, withVs, null, null);
    }

    // --- Pre-flight checks ---

    @Test
    void preFlightCheck_rssFeedsHasNoWarnings() {
        List<String> warnings = serviceNoKeys.preFlightCheck("rss-feeds");
        assertThat(warnings).isEmpty();
    }

    @Test
    void preFlightCheck_wikiCompileWarnsWhenNoAnthropicKey() {
        List<String> warnings = serviceNoKeys.preFlightCheck("wiki-compile");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("ANTHROPIC_API_KEY");
    }

    @Test
    void preFlightCheck_wikiCompileNoWarningsWhenKeyPresent() {
        List<String> warnings = serviceWithAnthropicKey.preFlightCheck("wiki-compile");
        assertThat(warnings).isEmpty();
    }

    @Test
    void preFlightCheck_embeddingWarnsWhenNoVectorStore() {
        List<String> warnings = serviceWithoutVectorStore.preFlightCheck("embedding");
        assertThat(warnings).anyMatch(w -> w.contains("VectorStore"));
    }

    @Test
    void preFlightCheck_embeddingWarnsOnH2() {
        List<String> warnings = serviceWithOpenAiKey.preFlightCheck("embedding");
        assertThat(warnings).anyMatch(w -> w.contains("H2"));
    }

    @Test
    void preFlightCheck_trendDetectionWarnsWithoutAnthropicKey() {
        List<String> warnings = serviceNoKeys.preFlightCheck("trend-detection");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("ANTHROPIC_API_KEY");
    }

    @Test
    void preFlightCheckAll_returnsResultsForAllPipelines() {
        Map<String, List<String>> results = serviceNoKeys.preFlightCheckAll(
                List.of("rss-feeds", "competitor", "wiki-compile"));
        assertThat(results).hasSize(3);
        assertThat(results).containsKey("rss-feeds");
        assertThat(results).containsKey("competitor");
        assertThat(results).containsKey("wiki-compile");
    }

    // --- Run tracking ---

    @Test
    void recordRun_storesAndRetrievesLastRun() {
        Instant start = Instant.now().minusSeconds(10);
        Instant end = Instant.now();
        PipelineHealthService.PipelineRunRecord record =
                PipelineHealthService.PipelineRunRecord.success("rss-feeds", 42, start, end);

        serviceNoKeys.recordRun("rss-feeds", record);
        PipelineHealthService.PipelineRunRecord retrieved = serviceNoKeys.getLastRun("rss-feeds");

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.status()).isEqualTo("SUCCESS");
        assertThat(retrieved.itemsProcessed()).isEqualTo(42);
    }

    @Test
    void getLastRun_returnsNullForUnknownPipeline() {
        PipelineHealthService.PipelineRunRecord result = serviceNoKeys.getLastRun("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void getAllLastRuns_returnsAllRecordedRuns() {
        Instant start = Instant.now().minusSeconds(5);
        Instant end = Instant.now();
        serviceNoKeys.recordRun("rss-feeds",
                PipelineHealthService.PipelineRunRecord.success("rss-feeds", 10, start, end));
        serviceNoKeys.recordRun("competitor",
                PipelineHealthService.PipelineRunRecord.failure("competitor", "timeout", start, end));

        Map<String, PipelineHealthService.PipelineRunRecord> runs = serviceNoKeys.getAllLastRuns();
        assertThat(runs).hasSize(2);
        assertThat(runs.get("rss-feeds").status()).isEqualTo("SUCCESS");
        assertThat(runs.get("competitor").status()).isEqualTo("FAILED");
    }

    @Test
    void pipelineRunRecord_failureHasErrorMessage() {
        Instant start = Instant.now().minusSeconds(2);
        Instant end = Instant.now();
        PipelineHealthService.PipelineRunRecord record =
                PipelineHealthService.PipelineRunRecord.failure("wiki-compile", "API key missing", start, end);

        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.itemsFailed()).isEqualTo(1);
        assertThat(record.errors()).containsExactly("API key missing");
        assertThat(record.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void pipelineRunRecord_partialHasBothCounts() {
        Instant start = Instant.now().minusSeconds(3);
        Instant end = Instant.now();
        PipelineHealthService.PipelineRunRecord record =
                PipelineHealthService.PipelineRunRecord.partial("rss-feeds", 8, 2,
                        List.of("feed timeout", "parse error"), start, end);

        assertThat(record.status()).isEqualTo("PARTIAL");
        assertThat(record.itemsProcessed()).isEqualTo(8);
        assertThat(record.itemsFailed()).isEqualTo(2);
        assertThat(record.errors()).hasSize(2);
    }

    // --- Dry run ---

    @Test
    void dryRun_returnsReadyForHealthyPipeline() {
        PipelineHealthService.DryRunResult result = serviceNoKeys.dryRun("rss-feeds");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.detail()).contains("harvest");
    }

    @Test
    void dryRun_returnsBlockedWhenPrerequisitesMissing() {
        PipelineHealthService.DryRunResult result = serviceNoKeys.dryRun("wiki-compile");
        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.detail()).contains("Cannot run");
    }
}
