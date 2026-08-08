package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;

import java.util.List;

/**
 * Outbound port — summarize a group of articles into a {@link NewsletterSection}.
 *
 * <p>Implementations live in {@code infrastructure/ai} and wrap Spring AI's
 * {@code ChatClient}. Unit tests inject a mock implementation so no real AI calls
 * are made outside the {@code ai-integration} Spring profile.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-05-21
 */
public interface AiSummarizationPort {

    /**
     * Summarize a set of articles covering the same topic into a single newsletter section.
     *
     * @param articles  Source articles to summarize; must not be empty.
     * @param topic     The topic/keyword shared by all articles; used to focus the prompt.
     * @param tone      Desired writing tone for the generated content.
     * @param sectionId Identifier to assign to the returned section.
     * @return A {@link NewsletterSection} with an AI-generated headline and summary.
     */
    NewsletterSection summarize(
            List<NewsArticle> articles,
            String topic,
            NewsletterTone tone,
            String sectionId
    );

    /**
     * Summarize a set of articles using a caller-provided prompt template
     * instead of the default template.  Used by the prompt evaluation framework
     * to experiment with different prompt variants.
     *
     * @param articles     Source articles to summarize; must not be empty.
     * @param topic        The topic/keyword shared by all articles.
     * @param tone         Desired writing tone for the generated content.
     * @param sectionId    Identifier to assign to the returned section.
     * @param templateText Custom prompt template text with placeholders.
     * @return A {@link NewsletterSection} with an AI-generated headline and summary.
     */
    NewsletterSection summarizeWithTemplate(
            List<NewsArticle> articles,
            String topic,
            NewsletterTone tone,
            String sectionId,
            String templateText
    );

    /**
     * Summarize fresh articles for a topic, enriched with semantically similar past
     * articles retrieved from the vector store (retrieval-augmented generation).
     *
     * <p>The {@code contextArticles} list is used only to provide historical perspective
     * in the prompt — they are not treated as the primary source material and are not
     * included in the returned section's article ID list.
     *
     * @param articles        Fresh articles for this topic; must not be empty.
     * @param contextArticles Past articles retrieved via RAG; may be empty.
     * @param topic           The topic/keyword shared by the fresh articles.
     * @param tone            Desired writing tone for the generated content.
     * @param sectionId       Identifier to assign to the returned section.
     * @return A {@link NewsletterSection} whose headline and summary incorporate both
     *         fresh and historical context.
     */
    NewsletterSection summarizeWithContext(
            List<NewsArticle> articles,
            List<NewsArticle> contextArticles,
            String topic,
            NewsletterTone tone,
            String sectionId
    );

    /**
     * Generate an introductory paragraph for the full newsletter draft.
     *
     * @param sections The sections already generated for this draft.
     * @param tone     Desired writing tone.
     * @return A short, engaging introduction paragraph (typically 2–3 sentences).
     */
    String generateIntroduction(List<NewsletterSection> sections, NewsletterTone tone);

    /**
     * Generate a concise 3-sentence summary of the given articles for a topic
     * section on the news listing page.
     *
     * <p>Uses only article titles (not full body text) to keep token cost low.
     * The returned string is plain text — no headline, no bullet points.
     *
     * @param topic    the topic name these articles belong to
     * @param articles articles to summarize; should contain more than one entry
     * @return a 3-sentence plain-text summary
     */
    String generateTopicSummary(String topic, List<NewsArticle> articles);
}
