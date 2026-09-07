package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thymeleaf controller that renders the vendor comparison UI at {@code GET /research/vendors}.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Vendor-select mode</b> — checkboxes select known competitors (derived from
 *       feed sources with tier {@code COMPETITOR}); articles are fetched directly per
 *       vendor topic, ensuring all selected vendors appear in results.</li>
 *   <li><b>Free-form mode</b> — a text query drives the COMBINED research pipeline
 *       (legacy behavior, retained for backward compatibility).</li>
 * </ul>
 *
 * <p>Vendor comparison runs are transient — no {@code ResearchRun} record is persisted.
 *
 * @author  Bill Blackmon
 * @version 2.1
 * @since   2026-05-14
 * @updated 2026-09-07
 */
@Slf4j
@Controller
@RequestMapping("/research/vendors")
public class VendorCompareController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;
    private static final int DEFAULT_MIN_VENDORS = 5;
    private static final int MAX_VENDORS_CAP     = 20;

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    private final CompareVendorsUseCase compareVendorsUseCase;
    private final List<VendorOption> availableVendors;

    /**
     * Constructs the controller and derives the available vendor checkbox options
     * from feed sources configured with tier {@code COMPETITOR}.
     *
     * @param compareVendorsUseCase Inbound port driving the vendor comparison pipeline.
     * @param feedSourceProperties  Feed configuration used to derive vendor checkbox options.
     */
    public VendorCompareController(CompareVendorsUseCase compareVendorsUseCase,
                                    FeedSourceProperties feedSourceProperties) {
        log.debug("VendorCompareController() | compareVendorsUseCase={}, feedSourceProperties={}",
                  compareVendorsUseCase.getClass().getSimpleName(),
                  feedSourceProperties.getClass().getSimpleName());
        this.compareVendorsUseCase = compareVendorsUseCase;
        this.availableVendors = buildVendorOptions(feedSourceProperties);
        log.debug("VendorCompareController() | availableVendors={}", availableVendors.size());
        log.debug("VendorCompareController() | return=void");
    }

    /**
     * Renders the vendor comparison page.
     *
     * <p>When no vendors are selected and no query is present, the page shows only the
     * form with checkboxes.  When vendors are selected via checkboxes, the vendor-select
     * pipeline runs.  When only a free-form query is entered (no checkboxes), the legacy
     * COMBINED pipeline runs.
     *
     * @param vendors    Selected vendor topic names from checkboxes; optional.
     * @param query      Free-form research question; optional (used when no vendors selected).
     * @param focusArea  Healthcare focus area to narrow the vendor comparison; optional.
     * @param maxSources Maximum sources per pipeline run; defaults to 20, capped at 100.
     * @param minVendors Minimum vendor sections to request; defaults to 5, capped at 20.
     * @param scoring    Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"} (default).
     * @param model      Thymeleaf model.
     * @return Thymeleaf view name {@code "vendor-compare"}.
     */
    @GetMapping
    public String compare(
            @RequestParam(required = false) List<String> vendors,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String focusArea,
            @RequestParam(defaultValue = "20") int maxSources,
            @RequestParam(defaultValue = "5") int minVendors,
            @RequestParam(defaultValue = "DOC_FREQUENCY") String scoring,
            Model model) {

        log.debug("compare() | vendors={}, query={}, focusArea={}, maxSources={}, minVendors={}, scoring={}",
                  vendors, query, focusArea, maxSources, minVendors, scoring);

        int cappedMax = Math.min(Math.max(maxSources, 1), MAX_SOURCES_CAP);
        int cappedVendors = Math.min(Math.max(minVendors, 1), MAX_VENDORS_CAP);
        model.addAttribute("query",            query != null ? query : "");
        model.addAttribute("focusArea",        focusArea != null ? focusArea : "");
        model.addAttribute("maxSources",       cappedMax);
        model.addAttribute("minVendors",       cappedVendors);
        model.addAttribute("scoring",          scoring);
        model.addAttribute("availableVendors", availableVendors);
        model.addAttribute("selectedVendors",  vendors != null ? vendors : Collections.emptyList());

        boolean hasVendorSelection = vendors != null && !vendors.isEmpty();
        boolean hasQuery = query != null && !query.isBlank();

        if (!hasVendorSelection && !hasQuery) {
            model.addAttribute("vendors",    Collections.emptyList());
            model.addAttribute("citations",  Collections.emptyList());
            model.addAttribute("hasResults", false);
            log.debug("compare() | no vendors or query — rendering empty form");
            log.debug("compare() | return=vendor-compare");
            return "vendor-compare";
        }

        List<VendorAssessment> vendorResults;
        List<SourceCitation> citations = Collections.emptyList();
        String errorMessage = null;

        try {
            if (hasVendorSelection) {
                // Vendor-select mode: fetch articles per vendor topic directly
                log.info("compare() | vendor-select mode: vendors={}, focusArea={}, query={}, scoring={}",
                         vendors, focusArea, query, scoring);
                VendorCompareResult result = compareVendorsUseCase.compareSelected(
                        vendors, focusArea, query, cappedMax, scoring);
                vendorResults = result.vendors();
                citations = result.citations();
            } else {
                // Free-form query mode: legacy COMBINED pipeline
                String trimmed = query.trim();
                log.info("compare() | free-form mode: query='{}', scoring={}", trimmed, scoring);
                VendorCompareResult result = compareVendorsUseCase.compare(
                        trimmed, cappedMax, cappedVendors, scoring);
                vendorResults = result.vendors();
                citations = result.citations();
            }
            log.info("compare() | vendor compare complete: vendorCount={}, citationCount={}",
                     vendorResults.size(), citations.size());
        } catch (Exception ex) {
            log.error("compare() | vendor comparison pipeline failed: {}", ex.getMessage());
            vendorResults = Collections.emptyList();
            errorMessage = "Vendor comparison pipeline error — check that ANTHROPIC_API_KEY is set. Detail: "
                           + ex.getMessage();
        }

        // Format citation timestamps, titles, and publications server-side
        Map<Integer, String> citationDates  = new HashMap<>();
        Map<Integer, String> citationTitles = new HashMap<>();
        Map<Integer, String> citationPubs   = new HashMap<>();
        for (SourceCitation c : citations) {
            if (c.retrievedAt() != null) {
                citationDates.put(c.citationNumber(), DISPLAY_FMT.format(c.retrievedAt()));
            } else {
                citationDates.put(c.citationNumber(), "—");
            }
            citationTitles.put(c.citationNumber(), formatTitle(c.title()));
            citationPubs.put(c.citationNumber(), extractPublication(c.url()));
        }

        // Sort citations newest-first by default
        List<SourceCitation> sortedCitations = new ArrayList<>(citations);
        sortedCitations.sort((a, b) -> {
            if (a.retrievedAt() == null && b.retrievedAt() == null) return 0;
            if (a.retrievedAt() == null) return 1;
            if (b.retrievedAt() == null) return -1;
            return b.retrievedAt().compareTo(a.retrievedAt());
        });

        model.addAttribute("vendors",        vendorResults);
        model.addAttribute("citations",      sortedCitations);
        model.addAttribute("citationDates",  citationDates);
        model.addAttribute("citationTitles", citationTitles);
        model.addAttribute("citationPubs",   citationPubs);
        model.addAttribute("hasResults",    !vendorResults.isEmpty());
        model.addAttribute("errorMessage",  errorMessage);

        log.debug("compare() | return=vendor-compare");
        return "vendor-compare";
    }

    /**
     * Reverses "domain.com — Article Title" to "Article Title - domain.com".
     * Handles both em-dash (—) and en-dash (–) separators.
     * Titles without a dash separator are returned unchanged.
     */
    private String formatTitle(String title) {
        log.debug("formatTitle() | title={}", title);
        if (title == null || title.isBlank()) {
            log.debug("formatTitle() | return=(empty)");
            return title;
        }

        // Try em-dash first, then en-dash, then " - "
        String[] separators = {" — ", " – ", " - "};
        for (String sep : separators) {
            int idx = title.indexOf(sep);
            if (idx > 0 && idx < title.length() - sep.length()) {
                String left  = title.substring(0, idx).trim();
                String right = title.substring(idx + sep.length()).trim();
                // Only reverse if the left part looks like a domain (contains a dot, no spaces)
                if (left.contains(".") && !left.contains(" ")) {
                    String result = right + " - " + left;
                    log.debug("formatTitle() | return={}", result);
                    return result;
                }
            }
        }

        log.debug("formatTitle() | return={} (unchanged)", title);
        return title;
    }

    /**
     * Extracts a human-readable publication name from a URL by cleaning the host domain.
     * E.g. "https://www.fiercehealthcare.com/article/123" → "fiercehealthcare.com".
     */
    private String extractPublication(String url) {
        log.debug("extractPublication() | url={}", url);
        if (url == null || url.isBlank()) {
            log.debug("extractPublication() | return=—");
            return "—";
        }
        try {
            String host = URI.create(url).getHost();
            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }
            String result = host != null ? host : "—";
            log.debug("extractPublication() | return={}", result);
            return result;
        } catch (Exception e) {
            log.debug("extractPublication() | return=— (parse error)");
            return "—";
        }
    }

    /**
     * Derives distinct vendor checkbox options from feed sources with tier COMPETITOR.
     * Each unique topic becomes a checkbox option with a display label extracted from the topic name.
     */
    private List<VendorOption> buildVendorOptions(FeedSourceProperties feedSourceProperties) {
        log.debug("buildVendorOptions() |");
        Set<String> seenTopics = new LinkedHashSet<>();
        List<VendorOption> options = new ArrayList<>();

        for (FeedSourceConfig config : feedSourceProperties.toFeedSourceConfigs()) {
            if (config.tier() == FeedSourceConfig.FeedTier.COMPETITOR) {
                String topic = config.effectiveTopic();
                if (seenTopics.add(topic)) {
                    String label = extractLabel(topic);
                    options.add(new VendorOption(topic, label));
                }
            }
        }

        log.debug("buildVendorOptions() | return={} vendor options", options.size());
        return List.copyOf(options);
    }

    /**
     * Extracts a user-friendly label from a feed topic name.
     * Maps feed topics to standard healthcare platform names.
     */
    private String extractLabel(String topicName) {
        log.debug("extractLabel() | topicName={}", topicName);
        String result;
        if (topicName.contains("Anthropic")) {
            result = "Claude for Healthcare";
        } else if (topicName.contains("OpenAI")) {
            result = "OpenAI for Healthcare";
        } else if (topicName.contains("Google")) {
            result = "Google for Health";
        } else if (topicName.contains("Amazon")) {
            result = "Amazon Health";
        } else if (topicName.contains("Perplexity")) {
            result = "Perplexity Health";
        } else if (topicName.contains("Microsoft")) {
            result = "Microsoft for Healthcare";
        } else {
            result = topicName;
        }
        log.debug("extractLabel() | return={}", result);
        return result;
    }

    /**
     * Immutable record representing a vendor checkbox option on the form.
     *
     * @param topicName Feed topic name used as the checkbox value.
     * @param label     User-friendly display label for the checkbox.
     */
    public record VendorOption(String topicName, String label) {}
}
