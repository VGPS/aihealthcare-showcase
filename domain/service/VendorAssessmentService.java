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
 * @version 1.4
 * @since   2026-05-14
 * @updated 2026-07-07
 */
public class VendorAssessmentService {

    private static final Logger log = Logger.getLogger(VendorAssessmentService.class.getName());

    private static final double DEFAULT_RELEVANCE     = 0.5;
    private static final int    DEFAULT_MENTION_COUNT = 1;

    /** Scoring algorithm constant: document frequency (mentionCount / totalSources). */
    public static final String SCORING_DOC_FREQUENCY = "DOC_FREQUENCY";

    /** Scoring algorithm constant: TF-IDF (term frequency * inverse document frequency). */
    public static final String SCORING_TF_IDF = "TF_IDF";

    /** Intermediate parsed vendor data before scoring is applied. */
    private record ParsedVendor(String vendorName, List<String> strengths,
                                List<String> weaknesses, int mentionCount) {}

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
     * Vendor discovery is left to the AI (free-form mode).
     *
     * @param query      The original research question.
     * @param sources    Retrieved source documents; may be empty.
     * @param citations  Pre-assembled, deduplicated citation list.
     * @param minVendors Minimum number of vendor sections to request from the AI.
     * @param scoring    Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"} (default).
     * @return List of {@link VendorAssessment} records sorted by relevance descending;
     *         empty if sources are empty or no vendor sections are produced by the AI.
     */
    public List<VendorAssessment> assess(String query,
                                         List<RetrievedSource> sources,
                                         List<SourceCitation> citations,
                                         int minVendors,
                                         String scoring) {
        return assess(query, sources, citations, minVendors, scoring, Collections.emptyList());
    }

    /**
     * Assess vendors with optional pre-specified vendor names.
     *
     * <p>When {@code vendorNames} is non-empty, the AI prompt explicitly lists those vendors
     * and instructs the model to produce a section for each one, even if a vendor has limited
     * evidence in the sources.  This eliminates the vendor-discovery failure mode where
     * the AI omits vendors not prominently featured in retrieved articles.
     *
     * @param query       The original research question.
     * @param sources     Retrieved source documents; may be empty.
     * @param citations   Pre-assembled, deduplicated citation list.
     * @param minVendors  Minimum number of vendor sections to request from the AI.
     * @param scoring     Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"}.
     * @param vendorNames Pre-specified vendor names; empty list falls back to AI discovery.
     * @return List of {@link VendorAssessment} records sorted by relevance descending.
     */
    public List<VendorAssessment> assess(String query,
                                         List<RetrievedSource> sources,
                                         List<SourceCitation> citations,
                                         int minVendors,
                                         String scoring,
                                         List<String> vendorNames) {
        log.fine("assess() | query=" + query + ", sourceCount="
                 + (sources == null ? 0 : sources.size()) + ", minVendors=" + minVendors
                 + ", scoring=" + scoring + ", vendorNames=" + vendorNames);

        if (sources == null || sources.isEmpty()) {
            log.warning("assess() | No sources available — returning empty list");
            log.fine("assess() | return=[]");
            return Collections.emptyList();
        }

        int totalSources = sources.size();
        String sourceListing = buildSourceListing(sources, citations);

        // Build vendor list instruction for the prompt
        String vendorListInstruction = "";
        if (vendorNames != null && !vendorNames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\nYou MUST produce a section for EACH of these vendors: ");
            for (int i = 0; i < vendorNames.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(vendorNames.get(i));
            }
            sb.append(".\nEven if a vendor has limited evidence in the sources, include it with ");
            sb.append("whatever information is available. Do NOT omit any of these vendors.");
            vendorListInstruction = sb.toString();
        }

        String prompt = vendorCompareTemplate
                .replace("{query}", query)
                .replace("{sources}", sourceListing)
                .replace("{minVendors}", String.valueOf(minVendors))
                .replace("{totalSources}", String.valueOf(totalSources))
                .replace("{vendorList}", vendorListInstruction);

        log.info("assess() | sending vendor-compare prompt to AI (" + prompt.length() + " chars)");
        String aiResponse = aiReportPort.generate(prompt);
        log.info("assess() | AI vendor-compare response received (" + aiResponse.length() + " chars)");

        List<ParsedVendor> parsed = parseVendorData(aiResponse, totalSources);

        if (vendorNames != null && !vendorNames.isEmpty() && parsed.size() < vendorNames.size()) {
            log.warning("assess() | AI response produced " + parsed.size()
                        + " of " + vendorNames.size()
                        + " requested vendors — response may have been truncated");
        }

        boolean useTfIdf = SCORING_TF_IDF.equalsIgnoreCase(scoring);
        List<VendorAssessment> result = useTfIdf
                ? applyTfIdfScoring(parsed, totalSources)
                : applyDocFrequencyScoring(parsed, totalSources);

        // Sort by relevance score descending
        sortByRelevanceDescending(result);

        log.fine("assess() | return=" + result.size() + " vendors (scoring=" + scoring + ")");
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Build a numbered source listing for the vendor-compare prompt. */
    private String buildSourceListing(List<RetrievedSource> sources,
                                      List<SourceCitation> citations) {
        log.fine("buildSourceListing() | sourceCount=" + sources.size());

        // Scale excerpt length by source count to keep prompt size manageable
        int maxExcerpt = excerptLengthForSourceCount(sources.size());
        log.fine("buildSourceListing() | maxExcerpt=" + maxExcerpt + " chars (sourceCount=" + sources.size() + ")");

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
                  .append(snippet, 0, Math.min(snippet.length(), maxExcerpt))
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
                          ? source.snippet().substring(0, Math.min(source.snippet().length(), maxExcerpt))
                          : "")
                  .append("\n\n");
            }
        }

        String result = sb.toString().trim();
        log.fine("buildSourceListing() | return=" + result.length() + " chars");
        return result;
    }

    /**
     * Parse the AI response into intermediate {@link ParsedVendor} records by splitting on
     * {@code ##} Markdown headings and reading the structured field lines.
     *
     * @param response     Raw AI response text.
     * @param totalSources Total number of source documents evaluated.
     */
    private List<ParsedVendor> parseVendorData(String response, int totalSources) {
        log.fine("parseVendorData() | responseLength=" + (response == null ? 0 : response.length())
                 + ", totalSources=" + totalSources);

        List<ParsedVendor> result = new ArrayList<>();

        if (response == null || response.isBlank()) {
            log.fine("parseVendorData() | return=[] (empty response)");
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

            List<String> strengths    = new ArrayList<>();
            List<String> weaknesses   = new ArrayList<>();
            int          mentionCount = DEFAULT_MENTION_COUNT;

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
                } else if (trimmed.startsWith("MENTIONS:")) {
                    String raw = trimmed.substring("MENTIONS:".length()).trim();
                    try {
                        int parsed = Integer.parseInt(raw);
                        if (parsed >= 0) {
                            mentionCount = Math.min(parsed, totalSources);
                        }
                    } catch (NumberFormatException e) {
                        log.warning("parseVendorData() | Could not parse MENTIONS value '" + raw
                                    + "' for vendor '" + vendorName + "' — using default 1");
                    }
                }
                // ANALYSIS line is intentionally not stored in VendorAssessment
            }

            result.add(new ParsedVendor(vendorName, strengths, weaknesses, mentionCount));
        }

        log.fine("parseVendorData() | return=" + result.size() + " vendors");
        return result;
    }

    /**
     * Document Frequency scoring: {@code relevanceScore = mentionCount / totalSources}.
     */
    private List<VendorAssessment> applyDocFrequencyScoring(List<ParsedVendor> parsed,
                                                            int totalSources) {
        log.fine("applyDocFrequencyScoring() | vendors=" + parsed.size()
                 + ", totalSources=" + totalSources);

        List<VendorAssessment> result = new ArrayList<>();
        for (ParsedVendor pv : parsed) {
            double score = totalSources > 0
                    ? (double) pv.mentionCount() / totalSources
                    : DEFAULT_RELEVANCE;
            result.add(new VendorAssessment(pv.vendorName(), pv.strengths(), pv.weaknesses(),
                    score, pv.mentionCount(), totalSources));
        }

        log.fine("applyDocFrequencyScoring() | return=" + result.size() + " vendors");
        return result;
    }

    /**
     * TF-IDF scoring: {@code score = tf(v) * idf(v)}, normalized to [0.0, 1.0].
     *
     * <p>Term Frequency: {@code mentionCount / totalSources}<br>
     * Inverse Document Frequency: {@code log(1 + totalSources / mentionCount)}<br>
     * Final score is normalized by dividing by the maximum raw TF-IDF value across
     * all vendors, ensuring the top vendor scores 1.0.
     */
    private List<VendorAssessment> applyTfIdfScoring(List<ParsedVendor> parsed,
                                                     int totalSources) {
        log.fine("applyTfIdfScoring() | vendors=" + parsed.size()
                 + ", totalSources=" + totalSources);

        // Step 1: compute raw TF-IDF for each vendor
        double[] rawScores = new double[parsed.size()];
        double maxRaw = 0.0;
        for (int i = 0; i < parsed.size(); i++) {
            int mentions = parsed.get(i).mentionCount();
            double tf  = totalSources > 0 ? (double) mentions / totalSources : 0.0;
            double idf = mentions > 0 ? Math.log(1.0 + (double) totalSources / mentions) : 0.0;
            rawScores[i] = tf * idf;
            if (rawScores[i] > maxRaw) {
                maxRaw = rawScores[i];
            }
        }

        // Step 2: normalize to [0.0, 1.0]
        List<VendorAssessment> result = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            ParsedVendor pv = parsed.get(i);
            double normalized = maxRaw > 0.0 ? rawScores[i] / maxRaw : DEFAULT_RELEVANCE;
            result.add(new VendorAssessment(pv.vendorName(), pv.strengths(), pv.weaknesses(),
                    normalized, pv.mentionCount(), totalSources));
        }

        log.fine("applyTfIdfScoring() | return=" + result.size() + " vendors");
        return result;
    }

    /**
     * Returns the maximum excerpt length (in characters) based on the total number of
     * source documents.  When many sources are present, shorter excerpts keep the prompt
     * size manageable and leave room for the AI to produce all vendor sections.
     *
     * <ul>
     *   <li>&le; 20 sources &rarr; 500 chars</li>
     *   <li>21–50 sources &rarr; 300 chars</li>
     *   <li>&gt; 50 sources &rarr; 150 chars</li>
     * </ul>
     */
    private int excerptLengthForSourceCount(int sourceCount) {
        if (sourceCount <= 20) {
            return 500;
        } else if (sourceCount <= 50) {
            return 300;
        } else {
            return 150;
        }
    }

    /**
     * Sorts the list in-place by {@code relevanceScore} in descending order (highest first).
     */
    private void sortByRelevanceDescending(List<VendorAssessment> vendors) {
        log.fine("sortByRelevanceDescending() | size=" + vendors.size());

        int n = vendors.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (vendors.get(j).relevanceScore() < vendors.get(j + 1).relevanceScore()) {
                    VendorAssessment temp = vendors.get(j);
                    vendors.set(j, vendors.get(j + 1));
                    vendors.set(j + 1, temp);
                }
            }
        }

        log.fine("sortByRelevanceDescending() | return=void");
    }
}
