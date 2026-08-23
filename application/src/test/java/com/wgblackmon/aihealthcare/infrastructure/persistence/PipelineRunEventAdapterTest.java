package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PipelineRunEventAdapter} using in-memory H2.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.  The adapter is imported
 * via {@code @Import} to make it available for injection.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
@Import(PipelineRunEventAdapter.class)
class PipelineRunEventAdapterTest {

    @Autowired
    private PipelineRunEventAdapter adapter;

    @Autowired
    private PipelineRunEventRepository repository;

    @Test
    void save_persistsEvent() {
        PipelineRunEvent event = event("rss-harvest", "RSS Feed Harvest",
                PipelineStepStatus.SUCCESS, Instant.parse("2026-08-04T04:00:00Z"), 5, null);

        adapter.save(event);

        List<PipelineRunEventEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getPipelineId()).isEqualTo("rss-harvest");
        assertThat(all.get(0).getStepName()).isEqualTo("RSS Feed Harvest");
        assertThat(all.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(all.get(0).getItemsProcessed()).isEqualTo(5);
        assertThat(all.get(0).getTriggerSource()).isEqualTo("SCHEDULER");
    }

    @Test
    void findRecent_returnsEventsOrderedByStartedAtDesc() {
        Instant t1 = Instant.parse("2026-08-01T04:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T04:00:00Z");
        Instant t3 = Instant.parse("2026-08-03T04:00:00Z");

        adapter.save(event("rss-harvest", "RSS Feed Harvest", PipelineStepStatus.SUCCESS, t1, 10, null));
        adapter.save(event("wiki-compile", "Wiki Compilation", PipelineStepStatus.SUCCESS, t2, 3, null));
        adapter.save(event("trend-detect", "Trend Detection", PipelineStepStatus.FAILED, t3, 0, "Timeout"));

        List<PipelineRunEvent> result = adapter.findRecent(10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).pipelineId()).isEqualTo("trend-detect");
        assertThat(result.get(1).pipelineId()).isEqualTo("wiki-compile");
        assertThat(result.get(2).pipelineId()).isEqualTo("rss-harvest");
    }

    @Test
    void findByPipelineId_filtersCorrectly() {
        Instant t1 = Instant.parse("2026-08-01T04:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T04:00:00Z");
        Instant t3 = Instant.parse("2026-08-03T04:00:00Z");

        adapter.save(event("rss-harvest", "RSS Feed Harvest", PipelineStepStatus.SUCCESS, t1, 10, null));
        adapter.save(event("wiki-compile", "Wiki Compilation", PipelineStepStatus.SUCCESS, t2, 3, null));
        adapter.save(event("rss-harvest", "RSS Feed Harvest", PipelineStepStatus.FAILED, t3, 0, "Connection refused"));

        List<PipelineRunEvent> result = adapter.findByPipelineId("rss-harvest", 10);

        assertThat(result).hasSize(2);
        for (PipelineRunEvent e : result) {
            assertThat(e.pipelineId()).isEqualTo("rss-harvest");
        }
    }

    @Test
    void findLatestPerPipeline_returnsOnePerPipeline() {
        Instant t1 = Instant.parse("2026-08-01T04:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T04:00:00Z");
        Instant t3 = Instant.parse("2026-08-03T04:00:00Z");
        Instant t4 = Instant.parse("2026-08-04T04:00:00Z");

        adapter.save(event("rss-harvest", "RSS Feed Harvest", PipelineStepStatus.SUCCESS, t1, 10, null));
        adapter.save(event("rss-harvest", "RSS Feed Harvest", PipelineStepStatus.FAILED, t3, 0, "Error"));
        adapter.save(event("wiki-compile", "Wiki Compilation", PipelineStepStatus.SUCCESS, t2, 5, null));
        adapter.save(event("wiki-compile", "Wiki Compilation", PipelineStepStatus.SUCCESS, t4, 8, null));

        Map<String, PipelineRunEvent> result = adapter.findLatestPerPipeline();

        assertThat(result).hasSize(2);
        assertThat(result.get("rss-harvest").startedAt()).isEqualTo(t3);
        assertThat(result.get("rss-harvest").status()).isEqualTo(PipelineStepStatus.FAILED);
        assertThat(result.get("wiki-compile").startedAt()).isEqualTo(t4);
        assertThat(result.get("wiki-compile").itemsProcessed()).isEqualTo(8);
    }

    @Test
    void findRecent_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            Instant t = Instant.parse("2026-08-0" + (i + 1) + "T04:00:00Z");
            adapter.save(event("pipeline-" + i, "Step " + i, PipelineStepStatus.SUCCESS, t, i, null));
        }

        List<PipelineRunEvent> result = adapter.findRecent(3);

        assertThat(result).hasSize(3);
    }

    // --- Helpers ---

    private PipelineRunEvent event(String pipelineId, String stepName,
                                    PipelineStepStatus status, Instant startedAt,
                                    int itemsProcessed, String errorMessage) {
        Instant completedAt = startedAt.plusMillis(1500);
        return new PipelineRunEvent(
                null,
                pipelineId,
                stepName,
                status,
                startedAt,
                completedAt,
                1500L,
                errorMessage,
                itemsProcessed,
                "SCHEDULER",
                null,
                null,
                null
        );
    }
}
