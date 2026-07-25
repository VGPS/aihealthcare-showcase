package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TrendDetectionService}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class TrendDetectionServiceTest {

    private TrendDetectionService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new TrendDetectionService(2, 20);
        now = Instant.now();
    }

    @Test
    void emptyArticleList_returnsEmptySnapshot() {
        TrendSnapshot snapshot = service.detectTrends(List.of(), now);

        assertThat(snapshot.risingTopics()).isEmpty();
        assertThat(snapshot.totalKeywords()).isZero();
    }

    @Test
    void nullArticleList_returnsEmptySnapshot() {
        TrendSnapshot snapshot = service.detectTrends(null, now);

        assertThat(snapshot.risingTopics()).isEmpty();
        assertThat(snapshot.totalKeywords()).isZero();
    }

    @Test
    void risingKeyword_detectedWhenCurrentFrequencyExceedsPrevious() {
        List<NewsArticle> articles = new ArrayList<>();
        // "radiology" is a domain unigram — appears 5 times in last 30 days
        for (int i = 0; i < 5; i++) {
            articles.add(makeArticle("Radiology AI breakthrough " + i,
                    now.minus(i + 1, ChronoUnit.DAYS)));
        }
        // "radiology" appears 1 time in previous 60 days (31-90)
        articles.add(makeArticle("Radiology findings overview",
                now.minus(50, ChronoUnit.DAYS)));

        TrendSnapshot snapshot = service.detectTrends(articles, now);

        boolean foundRising = false;
        for (TrendSignal signal : snapshot.risingTopics()) {
            if (signal.keyword().equals("radiology")) {
                foundRising = true;
                assertThat(signal.direction()).isEqualTo(TrendDirection.RISING);
                assertThat(signal.current30d()).isGreaterThanOrEqualTo(5);
                break;
            }
        }
        assertThat(foundRising).isTrue();
    }

    @Test
    void stopwordsAreFiltered() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("The study of health and research data", now));

        assertThat(keywords).doesNotContain("the", "and", "study", "health", "research", "data");
    }

    @Test
    void bigramsWithDomainTermsAreExtracted() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("Radiology AI advancement", now));

        // "radiology" is a domain term, so "radiology ai" bigram is kept
        assertThat(keywords).contains("radiology");
    }

    @Test
    void shortTokensAreFiltered() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("An AI ML test of NLP", now));

        // "an" is 2 chars and a stopword, "ai" is 2 chars (< 3), "ml" is 2 chars
        assertThat(keywords).doesNotContain("an", "ml", "of");
    }

    @Test
    void articlesWithNullPublishedAt_areSkipped() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(makeArticle("Some telehealth keyword topic", null));

        TrendSnapshot snapshot = service.detectTrends(articles, now);

        assertThat(snapshot.totalKeywords()).isZero();
    }

    @Test
    void momentumCalculation_correctRatio() {
        // current=6, previous=3 -> currentRate = 6/30 = 0.2, previousRate = 3/60 = 0.05
        // momentum = 0.2/0.05 = 4.0
        double momentum = service.computeMomentum(6, 3);
        assertThat(momentum).isEqualTo(4.0);
    }

    @Test
    void momentumCalculation_zeroPrevious_returnsMaxValue() {
        double momentum = service.computeMomentum(5, 0);
        assertThat(momentum).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void momentumCalculation_zeroBoth_returnsZero() {
        double momentum = service.computeMomentum(0, 0);
        assertThat(momentum).isZero();
    }

    @Test
    void directionClassification_risingAboveThreshold() {
        TrendDirection dir = service.classifyDirection(10, 2, 2.0);
        assertThat(dir).isEqualTo(TrendDirection.RISING);
    }

    @Test
    void directionClassification_stableInMiddle() {
        TrendDirection dir = service.classifyDirection(5, 5, 1.0);
        assertThat(dir).isEqualTo(TrendDirection.STABLE);
    }

    @Test
    void risingTopicsAreLimitedAndSortedByMomentum() {
        // Use a service with risingLimit=2
        TrendDetectionService limited = new TrendDetectionService(2, 2);
        List<NewsArticle> articles = new ArrayList<>();

        // Use domain unigrams so they pass the filter
        String[] keywords = {"genomics", "proteomics", "oncology"};
        int[] currentCounts = {10, 5, 8};

        for (int k = 0; k < keywords.length; k++) {
            for (int i = 0; i < currentCounts[k]; i++) {
                articles.add(makeArticle(keywords[k] + " breakthrough " + i,
                        now.minus(i + 1, ChronoUnit.DAYS)));
            }
            // Each has 2 in previous window
            articles.add(makeArticle(keywords[k] + " earlier findings",
                    now.minus(40, ChronoUnit.DAYS)));
            articles.add(makeArticle(keywords[k] + " previous findings",
                    now.minus(50, ChronoUnit.DAYS)));
        }

        TrendSnapshot snapshot = limited.detectTrends(articles, now);

        assertThat(snapshot.risingTopics()).hasSizeLessThanOrEqualTo(2);
        // The top-momentum keywords should be first
        if (snapshot.risingTopics().size() >= 2) {
            assertThat(snapshot.risingTopics().get(0).momentum())
                    .isGreaterThanOrEqualTo(snapshot.risingTopics().get(1).momentum());
        }
    }

    @Test
    void minOccurrencesFilter_excludesRareKeywords() {
        // minOccurrences=3 — keyword must appear 3+ times in at least one window
        TrendDetectionService strict = new TrendDetectionService(3, 20);
        List<NewsArticle> articles = new ArrayList<>();

        // "nanomedicine" appears only 2 times — should be excluded
        articles.add(makeArticle("Nanomedicine pilot program",
                now.minus(5, ChronoUnit.DAYS)));
        articles.add(makeArticle("Nanomedicine trial update",
                now.minus(10, ChronoUnit.DAYS)));

        TrendSnapshot snapshot = strict.detectTrends(articles, now);

        boolean found = false;
        for (TrendSignal signal : snapshot.risingTopics()) {
            if (signal.keyword().equals("nanomedicine")) {
                found = true;
                break;
            }
        }
        assertThat(found).isFalse();
    }

    @Test
    void snapshotContainsTotalKeywordCount() {
        List<NewsArticle> articles = new ArrayList<>();
        // Use domain terms so keywords actually get extracted
        for (int i = 0; i < 3; i++) {
            articles.add(makeArticle("Wearable biosensor innovation " + i,
                    now.minus(i + 1, ChronoUnit.DAYS)));
        }

        TrendSnapshot snapshot = service.detectTrends(articles, now);

        assertThat(snapshot.totalKeywords()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // HTML entity cleaning tests
    // -------------------------------------------------------------------------

    @Test
    void htmlEntities_nbspStrippedFromText() {
        String cleaned = service.cleanText("AI&nbsp;healthcare&nbsp;report");
        assertThat(cleaned).doesNotContain("nbsp");
        assertThat(cleaned).contains("ai");
        assertThat(cleaned).contains("healthcare");
    }

    @Test
    void htmlEntities_numericEntitiesStripped() {
        String cleaned = service.cleanText("Clinical&#160;trial&#38;results");
        assertThat(cleaned).doesNotContain("160");
        assertThat(cleaned).doesNotContain("38");
    }

    @Test
    void htmlEntities_namedEntitiesStripped() {
        String cleaned = service.cleanText("FDA&rsquo;s &ldquo;guidance&rdquo; &mdash; summary");
        assertThat(cleaned).doesNotContain("rsquo");
        assertThat(cleaned).doesNotContain("ldquo");
        assertThat(cleaned).doesNotContain("mdash");
    }

    @Test
    void htmlTags_strippedCleanly() {
        String cleaned = service.cleanText("<div class=\"article\"><p>Telehealth</p></div>");
        assertThat(cleaned).doesNotContain("<");
        assertThat(cleaned).doesNotContain(">");
        assertThat(cleaned).contains("telehealth");
    }

    @Test
    void nbspKeyword_notExtractedFromArticles() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("FDA&nbsp;approves&nbsp;new&nbsp;AI&nbsp;diagnostic", now));

        assertThat(keywords).doesNotContain("nbsp");
        assertThat(keywords).doesNotContain("nbsp nbsp");
        // FDA is a domain term and should be extracted
        assertThat(keywords).contains("fda");
    }

    // -------------------------------------------------------------------------
    // Domain relevance filter tests
    // -------------------------------------------------------------------------

    @Test
    void domainUnigrams_keptWhenRecognized() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("Telehealth radiology genomics", now));

        assertThat(keywords).contains("telehealth", "radiology", "genomics");
    }

    @Test
    void genericUnigrams_filteredWhenNotDomain() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("Building improved access records performance", now));

        // These are all generic words not in domain unigrams
        assertThat(keywords).doesNotContain("building", "improved", "access",
                "records", "performance");
    }

    @Test
    void bigrams_keptWhenOneDomainToken() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("FDA regulation enforcement", now));

        // "fda" is a domain term, so "fda regulation" bigram should be kept
        assertThat(keywords).contains("fda");
        // bigram with fda + a non-stopword should also be present
        boolean hasFdaBigram = false;
        for (String kw : keywords) {
            if (kw.contains("fda") && kw.contains(" ")) {
                hasFdaBigram = true;
                break;
            }
        }
        assertThat(hasFdaBigram).isTrue();
    }

    @Test
    void bigrams_filteredWhenNoDomainToken() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("Important announcement regarding upcoming changes", now));

        // None of these words are domain terms, so no bigrams should be extracted
        assertThat(keywords).isEmpty();
    }

    @Test
    void trigrams_extractedWithDomainToken() {
        Set<String> keywords = service.extractKeywords(
                makeArticle("FDA approval pathway", now));

        boolean hasTrigram = false;
        for (String kw : keywords) {
            if (kw.split(" ").length == 3) {
                hasTrigram = true;
                break;
            }
        }
        // With only 3 non-stop words, there should be exactly one trigram
        assertThat(hasTrigram).isTrue();
    }

    // -------------------------------------------------------------------------
    // Integration-level: noise keywords should not appear
    // -------------------------------------------------------------------------

    @Test
    void noiseKeywords_doNotAppearInSnapshot() {
        List<NewsArticle> articles = new ArrayList<>();
        // Simulate real scraped articles with HTML noise
        for (int i = 0; i < 5; i++) {
            articles.add(makeArticleWithBody(
                    "AI Healthcare Update " + i,
                    "<div>&nbsp;&nbsp;Building improved access to records and "
                            + "performance.&nbsp;Share appeared together next.&nbsp;</div>",
                    now.minus(i + 1, ChronoUnit.DAYS)));
        }

        TrendSnapshot snapshot = service.detectTrends(articles, now);

        List<String> allKeywords = new ArrayList<>();
        for (TrendSignal signal : snapshot.risingTopics()) {
            allKeywords.add(signal.keyword());
        }

        assertThat(allKeywords).doesNotContain(
                "nbsp", "nbsp nbsp", "stat", "build", "building",
                "access", "records", "performance", "share", "appeared",
                "together", "next", "real"
        );
    }

    private NewsArticle makeArticle(String title, Instant publishedAt) {
        return new NewsArticle(
                "test-" + title.hashCode(),
                title,
                URI.create("https://example.com/" + title.hashCode()),
                null,
                "Test Topic",
                null,
                null,
                "Test Source",
                "INDUSTRY",
                0.5,
                publishedAt
        );
    }

    private NewsArticle makeArticleWithBody(String title, String body, Instant publishedAt) {
        return new NewsArticle(
                "test-" + title.hashCode(),
                title,
                URI.create("https://example.com/" + title.hashCode()),
                body,
                "Test Topic",
                null,
                null,
                "Test Source",
                "INDUSTRY",
                0.5,
                publishedAt
        );
    }
}
