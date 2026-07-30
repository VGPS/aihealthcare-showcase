package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalTrendSnapshotAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class LegalTrendSnapshotAdapterTest {

    private LegalTrendSnapshotRepository repository;
    private LegalTrendSnapshotAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = mock(LegalTrendSnapshotRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        adapter = new LegalTrendSnapshotAdapter(repository, objectMapper);
    }

    @Test
    void save_persistsEntityWithSerializedJson() {
        LegalTrendSignal signal = new LegalTrendSignal(
                "FDA enforcement", "REGULATION", 10, 3,
                3.33, TrendDirection.RISING, List.of("a1"));
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, List.of(signal), 42);

        adapter.save(snapshot);

        ArgumentCaptor<LegalTrendSnapshotEntity> captor =
                ArgumentCaptor.forClass(LegalTrendSnapshotEntity.class);
        verify(repository).save(captor.capture());

        LegalTrendSnapshotEntity entity = captor.getValue();
        assertThat(entity.getWindowDays()).isEqualTo(30);
        assertThat(entity.getTotalKeywords()).isEqualTo(42);
        assertThat(entity.getRisingTrendsJson()).contains("FDA enforcement");
    }

    @Test
    void findLatest_returnsMappedSnapshot() {
        LegalTrendSnapshotEntity entity = new LegalTrendSnapshotEntity();
        entity.setId(1L);
        entity.setGeneratedAt(Instant.now());
        entity.setWindowDays(30);
        entity.setRisingTrendsJson("[{\"keyword\":\"HIPAA\",\"category\":\"LITIGATION\","
                + "\"current30d\":5,\"previous90d\":1,\"momentum\":10.0,"
                + "\"direction\":\"RISING\",\"topArticleIds\":[\"a1\"]}]");
        entity.setTotalKeywords(15);

        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));

        Optional<LegalTrendSnapshot> result = adapter.findLatest();

        assertThat(result).isPresent();
        assertThat(result.get().risingTrends()).hasSize(1);
        assertThat(result.get().risingTrends().get(0).keyword()).isEqualTo("HIPAA");
        assertThat(result.get().totalKeywords()).isEqualTo(15);
    }

    @Test
    void findLatest_returnsEmptyWhenNoSnapshots() {
        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.empty());

        Optional<LegalTrendSnapshot> result = adapter.findLatest();

        assertThat(result).isEmpty();
    }

    @Test
    void findLatest_handlesEmptyJson() {
        LegalTrendSnapshotEntity entity = new LegalTrendSnapshotEntity();
        entity.setId(1L);
        entity.setGeneratedAt(Instant.now());
        entity.setWindowDays(30);
        entity.setRisingTrendsJson("[]");
        entity.setTotalKeywords(0);

        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));

        Optional<LegalTrendSnapshot> result = adapter.findLatest();

        assertThat(result).isPresent();
        assertThat(result.get().risingTrends()).isEmpty();
    }
}
