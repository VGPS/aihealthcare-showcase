package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SocialPostAgentAdapter} response parsing and tool logic.
 * Tests the parser in isolation — no real ChatClient or AI calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
@ExtendWith(MockitoExtension.class)
class SocialPostAgentAdapterTest {

    @Mock ProduceMarketDigestUseCase digestUseCase;
    @Mock NewsArticleRepository articleRepository;

    SocialPostAgentAdapter adapter;

    @BeforeEach
    void setUp() {
        // Build adapter directly — bypasses Spring wiring and ChatClient
        adapter = new SocialPostAgentAdapter(digestUseCase, articleRepository);
    }

    @Test
    void parseResponse_allSectionsPresent_extractsCorrectly() {
        String response = """
                LINKEDIN_BODY:
                Epic Systems raised $500M in a landmark round.

                LINKEDIN_COMMENT:
                #AIHealthcare #HealthTech Follow for daily intel.

                FACEBOOK_BODY:
                Epic Systems just raised $500M.

                FACEBOOK_COMMENT:
                #AIHealthcare

                ENTRIES_SELECTED:
                Epic Systems $500M Raise, FDA Clears AI Diagnostic Tool

                RATIONALE:
                Both entries are CONFIRMED FUNDING/REGULATORY events ranked 1-2.
                """;

        SocialPostDraft draft = adapter.parseResponse(response, LocalDate.of(2026, 9, 26));

        assertThat(draft.linkedinBody()).contains("Epic Systems raised $500M");
        assertThat(draft.linkedinComment()).contains("#AIHealthcare");
        assertThat(draft.facebookBody()).contains("Epic Systems just raised $500M");
        assertThat(draft.entriesSelected()).hasSize(2);
        assertThat(draft.rationale()).contains("CONFIRMED");
        assertThat(draft.generatedAt()).isNotNull();
    }

    @Test
    void parseResponse_missingFacebookComment_producesEmptyString() {
        String response = """
                LINKEDIN_BODY:
                Body text here.

                LINKEDIN_COMMENT:
                Comment here.

                FACEBOOK_BODY:
                FB body here.

                FACEBOOK_COMMENT:

                ENTRIES_SELECTED:
                Headline A

                RATIONALE:
                Selected for high rank.
                """;

        SocialPostDraft draft = adapter.parseResponse(response, LocalDate.of(2026, 9, 26));

        assertThat(draft.facebookComment()).isBlank();
    }

    @Test
    void parseResponse_nullResponse_throws() {
        assertThatThrownBy(() -> adapter.parseResponse(null, LocalDate.of(2026, 9, 26)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parseResponse_blankResponse_throws() {
        assertThatThrownBy(() -> adapter.parseResponse("   ", LocalDate.of(2026, 9, 26)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void getDigestEntries_noDigestAvailable_returnsHelpfulMessage() {
        when(digestUseCase.findByDate(LocalDate.of(2026, 9, 26))).thenReturn(Optional.empty());
        when(digestUseCase.findLatest()).thenReturn(Optional.empty());

        String result = adapter.getDigestEntries("2026-09-26");

        assertThat(result).contains("No market digest available");
    }

    @Test
    void getCompanyArticles_noArticles_returnsHelpfulMessage() {
        when(articleRepository.findRealArticlesByCompanyName("Acme Corp"))
                .thenReturn(java.util.List.of());

        String result = adapter.getCompanyArticles("Acme Corp");

        assertThat(result).contains("No recent articles found");
    }
}
