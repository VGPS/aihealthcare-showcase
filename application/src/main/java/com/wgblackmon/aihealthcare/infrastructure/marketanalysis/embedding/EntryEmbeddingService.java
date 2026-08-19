package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.embedding;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Spring AI–backed adapter implementing {@link EntryEmbeddingPort}.
 *
 * <p>Delegates to the configured {@link EmbeddingModel} (OpenAI text-embedding-3-small
 * or equivalent) to produce a 1536-dimensional float vector for a given headline+summary
 * text. Used exclusively by the 7-day rolling dedup step in
 * {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService}.
 *
 * <p>If no {@code EmbeddingModel} bean is present in the application context (e.g. in
 * local development without an OpenAI API key) the service returns {@code null} from
 * {@link #embed} and the caller skips dedup, reverting to "notify all qualifying entries".
 *
 * <p>Embedding API call failures are caught and logged at {@code WARN} level; the
 * method returns {@code null} so the caller can safely skip the entry rather than
 * propagate the failure.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class EntryEmbeddingService implements EntryEmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public EntryEmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        log.debug("EntryEmbeddingService() | checking for EmbeddingModel");
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        log.debug("EntryEmbeddingService() | embeddingModel={}",
                embeddingModel != null ? embeddingModel.getClass().getSimpleName() : "NULL (not configured)");
        log.debug("EntryEmbeddingService() | return=void");
    }

    @Override
    public float[] embed(String headline, String summary) {
        log.debug("embed() | headline.length={}", headline != null ? headline.length() : 0);

        if (embeddingModel == null) {
            log.warn("embed() | EmbeddingModel not available — returning null");
            log.debug("embed() | return=null");
            return null;
        }

        try {
            String text = buildText(headline, summary);
            float[] result = embeddingModel.embed(text);
            log.debug("embed() | return=float[{}]", result.length);
            return result;
        } catch (Exception e) {
            log.warn("embed() | embedding call failed: {} — returning null", e.getMessage());
            log.debug("embed() | return=null");
            return null;
        }
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private String buildText(String headline, String summary) {
        StringBuilder sb = new StringBuilder();
        if (headline != null && !headline.isBlank()) {
            sb.append(headline);
        }
        if (summary != null && !summary.isBlank()) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(summary);
        }
        return sb.toString();
    }
}
