package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link DocumentIngestionAdapter}.
 *
 * <p>Verifies batching behaviour: a single {@code store()} call must split
 * its chunk list into batches of {@code BATCH_SIZE} and call
 * {@link VectorStore#add} once per batch, so that a single oversized
 * upload cannot exhaust the embedding API in one unbounded request.
 * Also verifies per-batch resilience: if one batch throws, remaining
 * batches are still processed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-23
 * @updated 2026-11-23
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestionAdapterTest {

    @Mock
    private VectorStore vectorStore;

    private DocumentIngestionAdapter adapter;

    @BeforeEach
    void setUp() {
        // Zero delay so tests do not sleep between batches.
        adapter = new DocumentIngestionAdapter(vectorStore, 0L);
    }

    private static DocumentChunk chunk(int i) {
        return new DocumentChunk("id-" + i, "file.txt", i, "content " + i, "label");
    }

    private static List<DocumentChunk> chunks(int count) {
        List<DocumentChunk> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(chunk(i));
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Batching
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("store() with <= BATCH_SIZE chunks calls vectorStore.add() exactly once")
    void store_singleBatch_callsVectorStoreOnce() {
        adapter.store(chunks(5));

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("store() with > BATCH_SIZE chunks calls vectorStore.add() once per batch")
    void store_multipleBatches_callsVectorStoreForEachBatch() {
        // 25 chunks → 3 batches (10 + 10 + 5)
        adapter.store(chunks(25));

        verify(vectorStore, times(3)).add(anyList());
    }

    @Test
    @DisplayName("store() each batch contains at most BATCH_SIZE documents")
    void store_eachBatchRespectsBatchSize() {
        adapter.store(chunks(25));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(captor.capture());

        List<List<Document>> batches = captor.getAllValues();
        assertThat(batches.get(0)).hasSize(10);
        assertThat(batches.get(1)).hasSize(10);
        assertThat(batches.get(2)).hasSize(5);
    }

    // -------------------------------------------------------------------------
    // Metadata mapping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("store() maps DocumentChunk fields to Document metadata correctly")
    void store_correctMetadataOnDocuments() {
        DocumentChunk c = new DocumentChunk("chunk-uuid", "report.txt", 3, "body text", "Research");
        adapter.store(List.of(c));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        assertThat(doc.getId()).isEqualTo("chunk-uuid");
        assertThat(doc.getText()).isEqualTo("body text");
        assertThat(doc.getMetadata().get("sourceFile")).isEqualTo("report.txt");
        assertThat(doc.getMetadata().get("chunkIndex")).isEqualTo(3);
        assertThat(doc.getMetadata().get("sourceLabel")).isEqualTo("Research");
    }

    // -------------------------------------------------------------------------
    // Resilience
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("store() continues processing remaining batches when one batch fails")
    void store_firstBatchFails_remainingBatchesStillProcessed() {
        doThrow(new RuntimeException("rate limit"))
                .doNothing()
                .when(vectorStore).add(anyList());

        // 15 chunks → 2 batches; first fails, second must still be attempted
        adapter.store(chunks(15));

        verify(vectorStore, times(2)).add(anyList());
    }
}
