package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when a prompt variant lookup fails because no variant exists for
 * the given identifier.
 *
 * <p>Maps to HTTP 404 Not Found in the web layer via
 * {@link com.wgblackmon.aihealthcare.web.controller.GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public class PromptVariantNotFoundException extends RuntimeException {

    private final String variantId;

    public PromptVariantNotFoundException(String variantId) {
        super("No prompt variant found for variantId: " + variantId);
        this.variantId = variantId;
    }

    public String getVariantId() {
        return variantId;
    }
}
