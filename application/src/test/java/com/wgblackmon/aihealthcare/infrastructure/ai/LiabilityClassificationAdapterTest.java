package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI smoke test for {@link LiabilityClassificationAdapter}.
 *
 * <p>Requires {@code ai-integration} profile and a live Anthropic API key.
 * Verifies that the classifier returns a parseable response for a known
 * qualifying article and correctly returns empty for a non-incident article.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@ActiveProfiles("ai-integration")
class LiabilityClassificationAdapterTest {

    @Autowired
    private LiabilityClassificationAdapter adapter;

    @Test
    void classify_qualifyingArticle_returnsProposal() {
        NewsArticle article = new NewsArticle(
                "test-lt1-001",
                "Family sues OpenAI after ChatGPT medication advice caused wrongful death",
                URI.create("https://example.com/openai-wrongful-death"),
                "The family of John Smith filed a wrongful death lawsuit against OpenAI in the "
                + "Northern District of California after their father stopped his blood pressure "
                + "medication based on advice from ChatGPT. The complaint, filed on November 1 2026, "
                + "alleges product liability and negligence. Mr Smith died on October 15 2026.",
                "AI Healthcare Legal",
                null, null, null, "LEGAL", 0.9, null
        );

        Optional<LiabilityIncident> result = adapter.classify(article);

        assertThat(result).isPresent();
        assertThat(result.get().defendant()).isNotBlank();
        assertThat(result.get().incidentType()).isNotNull();
        assertThat(result.get().harmType()).isNotNull();
        assertThat(result.get().title()).isNotBlank();
    }

    @Test
    void classify_opinionPiece_returnsEmpty() {
        NewsArticle article = new NewsArticle(
                "test-lt1-002",
                "Commentary: AI bias in healthcare could lead to future lawsuits",
                URI.create("https://example.com/opinion-ai-bias"),
                "In this opinion piece, we explore the theoretical risks that AI diagnostic tools "
                + "may one day face legal scrutiny. While no specific lawsuits have been filed yet, "
                + "experts warn that the industry should prepare for increased regulatory attention.",
                "AI Healthcare Legal",
                null, null, null, "LEGAL", 0.8, null
        );

        Optional<LiabilityIncident> result = adapter.classify(article);

        assertThat(result).isEmpty();
    }
}
