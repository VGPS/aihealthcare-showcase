package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Domain service that converts a set of {@link RetrievedSource} documents into a
 * structured list of {@link VendorAssessment} records by calling the AI model with
 * the vendor-compare prompt template.
 *
 * <p>The prompt instructs the model to produce one {@code ## VendorName} section per
 * vendor found in the sources, each containing structured {@code STRENGTHS:},
 * {@code WEAKNESSES:}, {@code RELEVANCE:}, and {@code ANALYSIS:} lines.  This service
 * parses those sections into {@link VendorAssessment} records.
 *
 * <p>This is a pure domain service — no Spring or Lombok imports are used.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-14
 * @updated 2026-05-14
 */
public class VendorAssessmentService {

    private static final Logger log = Logger.getLogger(VendorAssessmentService.class.getName());

    private static final double DEFAULT_RELEVANCE = 0.5;

    private final AiReportPort aiReportPort;
    private final String       vendorCompareTemplate;

    /**
     * Constructs the service with its AI port and pre-loaded vendor-compare prompt template.
     *
     * @param aiReportPort           Port for sending prompts to the configured LLM.
     * @param vendorCompareTemplate  Template from {@code vendor-compare.txt}; must contain
     *                               {@code {query}} and {@code {sources}} placeholders.
     */
    public VendorAssessmentService(AiReportPort aiReportPort, String vendorCompareTemplate) {
        log.fine("VendorAssessmentService() | aiReportPort=" + aiReportPort.getClass().getSimpleName()
                 + ", templateLength=" + (vendorCompareTemplate == null ? 0 : vendorCompareTemplate.length()));
        this.aiReportPort          = aiReportPort;
        this.vendorCompareTemplate = vendorCompareTemplate;
        log.fine("VendorAssessmentService() | return=void");
    }

    /**
     * Assess vendors mentioned in the retrieved sources for the given query.
     *
     * @param query     The original research question.
     * @param sources   Retrieved source documents; may be empty.
     * @param citations Pre-assembled, deduplicated citation list.
     * @return Ordered list of {@link VendorAssessment} records; empty if sources are empty
     *         or no vendor sections are produced by the AI.
     */
    public List<VendorAssessment> assess(String query,
                                         List<RetrievedSource> sources,
                                         List<SourceCitation> citations) {
        log.fine("assess() | query=" + query + ", sourceCount="
                 + (sources == null ? 0 : sources.size()));

        if (sources == null || sources.isEmpty()) {
            log.warning("assess() | No sources available — returning empty list");
            log.fine("assess() | return=[]");
            return Collections.emptyList();
        }

        String sourceListing = buildSourceListing(sources, citations);
        String prompt = vendorCompareTemplate
                .replace("{query}", query)
                .replace("{sources}", sourceListing);

        log.info("assess() | sending vendor-compare prompt to AI (" + prompt.length() + " chars)");
        String aiResponse = aiReportPort.generate(prompt);
        log.info("assess() | AI vendor-compare response received (" + aiResponse.length() + " chars)");

        List<VendorAssessment> result = parseVendors(aiResponse);

        log.fine("assess() | return=" + result.size() + " vendors");
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Build a numbered source listing for the vendor-compare prompt. */
    private String buildSourceListing(List<RetrievedSource> sources,
                                      List<SourceCitation> citations) {
        log.fine("buildSourceListing() | sourceCount=" + sources.size());

        StringBuilder sb = new StringBuilder();
        if (citations != null && !citations.isEmpty()) {
            for (SourceCitation citation : citations) {
                String snippet = "";
                for (RetrievedSource source : sources) {
                    String key = (source.url() != null && !source.url().isBlank())
                            ? source.url() : source.sourceId();
                    if (key.equals(citation.url()) || source.sourceId().equals(citation.url())) {
                        snippet = source.snippet() != null ? source.snippet() : "";
                        break;
                    }
                }
                sb.append("[").append(citation.citationNumber()).append("] ")
                  .append(citation.title()).append("\n")
                  .append("URL: ").append(citation.url()).append("\n")
                  .append("Excerpt: ")
                  .append(snippet, 0, Math.min(snippet.length(), 500))
                  .append("\n\n");
            }
        } else {
            int idx = 1;
            for (RetrievedSource source : sources) {
                sb.append("[").append(idx++).append("] ")
                  .append(source.title() != null ? source.title() : "(no title)").append("\n")
                  .append("URL: ").append(source.url() != null ? source.url() : "").append("\n")
                  .append("Excerpt: ")
                  .append(source.snippet() != null
                          ? source.snippet().substring(0, Math.min(source.snippet().length(), 500))
                          : "")
                  .append("\n\n");
            }
        }

        String result = sb.toString().trim();
        log.fine("buildSourceListing() | return=" + result.length() + " chars");
        return result;
    }

    /**
     * Parse the AI response into {@link VendorAssessment} records by splitting on
     * {@code ##} Markdown headings and reading the structured field lines.
     */
    private List<VendorAssessment> parseVendors(String response) {
        log.fine("parseVendors() | responseLength=" + (response == null ? 0 : response.length()));

        List<VendorAssessment> result = new ArrayList<>();

        if (response == null || response.isBlank()) {
            log.fine("parseVendors() | return=[] (empty response)");
            return result;
        }

        // Split on lines that begin with ## (Markdown heading level 2)
        String[] parts = response.split("(?m)^##\\s+");

        // parts[0] is pre-heading preamble — skip it
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isBlank()) {
                continue;
            }

            int firstNewline = part.indexOf('\n');
            if (firstNewline < 0) {
                continue; // heading only, no body — skip
            }

            String vendorName = part.substring(0, firstNewline).trim();
            if (vendorName.isBlank()) {
                continue;
            }

            String body = part.substring(firstNewline).trim();
            String[] lines = body.split("\n");

            List<String> strengths  = new ArrayList<>();
            List<String> weaknesses = new ArrayList<>();
            double relevanceScore   = DEFAULT_RELEVANCE;

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("STRENGTHS:")) {
                    String raw = trimmed.substring("STRENGTHS:".length()).trim();
                    for (String s : raw.split(";")) {
                        String item = s.trim();
                        if (!item.isBlank()) {
                            strengths.add(item);
                        }
                    }
                } else if (trimmed.startsWith("WEAKNESSES:")) {
                    String raw = trimmed.substring("WEAKNESSES:".length()).trim();
                    for (String w : raw.split(";")) {
                        String item = w.trim();
                        if (!item.isBlank()) {
                            weaknesses.add(item);
                        }
                    }
                } else if (trimmed.startsWith("RELEVANCE:")) {
                    String raw = trimmed.substring("RELEVANCE:".length()).trim();
                    try {
                        double parsed = Double.parseDouble(raw);
                        if (parsed >= 0.0 && parsed <= 1.0) {
                            relevanceScore = parsed;
                        }
                    } catch (NumberFormatException e) {
                        log.warning("parseVendors() | Could not parse RELEVANCE value '" + raw
                                    + "' for vendor '" + vendorName + "' — using default 0.5");
                    }
                }
                // ANALYSIS line is intentionally not stored in VendorAssessment
            }

            result.add(new VendorAssessment(vendorName, strengths, weaknesses, relevanceScore));
        }

        log.fine("parseVendors() | return=" + result.size() + " vendors");
        return result;
    }
}
