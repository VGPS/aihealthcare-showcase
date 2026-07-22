package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
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
 * Unit tests for {@link TrendSnapshotAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class TrendSnapshotAdapterTest {

    private TrendSnapshotRepository repository;
    private TrendSnapshotAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = mock(TrendSnapshotRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        adapter = new TrendSnapshotAdapter(repository, objectMapper);
    }

    @Test
    void save_persistsEntityWithSerializedJson() {
        TrendSignal signal = new TrendSignal("radiology ai", 10, 3, 2,
                3.33, TrendDirection.RISING, Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(signal), List.of(), List.of(), 42);

        adapter.save(snapshot);

        ArgumentCaptor<TrendSnapshotEntity> captor = ArgumentCaptor.forClass(TrendSnapshotEntity.class);
        verify(repository).save(captor.capture());

        TrendSnapshotEntity entity = captor.getValue();
        assertThat(entity.getWindowDays()).isEqualTo(30);
        assertThat(entity.getTotalKeywords()).isEqualTo(42);
        assertThat(entity.getRisingJson()).contains("radiology ai");
    }

    @Test
    void findLatest_returnsMappedSnapshot() {
        TrendSnapshotEntity entity = new TrendSnapshotEntity();
        entity.setId(1L);
        entity.setGeneratedAt(Instant.now());
        entity.setWindowDays(30);
        entity.setRisingJson("[{\"keyword\":\"genomics\",\"current30d\":5,\"previous90d\":1,"
                + "\"baseline180d\":0,\"momentum\":10.0,\"direction\":\"RISING\","
                + "\"firstSeenAt\":\"2026-07-01T00:00:00Z\"}]");
        entity.setFadingJson("[]");
        entity.setNewJson("[]");
        entity.setTotalKeywords(15);

        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));

        Optional<TrendSnapshot> result = adapter.findLatest();

        assertThat(result).isPresent();
        assertThat(result.get().risingTopics()).hasSize(1);
        assertThat(result.get().risingTopics().get(0).keyword()).isEqualTo("genomics");
        assertThat(result.get().totalKeywords()).isEqualTo(15);
    }

    @Test
    void findLatest_returnsEmptyWhenNoSnapshots() {
        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.empty());

        Optional<TrendSnapshot> result = adapter.findLatest();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_returnsMappedList() {
        TrendSnapshotEntity entity = new TrendSnapshotEntity();
        entity.setId(1L);
        entity.setGeneratedAt(Instant.now());
        entity.setWindowDays(30);
        entity.setRisingJson("[]");
        entity.setFadingJson("[]");
        entity.setNewJson("[]");
        entity.setTotalKeywords(0);

        when(repository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of(entity));

        List<TrendSnapshot> result = adapter.findAll();

        assertThat(result).hasSize(1);
    }
}
