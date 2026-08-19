package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

/**
 * Outbound port for computing text embeddings for market digest entries.
 *
 * <p>The infrastructure adapter ({@code EntryEmbeddingService}) delegates to
 * Spring AI's {@code EmbeddingModel} (OpenAI text-embedding-3-small or similar).
 * Returns {@code null} when the embedding model is unavailable or the call fails,
 * so callers must null-check before using the result.
 *
 * <p>Used exclusively by the dedup step in
 * {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService}
 * to suppress re-notification of resurface stories within the 7-day rolling window.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface EntryEmbeddingPort {

    /**
     * Computes a vector embedding for the given headline and summary text.
     *
     * @param headline the entry headline (non-null)
     * @param summary  the entry summary (non-null)
     * @return a float array representing the embedding, or {@code null} if the
     *         embedding model is unavailable or the call fails
     */
    float[] embed(String headline, String summary);
}
