package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.DayOfWeek;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link YamlFeaturePostAdapter}.
 *
 * These run against a small fixture on the test classpath rather than the real
 * library, and against a resource loader constructed directly — no Spring
 * context is needed to exercise parsing, so none is started.
 *
 * The behaviours worth pinning down are the ones that would fail silently in
 * production: meta-token substitution, deliberate retention of author
 * placeholders, and skipping malformed entries instead of losing the library.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
class YamlFeaturePostAdapterTest {

    private static final String FIXTURE = "classpath:linkedin/test-feature-posts.yml";

    private YamlFeaturePostAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new YamlFeaturePostAdapter(new DefaultResourceLoader(), FIXTURE);
        adapter.load();
    }

    @Test
    @DisplayName("valid posts load and malformed entries are skipped")
    void loadsValidPostsAndSkipsMalformed() {
        List<FeaturePost> posts = adapter.findAll();

        assertEquals(3, posts.size(), "three good posts, three bad ones skipped");
        assertEquals("good-monday", posts.get(0).id());
        assertEquals("good-tuesday", posts.get(1).id());
        assertEquals("good-week-three", posts.get(2).id());
    }

    @Test
    @DisplayName("meta tokens are substituted throughout the post")
    void metaTokensAreSubstituted() {
        FeaturePost post = adapter.findAll().get(0);

        assertTrue(post.body().contains("https://example.test/register"));
        assertFalse(post.body().contains("{{trial_url}}"));
        assertTrue(post.firstComment().contains("https://example.test/dashboard/regulatory"));
        assertFalse(post.firstComment().contains("{{site_base_url}}"));
    }

    @Test
    @DisplayName("author placeholders survive substitution and are reported")
    void authorPlaceholdersSurvive() {
        FeaturePost post = adapter.findAll().get(0);

        // {{site_base_url}} resolved, {{N}} did not — it is the author's to fill
        assertTrue(post.liveVariant().contains("https://example.test"));
        List<String> tokens = post.unresolvedTokens();
        assertEquals(1, tokens.size());
        assertEquals("N", tokens.get(0));
        assertFalse(post.liveVariantReady());
    }

    @Test
    @DisplayName("a post with no placeholders is reported ready")
    void postWithoutPlaceholdersIsReady() {
        FeaturePost post = adapter.findAll().get(1);

        assertTrue(post.unresolvedTokens().isEmpty());
        assertTrue(post.liveVariantReady());
    }

    @Test
    @DisplayName("slots parse into week and weekday")
    void slotsParse() {
        FeaturePost monday = adapter.findAll().get(0);
        FeaturePost friday = adapter.findAll().get(2);

        assertEquals(1, monday.slot().week());
        assertEquals(DayOfWeek.MONDAY, monday.slot().weekday());
        assertEquals(3, friday.slot().week());
        assertEquals(DayOfWeek.FRIDAY, friday.slot().weekday());
        assertEquals("W1-MONDAY", monday.slot().key());
    }

    @Test
    @DisplayName("cycle length comes from the highest week present")
    void cycleLengthIsHighestWeek() {
        assertEquals(3, adapter.cycleWeeks());
    }

    @Test
    @DisplayName("screenshot spec parses, including prompt and redaction flags")
    void screenshotSpecParses() {
        FeaturePost withoutPrompt = adapter.findAll().get(0);
        FeaturePost withPrompt = adapter.findAll().get(1);

        assertFalse(withoutPrompt.screenshot().hasPrompt(),
                "'None — ...' is not a prompt to type");
        assertFalse(withoutPrompt.screenshot().hasCriticalRedaction());

        assertTrue(withPrompt.screenshot().hasPrompt());
        assertTrue(withPrompt.screenshot().hasCriticalRedaction(),
                "the CRITICAL marker must reach the view");
        assertNotNull(withPrompt.screenshot().altText());
    }

    @Test
    @DisplayName("hashtags parse, and an empty list is tolerated")
    void hashtagsParse() {
        FeaturePost twoTags = adapter.findAll().get(0);
        FeaturePost noTags = adapter.findAll().get(2);

        assertEquals(2, twoTags.hashtags().size());
        assertEquals("#HealthcareAI #FDA", twoTags.hashtagLine());
        assertTrue(noTags.hashtags().isEmpty());
        assertEquals("", noTags.hashtagLine());
    }

    @Test
    @DisplayName("a missing library yields an empty list rather than failing startup")
    void missingLibraryIsTolerated() {
        YamlFeaturePostAdapter missing = new YamlFeaturePostAdapter(
                new DefaultResourceLoader(), "classpath:linkedin/does-not-exist.yml");

        missing.load();

        assertTrue(missing.findAll().isEmpty());
        assertEquals(0, missing.cycleWeeks());
    }

    @Test
    @DisplayName("hashtag list is immutable once parsed")
    void hashtagsAreImmutable() {
        FeaturePost post = adapter.findAll().get(0);

        try {
            post.hashtags().add("#Injected");
            org.junit.jupiter.api.Assertions.fail(
                    "hashtags must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // the compact constructor wraps the list defensively
        }
    }
}
