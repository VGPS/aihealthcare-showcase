package com.wgblackmon.aihealthcare.infrastructure.summary;

/**
 * Thrown by {@link ExtractionService} when the LLM response cannot be parsed into a
 * valid {@link SummaryExtraction} — e.g. missing headline, empty findings, or a
 * cited source ID that does not exist in the source list.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public class ExtractionValidationException extends RuntimeException {

    public ExtractionValidationException(String message) {
        super(message);
    }

    public ExtractionValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
