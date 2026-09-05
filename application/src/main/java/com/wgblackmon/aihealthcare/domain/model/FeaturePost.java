package com.wgblackmon.aihealthcare.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable domain record representing one ready-to-publish LinkedIn post
 * about a single AIHealthcare feature.
 *
 * Each post pairs an evergreen body — link-free, because LinkedIn suppresses
 * reach on posts carrying outbound links — with a separate first-comment block
 * holding the URLs, mirroring the posting workflow already used by the daily
 * article generator at {@code /dashboard/linkedin}.
 *
 * Two openings are carried. {@code hook} is evergreen and true without any
 * live data. {@code liveVariant} is a stronger opening that contains
 * {{PLACEHOLDER}} tokens the author fills from the screen on the day; it must
 * never be published with tokens unresolved, which is what
 * {@link #unresolvedTokens()} exists to detect.
 *
 * This record holds no rotation logic. Where a post sits in the calendar is
 * the concern of {@code FeaturePostRotationService}; this type only knows
 * which slot it was assigned.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public record FeaturePost(String id,
                          String feature,
                          String menuPath,
                          String url,
                          FeaturePostSlot slot,
                          String theme,
                          String hook,
                          String body,
                          String liveVariant,
                          String firstComment,
                          List<String> hashtags,
                          FeaturePostScreenshot screenshot) {

    /** LinkedIn's hard limit on post body length. */
    public static final int MAX_BODY_CHARS = 3000;

    public FeaturePost {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(body, "body must not be null");
        if (body.length() > MAX_BODY_CHARS) {
            throw new IllegalArgumentException(
                    "post body for '" + id + "' is " + body.length()
                            + " chars, exceeding LinkedIn's limit of " + MAX_BODY_CHARS);
        }
        hashtags = hashtags == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(hashtags));
    }

    /**
     * Returns the body's character count against LinkedIn's 3,000-character
     * limit, for display beside the copy button.
     *
     * @return the body length in characters
     */
    public int bodyLength() {
        return body.length();
    }

    /**
     * Collects every unresolved {{TOKEN}} remaining in the live variant.
     *
     * Meta tokens such as {{trial_url}} are substituted when the library is
     * loaded, so anything still present here is a value only the author can
     * supply — a company name, this week's count — and publishing with one
     * left in place would post a literal {{PLACEHOLDER}} to LinkedIn.
     *
     * @return the token names still awaiting a value, in the order found;
     *         empty when the live variant is ready to publish
     */
    public List<String> unresolvedTokens() {
        List<String> tokens = new ArrayList<>();
        if (liveVariant == null) {
            return tokens;
        }
        int cursor = 0;
        while (true) {
            int open = liveVariant.indexOf("{{", cursor);
            if (open < 0) {
                break;
            }
            int close = liveVariant.indexOf("}}", open + 2);
            if (close < 0) {
                break;
            }
            String token = liveVariant.substring(open + 2, close).trim();
            if (!token.isEmpty() && !tokens.contains(token)) {
                tokens.add(token);
            }
            cursor = close + 2;
        }
        return tokens;
    }

    /**
     * Indicates whether the live variant can be published as-is.
     *
     * @return true when no placeholder tokens remain
     */
    public boolean liveVariantReady() {
        return unresolvedTokens().isEmpty();
    }

    /**
     * Renders the hashtags as a single space-separated line for pasting.
     *
     * @return the hashtag line, or an empty string when there are none
     */
    public String hashtagLine() {
        StringBuilder line = new StringBuilder();
        for (String hashtag : hashtags) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(hashtag);
        }
        return line.toString();
    }
}
