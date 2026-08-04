package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DailyBriefingData;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DailyBriefingRenderer}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class DailyBriefingRendererTest {

    private DailyBriefingRenderer renderer;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @BeforeEach
    void setUp() {
        renderer = new DailyBriefingRenderer();
    }

    @Test
    void renderHtml_allSectionsPresent_containsAllHeaders() {
        DailyBriefingData data = new DailyBriefingData("Test User", "test@example.com", TODAY,
                List.of(new WatchlistMatch("m1", "i1", "art-123", NOW, "AI article match")),
                List.of(buildSentiment("tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.6)),
                List.of(buildNote("n1", "COMPANY", "tempus-ai", "Tempus AI", "Great company")));

        String html = renderer.renderHtml(data);

        assertThat(html).contains("Your Watchlist Alerts");
        assertThat(html).contains("Sentiment Update");
        assertThat(html).contains("Your Recent Notes");
        assertThat(html).contains("Good morning, Test User");
    }

    @Test
    void renderHtml_noMatches_omitsWatchlistSection() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(),
                List.of(buildSentiment("co1", "Company 1", SentimentLabel.NEUTRAL, 0.0)),
                List.of());

        String html = renderer.renderHtml(data);

        assertThat(html).doesNotContain("Your Watchlist Alerts");
        assertThat(html).contains("Sentiment Update");
    }

    @Test
    void renderHtml_noSentiments_omitsSentimentSection() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(new WatchlistMatch("m1", "i1", "art-1", NOW, "snippet")),
                List.of(),
                List.of());

        String html = renderer.renderHtml(data);

        assertThat(html).contains("Your Watchlist Alerts");
        assertThat(html).doesNotContain("Sentiment Update");
    }

    @Test
    void renderHtml_noNotes_omitsNotesSection() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(),
                List.of(buildSentiment("co1", "Company 1", SentimentLabel.POSITIVE, 0.5)),
                List.of());

        String html = renderer.renderHtml(data);

        assertThat(html).doesNotContain("Your Recent Notes");
    }

    @Test
    void renderHtml_allEmpty_showsNoContentMessage() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(), List.of(), List.of());

        String html = renderer.renderHtml(data);

        assertThat(html).contains("No personalized content available today");
    }

    @Test
    void renderHtml_escapesHtmlInContent() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(),
                List.of(),
                List.of(buildNote("n1", "ARTICLE", "a1", "<script>alert('xss')</script>", "Note content")));

        String html = renderer.renderHtml(data);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void renderHtml_containsUnsubscribePlaceholder() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(), List.of(), List.of());

        String html = renderer.renderHtml(data);

        assertThat(html).contains("{{unsubscribe_url}}");
        assertThat(html).contains("{{preferences_url}}");
    }

    @Test
    void renderPlainText_allSectionsPresent_containsAllHeaders() {
        DailyBriefingData data = new DailyBriefingData("Test User", "test@example.com", TODAY,
                List.of(new WatchlistMatch("m1", "i1", "art-123", NOW, "Match snippet")),
                List.of(buildSentiment("co1", "Company 1", SentimentLabel.NEGATIVE, -0.4)),
                List.of(buildNote("n1", "WIKI_PAGE", "slug", "Wiki Page", "Note text")));

        String text = renderer.renderPlainText(data);

        assertThat(text).contains("YOUR WATCHLIST ALERTS");
        assertThat(text).contains("SENTIMENT UPDATE");
        assertThat(text).contains("YOUR RECENT NOTES");
        assertThat(text).contains("Good morning, Test User");
    }

    @Test
    void renderPlainText_noMatches_omitsWatchlistSection() {
        DailyBriefingData data = new DailyBriefingData("User", "test@example.com", TODAY,
                List.of(), List.of(), List.of());

        String text = renderer.renderPlainText(data);

        assertThat(text).doesNotContain("YOUR WATCHLIST ALERTS");
    }

    private CompanySentiment buildSentiment(String slug, String name, SentimentLabel label, double score) {
        return new CompanySentiment(slug, name, label, score, 10, 5, 2, 2, 1,
                "Risk summary for " + name + ". Second sentence.", List.of(), NOW);
    }

    private AnalystNote buildNote(String noteId, String type, String targetId,
                                   String targetLabel, String content) {
        return new AnalystNote(noteId, "test@example.com",
                NoteTargetType.valueOf(type), targetId, targetLabel, content, NOW, NOW);
    }
}
