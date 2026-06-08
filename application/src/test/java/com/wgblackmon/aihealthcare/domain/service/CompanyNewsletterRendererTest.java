package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompanyNewsletterRenderer} markdown generation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
class CompanyNewsletterRendererTest {

    private CompanyNewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new CompanyNewsletterRenderer();
    }

    @Test
    void renderMarkdown_emptyList_returnsNoCompaniesMessage() {
        String md = renderer.renderMarkdown(List.of());

        assertThat(md).contains("No AI healthcare companies discovered");
    }

    @Test
    void renderMarkdown_scribeCompany_rendersScribeSection() {
        Company company = new Company("ScribeBot", "YC Health Tech",
                "https://yc.com/scribebot", "https://scribebot.com",
                "AI medical scribe for hospitals.",
                new CompanyTags(true, false, false, false, false), true, true);

        String md = renderer.renderMarkdown(List.of(company));

        assertThat(md).contains("## New AI Scribes & Ambient Documentation");
        assertThat(md).contains("**ScribeBot**");
        assertThat(md).contains("[Website](https://scribebot.com)");
        assertThat(md).contains("[Profile](https://yc.com/scribebot)");
    }

    @Test
    void renderMarkdown_anchorsAppearLast() {
        Company scraped = new Company("NewScribe", "YC Health Tech",
                "https://yc.com/new", null, "New AI scribe for clinics.",
                new CompanyTags(true, false, false, false, false), true, true);
        Company anchor = new Company("OldScribe", "anchor",
                "https://oldscribe.com", "https://oldscribe.com",
                "Established AI scribe leader.",
                new CompanyTags(true, false, false, false, false), true, true);

        String md = renderer.renderMarkdown(List.of(anchor, scraped));

        int scrapedPos = md.indexOf("**NewScribe**");
        int anchorPos = md.indexOf("**OldScribe**");
        assertThat(scrapedPos).isLessThan(anchorPos);
        assertThat(md).contains("_Established players:_");
    }

    @Test
    void renderMarkdown_filtersNonAiHealth() {
        Company aiHealth = new Company("HealthAI", "test",
                null, null, "AI for healthcare.",
                new CompanyTags(false, true, false, false, false), true, true);
        Company aiOnly = new Company("PureAI", "test",
                null, null, "AI for finance.",
                CompanyTags.none(), true, false);

        String md = renderer.renderMarkdown(List.of(aiHealth, aiOnly));

        assertThat(md).contains("**HealthAI**");
        assertThat(md).doesNotContain("**PureAI**");
    }

    @Test
    void renderMarkdown_uncategorizedSection() {
        // isAI + isHealth but no specific tag
        Company company = new Company("GenericHealthAI", "test",
                null, null, "A generic health AI company.",
                CompanyTags.none(), true, true);

        String md = renderer.renderMarkdown(List.of(company));

        assertThat(md).contains("## Other AI Healthcare Companies");
        assertThat(md).contains("**GenericHealthAI**");
    }

    @Test
    void renderMarkdown_multipleSections() {
        Company scribe = new Company("ScribeCo", "test", null, null, "AI scribe.",
                new CompanyTags(true, false, false, false, false), true, true);
        Company imaging = new Company("ImageCo", "test", null, null, "Imaging AI.",
                new CompanyTags(false, false, true, false, false), true, true);

        String md = renderer.renderMarkdown(List.of(scribe, imaging));

        assertThat(md).contains("## New AI Scribes & Ambient Documentation");
        assertThat(md).contains("## Imaging & Diagnostics AI");
        assertThat(md).doesNotContain("## AI Agents & Virtual Care");
    }

    @Test
    void renderMarkdown_companyWithNoWebsite_showsProfileOnly() {
        Company company = new Company("NoSite", "YC Health Tech",
                "https://yc.com/nosite", null, "Healthcare AI startup.",
                new CompanyTags(false, true, false, false, false), true, true);

        String md = renderer.renderMarkdown(List.of(company));

        assertThat(md).contains("[Profile](https://yc.com/nosite)");
        assertThat(md).doesNotContain("[Website]");
    }
}
