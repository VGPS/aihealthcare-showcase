package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link NewsletterSection} domain record.
 *
 * <p>Verifies field validation in the compact constructor, including the new
 * {@link SectionType} field, and that the defensive copy of {@code articleIds}
 * prevents external mutation after construction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
class NewsletterSectionTest {

    private static final String       SECTION_ID   = "section-001";
    private static final SectionType  SECTION_TYPE = SectionType.WHAT_SHIPPED;
    private static final String       TOPIC        = "AI diagnostics";
    private static final String       HEADLINE     = "AI Outperforms Radiologists in New Study";
    private static final String       SUMMARY      = "A groundbreaking study shows AI improving diagnostic rates.";
    private static final List<String> ARTICLE_IDS  = List.of("article-001", "article-002");

    @Test
    void validSection_constructsSuccessfully() {
        NewsletterSection section = new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, HEADLINE, SUMMARY, ARTICLE_IDS);

        assertThat(section.sectionId()).isEqualTo(SECTION_ID);
        assertThat(section.sectionType()).isEqualTo(SECTION_TYPE);
        assertThat(section.topic()).isEqualTo(TOPIC);
        assertThat(section.headline()).isEqualTo(HEADLINE);
        assertThat(section.summary()).isEqualTo(SUMMARY);
        assertThat(section.articleIds()).containsExactlyElementsOf(ARTICLE_IDS);
    }

    @Test
    void eachSectionType_constructsSuccessfully() {
        for (SectionType type : SectionType.values()) {
            NewsletterSection section = new NewsletterSection(
                    SECTION_ID, type, TOPIC, HEADLINE, SUMMARY, ARTICLE_IDS);
            assertThat(section.sectionType()).isEqualTo(type);
        }
    }

    @Test
    void articleIds_areDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(ARTICLE_IDS);
        NewsletterSection section = new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, HEADLINE, SUMMARY, mutable);

        mutable.add("article-999");

        assertThat(section.articleIds()).hasSize(ARTICLE_IDS.size());
    }

    @Test
    void blankSectionId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                "", SECTION_TYPE, TOPIC, HEADLINE, SUMMARY, ARTICLE_IDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sectionId");
    }

    @Test
    void nullSectionType_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, null, TOPIC, HEADLINE, SUMMARY, ARTICLE_IDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sectionType");
    }

    @Test
    void blankTopic_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, SECTION_TYPE, "  ", HEADLINE, SUMMARY, ARTICLE_IDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void blankHeadline_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, "", SUMMARY, ARTICLE_IDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headline");
    }

    @Test
    void blankSummary_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, HEADLINE, "  ", ARTICLE_IDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void emptyArticleIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, HEADLINE, SUMMARY, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleIds");
    }

    @Test
    void nullArticleIds_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterSection(
                SECTION_ID, SECTION_TYPE, TOPIC, HEADLINE, SUMMARY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("articleIds");
    }
}
