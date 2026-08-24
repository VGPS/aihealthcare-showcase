package com.wgblackmon.aihealthcare.domain.service;

/**
 * Shared "Upgrade to Subscriber" / "Free 7 Day Demo" call-to-action buttons,
 * reused across every outbound customer-facing email.
 *
 * <p>Originated in {@link DigestNewsletterRenderer}'s free-tier digest footer.
 * Centralized here so the wording, links, and styling stay in sync across the
 * digest, the full "AI in Healthcare Weekly" newsletter ({@link NewsletterRenderer}),
 * the FREE-tier teaser ({@link NewsletterTeaserBuilder}), and transactional
 * emails (welcome, demo expiration, password reset).
 *
 * <p>{@link #BUTTONS_HTML} / {@link #BUTTONS_PLAIN_TEXT}: both upgrade and demo buttons.
 * {@link #UPGRADE_ONLY_HTML} / {@link #UPGRADE_ONLY_PLAIN_TEXT}: upgrade button only,
 * used in demo-expiration emails where offering another demo would be contradictory.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-17
 * @updated 2026-08-24
 */
public final class PromotionalFooter {

    private PromotionalFooter() {}

    public static final String BUTTONS_HTML =
            "<a href=\"https://app.bigskylabs.ai/pricing\" style=\"display:inline-block; background:#0066cc; color:white; "
            + "padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Upgrade to Subscriber &mdash; $19/mo</a>"
            + "<span style=\"display:inline-block; margin-left:12px;\">"
            + "<a href=\"https://app.bigskylabs.ai/demo\" style=\"display:inline-block; background:#28a745; color:white; "
            + "padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Free 7 Day Demo</a></span>"
            + "<div style=\"margin-top:10px; font-size:0.8em;\">"
            + "<a href=\"https://app.bigskylabs.ai/pricing#enterprise\" style=\"color:#7c3aed; text-decoration:underline;\">"
            + "Health system or investment team? See Enterprise plans &rarr;</a></div>";

    public static final String BUTTONS_PLAIN_TEXT =
            "Upgrade to Subscriber ($19/mo): https://app.bigskylabs.ai/pricing\n"
            + "Free 7-Day Demo: https://app.bigskylabs.ai/demo\n"
            + "Enterprise plans for health systems & investment teams: https://app.bigskylabs.ai/pricing#enterprise";

    /** Upgrade-only variant — no demo button. Used in demo-expiration email. */
    public static final String UPGRADE_ONLY_HTML =
            "<a href=\"https://app.bigskylabs.ai/pricing\" style=\"display:inline-block; background:#0066cc; color:white; "
            + "padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Upgrade to Subscriber &mdash; $19/mo</a>";

    /** Upgrade-only plain-text variant. Used in demo-expiration email. */
    public static final String UPGRADE_ONLY_PLAIN_TEXT =
            "Upgrade to Subscriber ($19/mo): https://app.bigskylabs.ai/pricing";
}
