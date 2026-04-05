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
     * Generate an introductory paragraph for the full newsletter draft.
     *
     * @param sections The sections already generated for this draft.
     * @param tone     Desired writing tone.
     * @return A short, engaging introduction paragraph (typically 2–3 sentences).
     */
    String generateIntroduction(List<NewsletterSection> sections, NewsletterTone tone);
}
