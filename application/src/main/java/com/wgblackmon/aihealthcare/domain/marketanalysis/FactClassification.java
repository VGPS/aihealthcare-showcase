package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Classification of a news item's factual basis, as determined by the LLM impact classifier.
 *
 * <p>{@code CONFIRMED} sources include official press releases, SEC filings, regulatory
 * announcements, and earnings-call transcripts. {@code SPECULATIVE} sources include analyst
 * commentary, unconfirmed pacing claims, and "why it matters" editorial framing with no
 * primary-source citation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum FactClassification {

    /** Sourced from an official primary document (press release, SEC filing, agency announcement). */
    CONFIRMED,

    /** Based on analyst commentary, unconfirmed reports, or editorial framing without primary citation. */
    SPECULATIVE
}
