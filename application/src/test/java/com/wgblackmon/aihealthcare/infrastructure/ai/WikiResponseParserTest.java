package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link WikiResponseParser}.
 *
 * <p>Tests parsing of structured LLM responses into domain records.
 * No Spring context or mocks needed — this is a stateless parser.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class WikiResponseParserTest {

    private final WikiResponseParser parser = new WikiResponseParser();
    private static final Instant NOW = Instant.parse("2026-07-04T12:00:00Z");

    @Test
    void parsePages_validResponse_returnsParsedPage() {
        String response = "### PAGE: fda-ai-guidance\n"
                + "TITLE: FDA AI Device Guidance\n"
                + "TYPE: ENTITY\n"
                + "TAGS: fda, regulation, ai-devices\n"
                + "STATUS: CREATED\n"
                + "SOURCES: article-001|article-002\n"
                + "RELATED: epic-ambient|google-health\n"
                + "CONTENT:\n"
                + "# FDA AI Guidance\n"
                + "The FDA has released new guidance on AI devices.\n";

        List<WikiPage> pages = parser.parsePages(response, NOW);

        assertThat(pages).hasSize(1);
        WikiPage page = pages.get(0);
        assertThat(page.slug()).isEqualTo("fda-ai-guidance");
        assertThat(page.title()).isEqualTo("FDA AI Device Guidance");
        assertThat(page.pageType().name()).isEqualTo("ENTITY");
        assertThat(page.tags()).containsExactly("fda", "regulation", "ai-devices");
        assertThat(page.sources()).hasSize(2);
        assertThat(page.relatedSlugs()).containsExactly("epic-ambient", "google-health");
        assertThat(page.contentMarkdown()).contains("FDA has released new guidance");
    }

    @Test
    void parsePages_multiplePages_returnsAll() {
        String response = "### PAGE: fda-ai-guidance\n"
                + "TITLE: FDA AI Guidance\n"
                + "TYPE: ENTITY\n"
                + "TAGS: fda\n"
                + "STATUS: CREATED\n"
                + "SOURCES: article-001\n"
                + "RELATED:\n"
                + "CONTENT:\n"
                + "Content A\n"
                + "\n"
                + "### PAGE: epic-ambient\n"
                + "TITLE: Epic Ambient AI\n"
                + "TYPE: ENTITY\n"
                + "TAGS: epic, ambient\n"
                + "STATUS: CREATED\n"
                + "SOURCES: article-002\n"
                + "RELATED: fda-ai-guidance\n"
                + "CONTENT:\n"
                + "Content B\n";

        List<WikiPage> pages = parser.parsePages(response, NOW);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).slug()).isEqualTo("fda-ai-guidance");
        assertThat(pages.get(1).slug()).isEqualTo("epic-ambient");
    }

    @Test
    void parsePages_emptyResponse_returnsEmpty() {
        List<WikiPage> pages = parser.parsePages("", NOW);

        assertThat(pages).isEmpty();
    }

    @Test
    void parsePages_nullResponse_returnsEmpty() {
        List<WikiPage> pages = parser.parsePages(null, NOW);

        assertThat(pages).isEmpty();
    }

    @Test
    void parsePages_malformedSection_skipsIt() {
        String response = "### PAGE: \n"
                + "TITLE: \n"
                + "TYPE: ENTITY\n";

        List<WikiPage> pages = parser.parsePages(response, NOW);

        assertThat(pages).isEmpty();
    }

    @Test
    void parseContradictions_validResponse_returnsParsed() {
        String response = "### CONTRADICTION: fda-ai-guidance\n"
                + "PRIOR_CLAIM: All AI devices require premarket review\n"
                + "NEW_CLAIM: Low-risk AI devices are now exempt from review\n"
                + "PRIOR_SOURCES: article-001\n"
                + "NEW_SOURCES: article-042\n";

        List<Contradiction> contradictions = parser.parseContradictions(response, NOW);

        assertThat(contradictions).hasSize(1);
        Contradiction c = contradictions.get(0);
        assertThat(c.pageSlug()).isEqualTo("fda-ai-guidance");
        assertThat(c.priorClaim()).isEqualTo("All AI devices require premarket review");
        assertThat(c.newClaim()).isEqualTo("Low-risk AI devices are now exempt from review");
        assertThat(c.priorSources()).hasSize(1);
        assertThat(c.newSources()).hasSize(1);
    }

    @Test
    void parseContradictions_emptyResponse_returnsEmpty() {
        List<Contradiction> contradictions = parser.parseContradictions("", NOW);

        assertThat(contradictions).isEmpty();
    }

    @Test
    void parseContradictions_malformedSection_skipsIt() {
        String response = "### CONTRADICTION: \n"
                + "PRIOR_CLAIM: \n"
                + "NEW_CLAIM: \n";

        List<Contradiction> contradictions = parser.parseContradictions(response, NOW);

        assertThat(contradictions).isEmpty();
    }

    @Test
    void parseWarnings_validResponse_returnsWarnings() {
        String response = "### WARNING: Skipped 2 articles with no body text\n"
                + "### WARNING: Article format unrecognized for article-099\n";

        List<String> warnings = parser.parseWarnings(response);

        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0)).contains("Skipped 2 articles");
        assertThat(warnings.get(1)).contains("article-099");
    }

    @Test
    void parseWarnings_noWarnings_returnsEmpty() {
        String response = "### PAGE: fda-ai-guidance\n"
                + "TITLE: FDA AI Guidance\n";

        List<String> warnings = parser.parseWarnings(response);

        assertThat(warnings).isEmpty();
    }

    @Test
    void parseMixedResponse_pagesAndContradictionsAndWarnings() {
        String response = "### PAGE: fda-ai-guidance\n"
                + "TITLE: FDA AI Guidance\n"
                + "TYPE: ENTITY\n"
                + "TAGS: fda\n"
                + "STATUS: CREATED\n"
                + "SOURCES: article-001\n"
                + "RELATED:\n"
                + "CONTENT:\n"
                + "Some content here.\n"
                + "\n"
                + "### CONTRADICTION: fda-ai-guidance\n"
                + "PRIOR_CLAIM: Prior claim text\n"
                + "NEW_CLAIM: New claim text\n"
                + "PRIOR_SOURCES: article-001\n"
                + "NEW_SOURCES: article-042\n"
                + "\n"
                + "### WARNING: Check source article-099\n";

        List<WikiPage> pages = parser.parsePages(response, NOW);
        List<Contradiction> contradictions = parser.parseContradictions(response, NOW);
        List<String> warnings = parser.parseWarnings(response);

        assertThat(pages).hasSize(1);
        assertThat(contradictions).hasSize(1);
        assertThat(warnings).hasSize(1);
    }
}
