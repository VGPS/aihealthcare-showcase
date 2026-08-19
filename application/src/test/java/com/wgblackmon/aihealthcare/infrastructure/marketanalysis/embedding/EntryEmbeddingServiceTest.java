package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EntryEmbeddingService}.
 *
 * <p>Verifies embedding delegation, null-safety when the model is absent,
 * and fail-open behaviour on API errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class EntryEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void embed_whenModelAvailable_returnsFloatArray() {
        ObjectProvider<EmbeddingModel> provider = mockProvider(embeddingModel);
        EntryEmbeddingService service = new EntryEmbeddingService(provider);

        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(anyString())).thenReturn(vector);

        float[] result = service.embed("FDA approves EpicAI", "A major regulatory approval.");

        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(0.1f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Test
    void embed_whenModelNotAvailable_returnsNull() {
        ObjectProvider<EmbeddingModel> provider = mockProvider(null);
        EntryEmbeddingService service = new EntryEmbeddingService(provider);

        float[] result = service.embed("Any headline", "Any summary.");

        assertThat(result).isNull();
    }

    @Test
    void embed_whenModelThrows_returnsNull() {
        ObjectProvider<EmbeddingModel> provider = mockProvider(embeddingModel);
        EntryEmbeddingService service = new EntryEmbeddingService(provider);

        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API timeout"));

        float[] result = service.embed("Some headline", "Some summary.");

        assertThat(result).isNull();
    }

    @Test
    void embed_withNullHeadline_doesNotThrow() {
        ObjectProvider<EmbeddingModel> provider = mockProvider(embeddingModel);
        EntryEmbeddingService service = new EntryEmbeddingService(provider);

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});

        float[] result = service.embed(null, "Summary only.");

        assertThat(result).isNotNull();
    }

    @Test
    void embed_withNullSummary_doesNotThrow() {
        ObjectProvider<EmbeddingModel> provider = mockProvider(embeddingModel);
        EntryEmbeddingService service = new EntryEmbeddingService(provider);

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});

        float[] result = service.embed("Headline only.", null);

        assertThat(result).isNotNull();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ObjectProvider<EmbeddingModel> mockProvider(EmbeddingModel model) {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }
}
