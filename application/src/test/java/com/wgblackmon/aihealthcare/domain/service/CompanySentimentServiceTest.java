package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SentimentAnalysisPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanySentimentService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class CompanySentimentServiceTest {

    private CompanyProfilePort companyProfilePort;
    private ArticleIngestionPort articleIngestionPort;
    private SentimentAnalysisPort sentimentAnalysisPort;
    private CompanySentimentPort companySentimentPort;
    private CompanySentimentService service;

    @BeforeEach
    void setUp() {
        companyProfilePort = mock(CompanyProfilePort.class);
        articleIngestionPort = mock(ArticleIngestionPort.class);
        sentimentAnalysisPort = mock(SentimentAnalysisPort.class);
        companySentimentPort = mock(CompanySentimentPort.class);
        service = new CompanySentimentService(
                companyProfilePort, articleIngestionPort,
                sentimentAnalysisPort, companySentimentPort);
    }

    @Test
    void analyzeAll_withValidProfiles_returnsSentiments() {
        CompanyProfile profile = buildProfile("tempus-ai", "Tempus AI", List.of("a1", "a2", "a3"));
        when(companyProfilePort.findAll()).thenReturn(List.of(profile));

        List<NewsArticle> articles = List.of(
                buildArticle("a1", "Tempus raises $100M"),
                buildArticle("a2", "Tempus launches new product"),
                buildArticle("a3", "Tempus partner with Mayo")
        );
        when(articleIngestionPort.fetchArticlesByIds(profile.articleIds())).thenReturn(articles);

        List<ArticleSentiment> sentiments = List.of(
                new ArticleSentiment("a1", "Tempus raises $100M", SentimentLabel.POSITIVE, 0.9, "Funding"),
                new ArticleSentiment("a2", "Tempus launches new product", SentimentLabel.POSITIVE, 0.8, "Launch"),
                new ArticleSentiment("a3", "Tempus partner with Mayo", SentimentLabel.NEUTRAL, 0.7, "Partnership")
        );
        when(sentimentAnalysisPort.analyzeSentiment(anyList(), eq("Tempus AI"))).thenReturn(sentiments);

        List<CompanySentiment> result = service.analyzeAll();

        assertThat(result).hasSize(1);
        CompanySentiment cs = result.get(0);
        assertThat(cs.companySlug()).isEqualTo("tempus-ai");
        assertThat(cs.positiveCount()).isEqualTo(2);
        assertThat(cs.neutralCount()).isEqualTo(1);
        assertThat(cs.sentimentScore()).isGreaterThan(0);
        verify(companySentimentPort).save(any(CompanySentiment.class));
    }

    @Test
    void analyzeAll_skipsProfilesWithFewerThanTwoArticles() {
        CompanyProfile profile = buildProfile("small-co", "Small Co", List.of("a1"));
        when(companyProfilePort.findAll()).thenReturn(List.of(profile));
        when(articleIngestionPort.fetchArticlesByIds(List.of("a1")))
                .thenReturn(List.of(buildArticle("a1", "Title")));

        List<CompanySentiment> result = service.analyzeAll();

        assertThat(result).isEmpty();
        verify(sentimentAnalysisPort, never()).analyzeSentiment(anyList(), any());
    }

    @Test
    void analyzeAll_skipsProfilesWithNoArticleIds() {
        CompanyProfile profile = buildProfile("empty-co", "Empty Co", List.of());
        when(companyProfilePort.findAll()).thenReturn(List.of(profile));

        List<CompanySentiment> result = service.analyzeAll();

        assertThat(result).isEmpty();
        verify(articleIngestionPort, never()).fetchArticlesByIds(anyList());
    }

    @Test
    void analyzeAll_skipsWhenLlmReturnsEmpty() {
        CompanyProfile profile = buildProfile("fail-co", "Fail Co", List.of("a1", "a2"));
        when(companyProfilePort.findAll()).thenReturn(List.of(profile));
        when(articleIngestionPort.fetchArticlesByIds(anyList()))
                .thenReturn(List.of(buildArticle("a1", "T1"), buildArticle("a2", "T2")));
        when(sentimentAnalysisPort.analyzeSentiment(anyList(), eq("Fail Co")))
                .thenReturn(List.of());

        List<CompanySentiment> result = service.analyzeAll();

        assertThat(result).isEmpty();
        verify(companySentimentPort, never()).save(any());
    }

    @Test
    void aggregate_allPositive_returnsPositiveLabel() {
        List<ArticleSentiment> sentiments = List.of(
                new ArticleSentiment("a1", "T1", SentimentLabel.POSITIVE, 0.9, "r1"),
                new ArticleSentiment("a2", "T2", SentimentLabel.POSITIVE, 0.8, "r2")
        );

        CompanySentiment result = service.aggregate("slug", "Name", sentiments);

        assertThat(result.overallSentiment()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(result.sentimentScore()).isEqualTo(1.0);
    }

    @Test
    void aggregate_allNegative_returnsNegativeLabel() {
        List<ArticleSentiment> sentiments = List.of(
                new ArticleSentiment("a1", "T1", SentimentLabel.NEGATIVE, 0.9, "r1"),
                new ArticleSentiment("a2", "T2", SentimentLabel.NEGATIVE, 0.8, "r2")
        );

        CompanySentiment result = service.aggregate("slug", "Name", sentiments);

        assertThat(result.overallSentiment()).isEqualTo(SentimentLabel.NEGATIVE);
        assertThat(result.sentimentScore()).isEqualTo(-1.0);
    }

    @Test
    void aggregate_mixedSignals_returnsMixedLabel() {
        List<ArticleSentiment> sentiments = List.of(
                new ArticleSentiment("a1", "T1", SentimentLabel.POSITIVE, 0.9, "r1"),
                new ArticleSentiment("a2", "T2", SentimentLabel.NEGATIVE, 0.8, "r2"),
                new ArticleSentiment("a3", "T3", SentimentLabel.NEUTRAL, 0.7, "r3"),
                new ArticleSentiment("a4", "T4", SentimentLabel.NEUTRAL, 0.6, "r4")
        );

        CompanySentiment result = service.aggregate("slug", "Name", sentiments);

        // score = (1 - 1) / 4 = 0.0 → NEUTRAL (within ±0.10 band)
        assertThat(result.overallSentiment()).isEqualTo(SentimentLabel.NEUTRAL);
    }

    @Test
    void aggregate_slightlyPositive_returnsMixedLabel() {
        List<ArticleSentiment> sentiments = List.of(
                new ArticleSentiment("a1", "T1", SentimentLabel.POSITIVE, 0.9, "r1"),
                new ArticleSentiment("a2", "T2", SentimentLabel.POSITIVE, 0.8, "r2"),
                new ArticleSentiment("a3", "T3", SentimentLabel.NEGATIVE, 0.7, "r3"),
                new ArticleSentiment("a4", "T4", SentimentLabel.NEUTRAL, 0.6, "r4"),
                new ArticleSentiment("a5", "T5", SentimentLabel.NEUTRAL, 0.5, "r5")
        );

        CompanySentiment result = service.aggregate("slug", "Name", sentiments);

        // score = (2 - 1) / 5 = 0.20 → MIXED (between 0.10 and 0.25)
        assertThat(result.overallSentiment()).isEqualTo(SentimentLabel.MIXED);
    }

    @Test
    void getBySlug_delegatesToPort() {
        CompanySentiment expected = new CompanySentiment(
                "test", "Test", SentimentLabel.NEUTRAL, 0, 0, 0, 0, 0, 0,
                "summary", List.of(), Instant.now());
        when(companySentimentPort.findBySlug("test")).thenReturn(Optional.of(expected));

        Optional<CompanySentiment> result = service.getBySlug("test");

        assertThat(result).isPresent();
        assertThat(result.get().companySlug()).isEqualTo("test");
    }

    @Test
    void getAll_delegatesToPort() {
        when(companySentimentPort.findAll()).thenReturn(List.of());

        List<CompanySentiment> result = service.getAll();

        assertThat(result).isEmpty();
        verify(companySentimentPort).findAll();
    }

    private CompanyProfile buildProfile(String slug, String name, List<String> articleIds) {
        return new CompanyProfile(slug, name, "https://example.com", "desc",
                List.of("ai"), articleIds, Instant.now(), Instant.now(),
                articleIds.size(), TrendDirection.STABLE);
    }

    private NewsArticle buildArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "body text", "topic", null, null, null, null, 0.5, null);
    }
}
