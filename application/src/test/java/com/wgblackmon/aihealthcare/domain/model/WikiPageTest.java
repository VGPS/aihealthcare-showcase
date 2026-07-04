package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link WikiPage} domain record.
 *
 * <p>Verifies compact constructor validation (slug format, required fields,
 * revision bounds) and defensive list copying.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class WikiPageTest {

    private static final String       SLUG     = "fda-ai-guidance";
    private static final String       TITLE    = "FDA AI Device Guidance";
    private static final WikiPageType TYPE     = WikiPageType.ENTITY;
    private static final String       CONTENT  = "# Overview\nThe FDA issued guidance...";
    private static final Instant      NOW      = Instant.parse("2026-07-04T00:00:00Z");
    private static final SourceRef    REF      = new SourceRef(
            "article-001", "FDA", LocalDate.of(2026, 7, 1), "premarket review required");

    private WikiPage validPage() {
        return new WikiPage(
                SLUG, TITLE, TYPE,
                List.of("fda", "regulation"),
                CONTENT,
                List.of(REF),
                List.of("ai-device-overview"),
                NOW, null, 1);
    }

    @Test
    void validPage_constructsSuccessfully() {
        WikiPage page = validPage();

        assertThat(page.slug()).isEqualTo(SLUG);
        assertThat(page.title()).isEqualTo(TITLE);
        assertThat(page.pageType()).isEqualTo(TYPE);
        assertThat(page.tags()).containsExactly("fda", "regulation");
        assertThat(page.contentMarkdown()).isEqualTo(CONTENT);
        assertThat(page.sources()).hasSize(1);
        assertThat(page.relatedSlugs()).containsExactly("ai-device-overview");
        assertThat(page.createdAt()).isEqualTo(NOW);
        assertThat(page.updatedAt()).isNull();
        assertThat(page.revision()).isEqualTo(1);
    }

    @Test
    void nullSlug_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                null, TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void uppercaseSlug_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                "FDA-Guidance", TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void slugWithSpaces_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                "fda ai guidance", TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void slugWithTrailingHyphen_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                "fda-", TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void singleWordSlug_isAllowed() {
        WikiPage page = new WikiPage(
                "fda", TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1);

        assertThat(page.slug()).isEqualTo("fda");
    }

    @Test
    void blankTitle_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, "  ", TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullPageType_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, TITLE, null, List.of(), CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageType");
    }

    @Test
    void nullTags_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, TITLE, TYPE, null, CONTENT, List.of(), List.of(), NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags");
    }

    @Test
    void nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), null, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void zeroRevision_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void negativeRevision_throwsIllegalArgument() {
        assertThatThrownBy(() -> new WikiPage(
                SLUG, TITLE, TYPE, List.of(), CONTENT, List.of(), List.of(), NOW, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void listsAreDefensivelyCopied() {
        ArrayList<String> mutableTags = new ArrayList<>();
        mutableTags.add("fda");

        WikiPage page = new WikiPage(
                SLUG, TITLE, TYPE, mutableTags, CONTENT, List.of(), List.of(), NOW, null, 1);

        mutableTags.add("should-not-appear");

        assertThat(page.tags()).containsExactly("fda");
    }
}
