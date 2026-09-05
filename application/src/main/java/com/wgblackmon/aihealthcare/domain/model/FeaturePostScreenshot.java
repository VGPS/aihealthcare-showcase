package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record carrying the capture instructions for the screenshot
 * that accompanies a {@link FeaturePost}.
 *
 * These fields are guidance for the human taking the screenshot, not something
 * the application executes. They are part of the domain because they are part
 * of the post — a feature post without its shot list is not ready to publish.
 *
 * {@code redact} deserves particular care: several pages in this application
 * display live secrets (API keys on the developer portal, webhook endpoint
 * URLs with embedded tokens), and this field is what carries that warning
 * through to the person about to post the image publicly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public record FeaturePostScreenshot(String page,
                                    String prompt,
                                    String capture,
                                    String redact,
                                    String altText) {

    /**
     * Indicates whether this screenshot has a prompt to type before capturing.
     * Pages without an input field record the prompt as "None ...".
     *
     * @return true when a prompt must be entered first
     */
    public boolean hasPrompt() {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        return !prompt.trim().toLowerCase().startsWith("none");
    }

    /**
     * Indicates whether this screenshot carries a hard redaction requirement
     * rather than routine nav-bar tidying. Flagged entries are marked
     * "CRITICAL" in the library so the view can warn before the image is used.
     *
     * @return true when something on the page must be blurred before posting
     */
    public boolean hasCriticalRedaction() {
        if (redact == null) {
            return false;
        }
        return redact.contains("CRITICAL");
    }
}
