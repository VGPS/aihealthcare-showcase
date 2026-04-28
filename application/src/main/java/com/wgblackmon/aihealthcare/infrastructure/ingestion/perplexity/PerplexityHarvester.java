package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Infrastructure stub for harvesting AI-in-Healthcare articles via the
 * Perplexity Sonar API.
 *
 * <p>At startup the adapter checks for a configured {@code PERPLEXITY_API_KEY}
 * environment variable.  When the key is absent the stub logs a warning and
 * returns an empty list from {@link #harvestArticles} — the rest of the pipeline
 * continues unaffected.  This allows the full Slice 6 wiring to be merged and
 * tested before an API key is obtained.
 *
 * <p>When a key is present the stub logs the prompt that <em>would</em> be sent
 * and returns an empty list.  Full Perplexity API integration (HTTP call,
 * response parsing, {@link NewsArticle} construction) is deferred to the slice
 * that follows key acquisition.
 *
 * <p>The harvest prompt is resolved at call time via {@link SearchPromptPort}
 * using the {@code "PERPLEXITY"} engine key.  If no active prompt is found the
 * method returns an empty list and logs a warning.
 *
 * <p>Planned API endpoint:
 * <pre>
 * POST https://api.perplexity.ai/chat/completions
 * model: sonar   (Perplexity's online search model)
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Slf4j
@Component
public class PerplexityHarvester {

    private final String apiKey;
    private final SearchPromptPort searchPromptPort;

    /**
     * Constructs the harvester with the Perplexity API key and search prompt port.
     *
     * @param apiKey           Value of {@code PERPLEXITY_API_KEY} env var; blank
     *                         when not configured.
     * @param searchPromptPort Port used to retrieve the active PERPLEXITY query
     *                         template at harvest time.
     */
    public PerplexityHarvester(
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            SearchPromptPort searchPromptPort) {
        log.debug("PerplexityHarvester() | apiKey={}, searchPromptPort={}",
                  apiKey.isBlank() ? "[not configured]" : "[present]",
                  searchPromptPort.getClass().getSimpleName());
        this.apiKey = apiKey;
        this.searchPromptPort = searchPromptPort;
        if (apiKey.isBlank()) {
            log.warn("PerplexityHarvester() | PERPLEXITY_API_KEY not set — harvester will return empty results");
        } else {
            log.info("PerplexityHarvester() | API key present — stub ready for full integration");
        }
        log.debug("PerplexityHarvester() | return=void");
    }

    /**
     * Harvests AI-in-Healthcare articles using the Perplexity Sonar API.
     *
     * <p>Returns an empty list in two cases:
     * <ul>
     *   <li>No API key is configured ({@code PERPLEXITY_API_KEY} is blank).</li>
     *   <li>No active {@code PERPLEXITY} search prompt is found in the database.</li>
     * </ul>
     *
     * <p>Full API integration (HTTP POST, structured response parsing, article
     * construction) is deferred until an API key is available.
     *
     * @param topic The healthcare topic to research (substituted into the prompt
     *              template as {@code {topic}}).
     * @return List of harvested {@link NewsArticle} objects, or an empty list
     *         when the harvester is not yet fully configured.
     */
    public List<NewsArticle> harvestArticles(String topic) {
        log.debug("harvestArticles() | topic={}", topic);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("harvestArticles() | PERPLEXITY_API_KEY not configured — skipping harvest");
            log.debug("harvestArticles() | return=[] (no API key)");
            return Collections.emptyList();
        }

        Optional<SearchPromptConfig> promptOpt = searchPromptPort.findByEngine("PERPLEXITY");
        if (promptOpt.isEmpty() || !promptOpt.get().active()) {
            log.warn("harvestArticles() | No active PERPLEXITY search prompt found — skipping harvest");
            log.debug("harvestArticles() | return=[] (no active prompt)");
            return Collections.emptyList();
        }

        String resolvedTopic = (topic != null && !topic.isBlank()) ? topic : "AI in Healthcare";
        String prompt = promptOpt.get().templateText().replace("{topic}", resolvedTopic);
        log.info("harvestArticles() | API key present; prompt built ({} chars) — "
                 + "full Perplexity Sonar API call deferred until integration slice",
                 prompt.length());

        // TODO: Implement full Perplexity Sonar API integration:
        //   POST https://api.perplexity.ai/chat/completions
        //   Body: { "model": "sonar", "messages": [{ "role": "user", "content": prompt }] }
        //   Authorization: Bearer {apiKey}
        //   Parse structured response → List<NewsArticle> with tier=PERPLEXITY

        log.debug("harvestArticles() | return=[] (API integration pending)");
        return Collections.emptyList();
    }
}
