package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.embedding;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence.EntryEmbeddingCacheEntity;
import com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence.EntryEmbeddingCacheJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Spring AI–backed adapter implementing {@link EntryEmbeddingPort} with a database cache.
 *
 * <p>Delegates to the configured {@link EmbeddingModel} (OpenAI text-embedding-3-small
 * or equivalent) to produce a 1536-dimensional float vector for a given headline+summary
 * text. Computed embeddings are cached in the {@code entry_embedding_cache} table (keyed
 * by SHA-256 of the input text) so that the 7-day rolling dedup window in
 * {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService} can reuse
 * them without re-calling the embedding API.
 *
 * <p>If no {@code EmbeddingModel} bean is present in the application context (e.g. in
 * local development without an OpenAI API key) the service returns {@code null} from
 * {@link #embed} and the caller skips dedup, reverting to "notify all qualifying entries".
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07  added database-backed embedding cache
 */
@Slf4j
@Component
public class EntryEmbeddingService implements EntryEmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final EntryEmbeddingCacheJpaRepository cacheRepo;

    public EntryEmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                  ObjectProvider<EntryEmbeddingCacheJpaRepository> cacheRepoProvider) {
        log.debug("EntryEmbeddingService() | checking for EmbeddingModel and cache repo");
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.cacheRepo      = cacheRepoProvider.getIfAvailable();
        log.debug("EntryEmbeddingService() | embeddingModel={}, cacheRepo={}",
                embeddingModel != null ? embeddingModel.getClass().getSimpleName() : "NULL",
                cacheRepo != null ? "available" : "NULL");
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

        String text = buildText(headline, summary);
        String textHash = sha256(text);

        float[] cached = lookupCache(textHash);
        if (cached != null) {
            log.debug("embed() | cache hit for hash={}", textHash.substring(0, 8));
            return cached;
        }

        try {
            float[] result = embeddingModel.embed(text);
            storeCache(textHash, result);
            log.debug("embed() | return=float[{}]", result.length);
            return result;
        } catch (Exception e) {
            log.warn("embed() | embedding call failed: {} — returning null", e.getMessage());
            log.debug("embed() | return=null");
            return null;
        }
    }

    // ─── cache helpers ──────────────────────────────────────────────────────

    private float[] lookupCache(String textHash) {
        if (cacheRepo == null) {
            return null;
        }
        try {
            Optional<EntryEmbeddingCacheEntity> entity = cacheRepo.findById(textHash);
            if (entity.isPresent()) {
                return deserializeEmbedding(entity.get().getEmbeddingJson());
            }
        } catch (Exception e) {
            log.warn("lookupCache() | cache read failed: {}", e.getMessage());
        }
        return null;
    }

    private void storeCache(String textHash, float[] embedding) {
        if (cacheRepo == null || embedding == null) {
            return;
        }
        try {
            String json = serializeEmbedding(embedding);
            cacheRepo.save(new EntryEmbeddingCacheEntity(textHash, json));
        } catch (Exception e) {
            log.warn("storeCache() | cache write failed (non-fatal): {}", e.getMessage());
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

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    static String serializeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    static float[] deserializeEmbedding(String json) {
        String trimmed = json.substring(1, json.length() - 1);
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
