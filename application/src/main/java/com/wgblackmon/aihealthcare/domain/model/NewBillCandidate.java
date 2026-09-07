package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * A newly discovered bill candidate surfaced by the Perplexity legislation
 * discovery pipeline.
 *
 * <p>Candidates require human review before promotion to the legislation
 * registry. Nothing is auto-written — an admin must explicitly promote
 * (creating a {@link StateLaw}) or dismiss the candidate. The
 * {@code promotedLawId} links to the resulting law if promoted.
 *
 * @param id            database-assigned identifier (nullable for new candidates)
 * @param stateCode     the state where the bill was introduced
 * @param billNumber    legislative bill number (e.g. "SB 1234")
 * @param title         short title or description of the bill
 * @param summary       LLM-generated summary of the bill's provisions (nullable)
 * @param sourceUrls    URLs where the bill was discovered
 * @param discoveredAt  when the candidate was first discovered
 * @param confidence    LLM confidence score for the discovery (0.0 to 1.0)
 * @param reviewed      whether an admin has reviewed this candidate
 * @param promotedLawId the id of the StateLaw created on promotion (nullable)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public record NewBillCandidate(
        Long id,
        StateCode stateCode,
        String billNumber,
        String title,
        String summary,
        List<String> sourceUrls,
        Instant discoveredAt,
        double confidence,
        boolean reviewed,
        String promotedLawId
) {

    /**
     * Compact constructor — validates required fields and defensively copies lists.
     */
    public NewBillCandidate {
        if (stateCode == null) {
            throw new IllegalArgumentException("stateCode must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        sourceUrls = sourceUrls != null ? List.copyOf(sourceUrls) : List.of();
    }
}
