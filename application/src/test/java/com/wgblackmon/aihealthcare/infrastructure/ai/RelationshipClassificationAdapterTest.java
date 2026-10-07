package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelationshipClassificationAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationshipClassificationAdapterTest {

    @Mock
    private PromptLoaderService promptLoaderService;

    private RelationshipClassificationAdapter adapter;

    @BeforeEach
    void setUp() {
        when(promptLoaderService.load("relationship-classification.txt"))
                .thenReturn("Classify these articles:\n{numberedArticleList}");
        adapter = new TestRelationshipClassificationAdapter(promptLoaderService);
    }

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                body, "Test", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    @Test
    void buildPrompt_insertsArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Google Health partners with Mayo Clinic", "Partnership details"));
        articles.add(article("a2", "Oracle acquires Cerner", "Acquisition news"));

        String prompt = adapter.buildPrompt(articles);

        assertThat(prompt).contains("[1] Google Health partners with Mayo Clinic");
        assertThat(prompt).contains("[2] Oracle acquires Cerner");
        assertThat(prompt).contains("Classify these articles:");
    }

    @Test
    void buildPrompt_truncatesLongBody() {
        String longBody = "x".repeat(600);
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Title", longBody));

        String prompt = adapter.buildPrompt(articles);

        assertThat(prompt).contains("...");
        assertThat(prompt).doesNotContain("x".repeat(600));
    }

    @Test
    void parseResponse_validPartnership_returnsRelationship() {
        String response = "RELATIONSHIP_RESULTS:\n"
                + "[1] TYPE: PARTNERSHIP | SOURCE: Google Health | TARGET: Mayo Clinic | "
                + "CONFIDENCE: 0.88 | SUMMARY: Strategic partnership for AI diagnostics.";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Google Health partners with Mayo Clinic", "details"));

        List<CompanyRelationship> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relationshipType()).isEqualTo(CompanyRelationshipType.PARTNERSHIP);
        assertThat(result.get(0).sourceCompany()).isEqualTo("Google Health");
        assertThat(result.get(0).targetCompany()).isEqualTo("Mayo Clinic");
        assertThat(result.get(0).confidence()).isCloseTo(0.88, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void parseResponse_noRelationship_skipsArticle() {
        String response = "RELATIONSHIP_RESULTS:\n"
                + "[1] NO_RELATIONSHIP\n"
                + "[2] TYPE: ACQUISITION | SOURCE: BigCo | TARGET: StartupX | "
                + "CONFIDENCE: 0.9 | SUMMARY: Acquisition.";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "General news", "No relationship here"));
        articles.add(article("a2", "BigCo acquires StartupX", "deal details"));

        List<CompanyRelationship> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relationshipType()).isEqualTo(CompanyRelationshipType.ACQUISITION);
    }

    @Test
    void parseResponse_sameSourceAndTarget_skipsLine() {
        String response = "RELATIONSHIP_RESULTS:\n"
                + "[1] TYPE: PARTNERSHIP | SOURCE: Acme Health | TARGET: Acme Health | "
                + "CONFIDENCE: 0.8 | SUMMARY: Self-reference.";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Title", "Body"));

        List<CompanyRelationship> result = adapter.parseResponse(response, articles);

        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_emptyResponse_returnsEmptyList() {
        List<CompanyRelationship> result = adapter.parseResponse("", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_invalidIndex_skipsLine() {
        String response = "RELATIONSHIP_RESULTS:\n"
                + "[99] TYPE: PARTNERSHIP | SOURCE: A | TARGET: B | "
                + "CONFIDENCE: 0.7 | SUMMARY: Test.";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Title", "Body"));

        List<CompanyRelationship> result = adapter.parseResponse(response, articles);

        assertThat(result).isEmpty();
    }

    @Test
    void classifyRelationships_emptyInput_returnsEmptyList() {
        List<CompanyRelationship> result = adapter.classifyRelationships(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void classifyRelationships_nullInput_returnsEmptyList() {
        List<CompanyRelationship> result = adapter.classifyRelationships(null);
        assertThat(result).isEmpty();
    }

    /**
     * Test subclass that avoids real ChatClient creation.
     */
    private static class TestRelationshipClassificationAdapter extends RelationshipClassificationAdapter {

        TestRelationshipClassificationAdapter(PromptLoaderService promptLoaderService) {
            super(ChatClient.builder(new org.springframework.ai.chat.model.ChatModel() {
                @Override
                public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                    return null;
                }
            }), promptLoaderService, "claude-haiku-4-5");
        }
    }
}
