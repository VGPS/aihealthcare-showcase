package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.ai;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter implementing {@link ImpactClassifierPort} via Claude.
 *
 * <p>Sends a structured prompt listing all unclassified {@link MarketDigestEntry}
 * objects to Claude and parses {@code ENTRY/FACT/RANK/dimension} blocks from
 * the response. Each entry is enriched with five {@link ImpactAssessment} records
 * and a {@link FactClassification}, then re-wrapped as a new {@link MarketDigestEntry}
 * with the original news item and companies preserved.
 *
 * <p>If the LLM call fails or an entry cannot be parsed, the original entry is
 * returned unchanged rather than propagating an exception — the pipeline degrades
 * gracefully with SPECULATIVE/rank-5/NEUTRAL defaults.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07
 */
@Slf4j
@Component
public class ClaudeImpactClassifierAdapter implements ImpactClassifierPort {

    private static final String PROMPT_FILE = "market-impact-classify.txt";

    private final ChatClient          chatClient;
    private final PromptLoaderService promptLoader;

    public ClaudeImpactClassifierAdapter(ChatClient.Builder chatClientBuilder,
                                          PromptLoaderService promptLoader) {
        log.debug("ClaudeImpactClassifierAdapter() | promptLoader={}",
                promptLoader.getClass().getSimpleName());
        this.chatClient   = chatClientBuilder.build();
        this.promptLoader = promptLoader;
        log.debug("ClaudeImpactClassifierAdapter() | return=void");
    }

    @Override
    public List<MarketDigestEntry> classify(List<MarketDigestEntry> entries) {
        log.debug("classify() | entryCount={}", entries.size());

        if (entries.isEmpty()) {
            log.debug("classify() | return=[] (empty input)");
            return new ArrayList<>();
        }

        String prompt = buildPrompt(entries);

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("classify() | LLM call failed: {} — returning entries with defaults", e.getMessage());
            List<MarketDigestEntry> result = applyDefaults(entries);
            log.debug("classify() | return={} entries (defaults)", result.size());
            return result;
        }

        if (response == null || response.isBlank()) {
            log.warn("classify() | empty LLM response — returning entries with defaults");
            List<MarketDigestEntry> result = applyDefaults(entries);
            log.debug("classify() | return={} entries (defaults)", result.size());
            return result;
        }

        List<MarketDigestEntry> result = parseResponse(response, entries);
        log.info("classify() | classified {} of {} entries successfully", result.size(), entries.size());
        log.debug("classify() | return={}", result);
        return result;
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    String buildPrompt(List<MarketDigestEntry> entries) {
        log.debug("buildPrompt() | entryCount={}", entries.size());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            MarketDigestEntry entry = entries.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("Category: ").append(entry.newsItem().category().name()).append(" | ");
            sb.append("Headline: ").append(entry.newsItem().headline()).append(" | ");
            sb.append("Summary: ").append(entry.newsItem().summary());
            if (entry.newsItem().dealSizeUsd() != null) {
                sb.append(" | Deal size: $").append(entry.newsItem().dealSizeUsd());
            }
            sb.append("\n");
        }

        String result = promptLoader.load(PROMPT_FILE).replace("{entries}", sb.toString().trim());
        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    List<MarketDigestEntry> parseResponse(String response, List<MarketDigestEntry> originals) {
        log.debug("parseResponse() | responseLength={}", response.length());

        // Initialize results with defaults so every original has a slot
        List<MarketDigestEntry> results = applyDefaults(originals);

        int currentIndex = -1;
        FactClassification currentFact = FactClassification.SPECULATIVE;
        int currentRank = 5;
        List<ImpactAssessment> currentAssessments = new ArrayList<>();
        List<AffectedCompany> currentCompanies = new ArrayList<>();

        for (String line : response.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("ENTRY: ")) {
                if (currentIndex >= 0 && currentIndex < originals.size()) {
                    results.set(currentIndex, enrichEntry(originals.get(currentIndex),
                            currentFact, currentRank, currentAssessments, currentCompanies));
                }
                try {
                    currentIndex = Integer.parseInt(trimmed.substring("ENTRY: ".length()).trim()) - 1;
                } catch (NumberFormatException e) {
                    log.warn("parseResponse() | bad ENTRY index: '{}'", trimmed);
                    currentIndex = -1;
                }
                currentFact        = FactClassification.SPECULATIVE;
                currentRank        = 5;
                currentAssessments = new ArrayList<>();
                currentCompanies   = new ArrayList<>();

            } else if (trimmed.startsWith("FACT: ")) {
                String factStr = trimmed.substring("FACT: ".length()).trim();
                try {
                    currentFact = FactClassification.valueOf(factStr);
                } catch (IllegalArgumentException e) {
                    log.warn("parseResponse() | unknown FACT value '{}' — defaulting to SPECULATIVE", factStr);
                }

            } else if (trimmed.startsWith("RANK: ")) {
                try {
                    currentRank = Integer.parseInt(trimmed.substring("RANK: ".length()).trim());
                    if (currentRank < 1 || currentRank > 5) currentRank = 5;
                } catch (NumberFormatException e) {
                    log.warn("parseResponse() | bad RANK '{}' — defaulting to 5", trimmed);
                }

            } else if (trimmed.startsWith("COMPANIES: ")) {
                currentCompanies = parseCompaniesLine(trimmed.substring("COMPANIES: ".length()).trim());

            } else {
                ImpactAssessment assessment = parseDimensionLine(trimmed);
                if (assessment != null) {
                    currentAssessments.add(assessment);
                }
            }
        }

        // Flush last entry
        if (currentIndex >= 0 && currentIndex < originals.size()) {
            results.set(currentIndex, enrichEntry(originals.get(currentIndex),
                    currentFact, currentRank, currentAssessments, currentCompanies));
        }

        log.debug("parseResponse() | return={} entries", results.size());
        return results;
    }

    private ImpactAssessment parseDimensionLine(String line) {
        ImpactDimension dimension = null;
        String rest = null;

        if (line.startsWith("REVENUE: ")) {
            dimension = ImpactDimension.REVENUE;
            rest = line.substring("REVENUE: ".length());
        } else if (line.startsWith("EARNINGS: ")) {
            dimension = ImpactDimension.EARNINGS;
            rest = line.substring("EARNINGS: ".length());
        } else if (line.startsWith("VALUATION: ")) {
            dimension = ImpactDimension.VALUATION;
            rest = line.substring("VALUATION: ".length());
        } else if (line.startsWith("INVESTOR_SENTIMENT: ")) {
            dimension = ImpactDimension.INVESTOR_SENTIMENT;
            rest = line.substring("INVESTOR_SENTIMENT: ".length());
        } else if (line.startsWith("FUTURE_GROWTH: ")) {
            dimension = ImpactDimension.FUTURE_GROWTH;
            rest = line.substring("FUTURE_GROWTH: ".length());
        }

        if (dimension == null || rest == null || !rest.contains("|")) {
            return null;
        }

        int pipe = rest.indexOf('|');
        String directionStr = rest.substring(0, pipe).trim();
        String rationale    = rest.substring(pipe + 1).trim();

        if (rationale.isBlank()) {
            rationale = "No rationale provided.";
        }

        ImpactDirection direction;
        try {
            direction = ImpactDirection.valueOf(directionStr);
        } catch (IllegalArgumentException e) {
            log.warn("parseDimensionLine() | unknown direction '{}' — defaulting to NEUTRAL", directionStr);
            direction = ImpactDirection.NEUTRAL;
        }

        return new ImpactAssessment(dimension, direction, rationale);
    }

    private MarketDigestEntry enrichEntry(MarketDigestEntry original,
                                          FactClassification fact,
                                          int rankValue,
                                          List<ImpactAssessment> assessments,
                                          List<AffectedCompany> companies) {
        MarketImpactRank rank;
        try {
            rank = new MarketImpactRank(rankValue);
        } catch (IllegalArgumentException e) {
            rank = new MarketImpactRank(5);
        }
        List<AffectedCompany> effectiveCompanies = companies.isEmpty()
                ? original.affectedCompanies() : companies;
        return new MarketDigestEntry(
                original.newsItem(),
                assessments,
                fact,
                rank,
                effectiveCompanies
        );
    }

    List<AffectedCompany> parseCompaniesLine(String value) {
        log.debug("parseCompaniesLine() | value={}", value);
        List<AffectedCompany> companies = new ArrayList<>();
        if (value == null || value.isBlank() || "NONE".equalsIgnoreCase(value.trim())) {
            log.debug("parseCompaniesLine() | return=[] (none)");
            return companies;
        }
        for (String segment : value.split(";")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\|");
            if (parts.length < 2) {
                log.warn("parseCompaniesLine() | skipping malformed segment: '{}'", trimmed);
                continue;
            }
            String name = parts[0].trim();
            String tickerOrPrivate = parts.length > 1 ? parts[1].trim() : "PRIVATE";
            String role = parts.length > 2 ? parts[2].trim() : "SUBJECT";
            String ticker = "PRIVATE".equalsIgnoreCase(tickerOrPrivate) ? null : tickerOrPrivate;
            companies.add(new AffectedCompany(name, ticker, role, null));
        }
        log.debug("parseCompaniesLine() | return={} companies", companies.size());
        return companies;
    }

    private List<MarketDigestEntry> applyDefaults(List<MarketDigestEntry> originals) {
        List<MarketDigestEntry> results = new ArrayList<>();
        for (MarketDigestEntry original : originals) {
            results.add(new MarketDigestEntry(
                    original.newsItem(),
                    new ArrayList<>(),
                    FactClassification.SPECULATIVE,
                    new MarketImpactRank(5),
                    original.affectedCompanies()
            ));
        }
        return results;
    }
}
