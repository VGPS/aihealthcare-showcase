package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain service that detects inter-company relationships from recently
 * harvested articles using keyword pattern matching.
 *
 * <p>Scans article titles and body text for patterns like "X partners with Y",
 * "X acquires Y", "X integrates with Y" to build a company relationship graph.
 * No LLM dependency — uses deterministic keyword extraction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public class CompanyRelationshipService implements MapCompanyRelationshipsUseCase {

    private static final int SCAN_DAYS = 30;

    private static final String[][] PARTNERSHIP_PATTERNS = {
            {"partners with", "PARTNERSHIP"},
            {"partnership with", "PARTNERSHIP"},
            {"collaboration with", "PARTNERSHIP"},
            {"collaborates with", "PARTNERSHIP"},
            {"strategic alliance with", "PARTNERSHIP"},
            {"joint venture with", "PARTNERSHIP"},
            {"teamed up with", "PARTNERSHIP"},
            {"teams up with", "PARTNERSHIP"},
            {"signs deal with", "PARTNERSHIP"},
            {"agreement with", "PARTNERSHIP"}
    };

    private static final String[][] ACQUISITION_PATTERNS = {
            {"acquires", "ACQUISITION"},
            {"acquired", "ACQUISITION"},
            {"to acquire", "ACQUISITION"},
            {"acquisition of", "ACQUISITION"},
            {"takeover of", "ACQUISITION"},
            {"buyout of", "ACQUISITION"},
            {"buys", "ACQUISITION"},
            {"bought", "ACQUISITION"}
    };

    private static final String[][] INVESTMENT_PATTERNS = {
            {"invests in", "INVESTMENT"},
            {"investment in", "INVESTMENT"},
            {"leads round for", "INVESTMENT"},
            {"backs", "INVESTMENT"},
            {"funding for", "INVESTMENT"}
    };

    private static final String[][] INTEGRATION_PATTERNS = {
            {"integrates with", "INTEGRATION"},
            {"integration with", "INTEGRATION"},
            {"now supports", "INTEGRATION"},
            {"adds support for", "INTEGRATION"},
            {"connects with", "INTEGRATION"},
            {"launches on", "INTEGRATION"}
    };

    private final ArticleIngestionPort articleIngestionPort;
    private final CompanyRelationshipPort relationshipPort;

    public CompanyRelationshipService(ArticleIngestionPort articleIngestionPort,
                                       CompanyRelationshipPort relationshipPort) {
        this.articleIngestionPort = articleIngestionPort;
        this.relationshipPort = relationshipPort;
    }

    @Override
    public List<CompanyRelationship> detectRelationships() {
        List<NewsArticle> recentArticles = articleIngestionPort.fetchRecentArticles(SCAN_DAYS);
        List<CompanyRelationship> newRelationships = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();

        for (NewsArticle article : recentArticles) {
            String text = buildSearchText(article);
            List<CompanyRelationship> detected = extractRelationships(article, text, now);
            for (CompanyRelationship rel : detected) {
                String dedupKey = rel.sourceCompany() + "|" + rel.targetCompany() + "|" + rel.relationshipType().name();
                if (seen.contains(dedupKey)) {
                    continue;
                }
                if (!relationshipPort.existsBySourceAndTargetAndType(
                        rel.sourceCompany(), rel.targetCompany(), rel.relationshipType().name())) {
                    newRelationships.add(rel);
                    seen.add(dedupKey);
                }
            }
        }

        if (!newRelationships.isEmpty()) {
            relationshipPort.saveAll(newRelationships);
        }

        return newRelationships;
    }

    @Override
    public List<CompanyRelationship> getAllRelationships() {
        return relationshipPort.findAll();
    }

    @Override
    public List<CompanyRelationship> getRelationshipsForCompany(String companyName) {
        return relationshipPort.findByCompany(companyName);
    }

    private List<CompanyRelationship> extractRelationships(NewsArticle article,
                                                            String text, Instant now) {
        List<CompanyRelationship> results = new ArrayList<>();

        extractFromPatterns(article, text, PARTNERSHIP_PATTERNS, results, now);
        extractFromPatterns(article, text, ACQUISITION_PATTERNS, results, now);
        extractFromPatterns(article, text, INVESTMENT_PATTERNS, results, now);
        extractFromPatterns(article, text, INTEGRATION_PATTERNS, results, now);

        return results;
    }

    private void extractFromPatterns(NewsArticle article, String text,
                                     String[][] patterns,
                                     List<CompanyRelationship> results, Instant now) {
        for (String[] pattern : patterns) {
            String keyword = pattern[0];
            CompanyRelationshipType type = CompanyRelationshipType.valueOf(pattern[1]);

            int idx = text.indexOf(keyword);
            if (idx < 0) {
                continue;
            }

            String before = text.substring(0, idx).trim();
            String after = text.substring(idx + keyword.length()).trim();

            String sourceCompany = extractLastEntity(before);
            String targetCompany = extractFirstEntity(after);

            if (sourceCompany == null || targetCompany == null) {
                continue;
            }
            if (sourceCompany.equalsIgnoreCase(targetCompany)) {
                continue;
            }

            int hitCount = countPatternHits(text, patterns);
            double confidence = Math.min(1.0, 0.5 + (hitCount * 0.1));

            String summary = article.title();
            CompanyRelationship rel = new CompanyRelationship(
                    UUID.randomUUID().toString(),
                    sourceCompany,
                    targetCompany,
                    type,
                    article.url().toString(),
                    summary,
                    confidence,
                    now
            );
            results.add(rel);
            return;
        }
    }

    private int countPatternHits(String text, String[][] patterns) {
        int count = 0;
        for (String[] pattern : patterns) {
            if (text.contains(pattern[0])) {
                count++;
            }
        }
        return count;
    }

    private String extractLastEntity(String text) {
        if (text.isEmpty()) {
            return null;
        }
        String[] words = text.split("\\s+");
        if (words.length == 0) {
            return null;
        }

        List<String> entityWords = new ArrayList<>();
        for (int i = words.length - 1; i >= 0 && i >= words.length - 4; i--) {
            String word = words[i];
            String cleaned = word.replaceAll("[^a-zA-Z0-9']", "");
            if (cleaned.isEmpty()) {
                break;
            }
            if (isStopWord(cleaned.toLowerCase(Locale.ENGLISH))) {
                break;
            }
            entityWords.add(0, capitalizeFirst(cleaned));
        }
        if (entityWords.isEmpty()) {
            return null;
        }
        return String.join(" ", entityWords);
    }

    private String extractFirstEntity(String text) {
        if (text.isEmpty()) {
            return null;
        }
        String[] words = text.split("\\s+");
        if (words.length == 0) {
            return null;
        }

        List<String> entityWords = new ArrayList<>();
        for (int i = 0; i < words.length && i < 4; i++) {
            String word = words[i];
            String cleaned = word.replaceAll("[^a-zA-Z0-9']", "");
            if (cleaned.isEmpty()) {
                break;
            }
            if (isStopWord(cleaned.toLowerCase(Locale.ENGLISH))) {
                if (entityWords.isEmpty()) {
                    continue;
                }
                break;
            }
            entityWords.add(capitalizeFirst(cleaned));
        }
        if (entityWords.isEmpty()) {
            return null;
        }
        return String.join(" ", entityWords);
    }

    private boolean isStopWord(String word) {
        return "the".equals(word) || "a".equals(word) || "an".equals(word)
                || "in".equals(word) || "on".equals(word) || "at".equals(word)
                || "to".equals(word) || "for".equals(word) || "of".equals(word)
                || "and".equals(word) || "or".equals(word) || "by".equals(word)
                || "its".equals(word) || "their".equals(word) || "has".equals(word)
                || "have".equals(word) || "is".equals(word) || "was".equals(word)
                || "will".equals(word) || "that".equals(word) || "this".equals(word)
                || "with".equals(word) || "from".equals(word) || "as".equals(word);
    }

    private String capitalizeFirst(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return word.substring(0, 1).toUpperCase(Locale.ENGLISH) + word.substring(1);
    }

    private String buildSearchText(NewsArticle article) {
        StringBuilder sb = new StringBuilder();
        if (article.title() != null) {
            sb.append(article.title());
        }
        if (article.bodyText() != null && !article.bodyText().isBlank()) {
            sb.append(" ").append(article.bodyText());
        }
        return sb.toString().toLowerCase(Locale.ENGLISH);
    }
}
