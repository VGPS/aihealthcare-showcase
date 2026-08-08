package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain service that converts a list of {@link RetrievedSource} objects into a
 * deduplicated, sequentially numbered list of {@link SourceCitation} records.
 *
 * <p>Deduplication is URL-based: the first occurrence of each URL wins and the duplicate
 * is silently dropped.  Citation numbers are assigned in the order sources first appear,
 * starting at 1.
 *
 * <p>This class has no AI or persistence dependencies — it is safe to unit-test directly
 * without any mocks or Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
public class CitationAssembler {

    /**
     * Assemble a deduplicated, numbered citation list from the given sources.
     *
     * <p>Sources with a blank or {@code null} URL are retained and deduplicated by
     * source ID instead so that no source is silently lost.
     *
     * @param sources Raw sources from one or more retrieval calls; may be empty.
     * @return Ordered, numbered citations; empty when {@code sources} is empty.
     */
    public List<SourceCitation> assemble(List<RetrievedSource> sources) {
        log.debug("assemble() | sourceCount={}", sources == null ? 0 : sources.size());

        if (sources == null || sources.isEmpty()) {
            log.debug("assemble() | return=[] (no sources)");
            return new ArrayList<>();
        }

        // Deduplicate by URL (fall back to sourceId when URL is blank)
        Map<String, RetrievedSource> seen = new LinkedHashMap<>();
        for (RetrievedSource source : sources) {
            String key = (source.url() != null && !source.url().isBlank())
                    ? source.url()
                    : source.sourceId();
            if (!seen.containsKey(key)) {
                seen.put(key, source);
            } else {
                log.debug("assemble() | duplicate dropped: sourceId={}", source.sourceId());
            }
        }

        List<SourceCitation> result = new ArrayList<>();
        int number = 1;
        for (RetrievedSource source : seen.values()) {
            result.add(new SourceCitation(
                    number++,
                    source.title(),
                    source.url(),
                    source.retrievedAt()
            ));
        }

        log.debug("assemble() | return={} citations (deduped from {} sources)",
                  result.size(), sources.size());
        return result;
    }
}
