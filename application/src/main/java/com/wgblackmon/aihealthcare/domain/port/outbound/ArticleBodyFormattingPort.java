package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port — formats raw article body text with entity bolding for
 * newsletter display.
 *
 * <p>Implementations call an LLM to rewrite the body as clean paragraphs
 * and wrap key entities (company names, drug names, dollar amounts, regulatory
 * designations) in {@code **bold**} markers. Callers convert those markers to
 * HTML {@code <strong>} tags before embedding in the email layout.
 *
 * <p>Implementations must be tolerant of failures — callers fall back to
 * displaying raw body text if this port returns an empty string or throws.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public interface ArticleBodyFormattingPort {

    /**
     * Formats article body text with entity bolding.
     *
     * @param title    article headline, used as LLM context
     * @param bodyText raw article body; must not be blank
     * @return formatted text with {@code **entity**} markers; empty string if unavailable
     */
    String formatWithEntityBolding(String title, String bodyText);
}
