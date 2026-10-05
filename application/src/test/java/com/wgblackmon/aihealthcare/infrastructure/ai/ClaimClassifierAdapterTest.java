package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClaimClassifierAdapter} response parsing.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@ExtendWith(MockitoExtension.class)
class ClaimClassifierAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private PromptLoaderService promptLoaderService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private ClaimClassifierAdapter adapter;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new ClaimClassifierAdapter(chatClientBuilder, promptLoaderService);
        when(promptLoaderService.load("claim-classifier.txt"))
                .thenReturn("You are analyst. {articleCount} {numberedArticleList}");
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    @Test
    void parsesValidClaimResultsBlock() {
        String response = "CLAIM_RESULTS:\n" +
                "COMPANY|TYPE|VERDICT|CLAIM_TEXT|EVIDENCE_NOTES|SOURCE_URL|ARTICLE_ID\n" +
                "OpenAI|CAPABILITY_CLAIM|MARKETING_HYPE|GPT-5 beats everything|No independent study cited.|https://openai.com|art-1\n";
        when(callSpec.content()).thenReturn(response);

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).company()).isEqualTo("Openai");
        assertThat(claims.get(0).claimType()).isEqualTo(ClaimType.CAPABILITY_CLAIM);
        assertThat(claims.get(0).verdict()).isEqualTo(ClaimVerdict.MARKETING_HYPE);
        assertThat(claims.get(0).evidenceNotes()).isEqualTo("No independent study cited.");
        assertThat(claims.get(0).sourceUrl()).isEqualTo("https://openai.com");
        assertThat(claims.get(0).articleId()).isEqualTo("art-1");
    }

    @Test
    void emptyResponse_returnsEmptyList() {
        when(callSpec.content()).thenReturn("No relevant claims found.");

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).isEmpty();
    }

    @Test
    void malformedLines_areSkippedGracefully() {
        String response = "CLAIM_RESULTS:\n" +
                "COMPANY|TYPE|VERDICT|CLAIM_TEXT|EVIDENCE_NOTES|SOURCE_URL|ARTICLE_ID\n" +
                "OnlyOneField\n" +
                "OpenAI|CAPABILITY_CLAIM|MARKETING_HYPE|Valid claim|Notes.|https://x.com|art-1\n";
        when(callSpec.content()).thenReturn(response);

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).claimText()).isEqualTo("Valid claim");
    }

    @Test
    void unknownVerdictString_defaultsToAllegedUnverified() {
        String response = "CLAIM_RESULTS:\n" +
                "COMPANY|TYPE|VERDICT|CLAIM_TEXT|EVIDENCE_NOTES|SOURCE_URL|ARTICLE_ID\n" +
                "Google|CAPABILITY_CLAIM|TOTAL_NONSENSE|Gemini is amazing|Some notes.|https://g.com|art-1\n";
        when(callSpec.content()).thenReturn(response);

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).verdict()).isEqualTo(ClaimVerdict.ALLEGED_UNVERIFIED);
    }

    @Test
    void batchLimitRespected_twoBatchesForElevenArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            articles.add(buildArticle("art-" + i));
        }

        String response = "CLAIM_RESULTS:\n" +
                "COMPANY|TYPE|VERDICT|CLAIM_TEXT|EVIDENCE_NOTES|SOURCE_URL|ARTICLE_ID\n" +
                "OpenAI|CAPABILITY_CLAIM|MARKETING_HYPE|Claim text|Notes.|https://x.com|art-0\n";
        when(callSpec.content()).thenReturn(response);

        List<FrontierClaim> claims = adapter.classifyClaims(articles);

        // 11 articles → 2 batches → 2 LLM calls → 2 claims (one per batch response)
        assertThat(claims).hasSize(2);
    }

    @Test
    void llmCallFailure_returnsEmptyForBatch() {
        when(callSpec.content()).thenThrow(new RuntimeException("API error"));

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).isEmpty();
    }

    @Test
    void claimWithMissingOptionalFields_isStillParsed() {
        String response = "CLAIM_RESULTS:\n" +
                "COMPANY|TYPE|VERDICT|CLAIM_TEXT\n" +
                "Anthropic|SAFETY_CLAIM|EVIDENCE_BACKED|Claude is safe\n";
        when(callSpec.content()).thenReturn(response);

        List<FrontierClaim> claims = adapter.classifyClaims(List.of(buildArticle("art-1")));

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).evidenceNotes()).isNull();
        assertThat(claims.get(0).sourceUrl()).isNull();
    }

    private NewsArticle buildArticle(String id) {
        return new NewsArticle(id, "AI News", URI.create("https://example.com/" + id),
                "Body text", "AI", null, 1L, "Source", "INDUSTRY", 0.5, null);
    }
}
