package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiSourceRefRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WikiCompilationAdapter}.
 *
 * <p>Uses a mocked {@link ChatClient} to verify prompt construction,
 * response parsing, and persistence calls without making real LLM calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WikiCompilationAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private WikiPageRepository pageRepository;

    @Mock
    private WikiSourceRefRepository sourceRefRepository;

    @Mock
    private WikiContradictionRepository contradictionRepository;

    @Mock
    private WikiPageRevisionRepository revisionRepository;

    private WikiCompilationAdapter adapter;

    private static final String PROMPT_TEMPLATE =
            "Compile {articleCount} articles and {existingPageCount} pages.\n"
            + "{articleList}\n{existingPageSummaries}";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001", "FDA Clears New AI Device",
            URI.create("https://example.com/fda-ai"),
            "The FDA has cleared a new AI diagnostic device for radiology.",
            "FDA News", "Jane Doe", null, "FDA.gov", "REGULATORY", 0.9,
            Instant.parse("2026-07-01T00:00:00Z"));

    private static final String LLM_RESPONSE_WITH_PAGE =
            "### PAGE: fda-ai-device\n"
            + "TITLE: FDA AI Device Clearance\n"
            + "TYPE: ENTITY\n"
            + "TAGS: fda, ai-device, clearance\n"
            + "STATUS: CREATED\n"
            + "SOURCES: article-001\n"
            + "RELATED:\n"
            + "CONTENT:\n"
            + "# FDA AI Device\n"
            + "The FDA has cleared a new AI device.\n";

    private static final String LLM_RESPONSE_WITH_CONTRADICTION =
            "### PAGE: fda-ai-guidance\n"
            + "TITLE: FDA AI Guidance\n"
            + "TYPE: ENTITY\n"
            + "TAGS: fda\n"
            + "STATUS: UPDATED\n"
            + "SOURCES: article-001\n"
            + "RELATED:\n"
            + "CONTENT:\n"
            + "Updated content.\n"
            + "\n"
            + "### CONTRADICTION: fda-ai-guidance\n"
            + "PRIOR_CLAIM: All AI devices require premarket review\n"
            + "NEW_CLAIM: Low-risk AI devices are now exempt\n"
            + "PRIOR_SOURCES: article-old\n"
            + "NEW_SOURCES: article-001\n";

    private static final String LLM_RESPONSE_WARNING_ONLY =
            "### WARNING: No actionable content in this batch\n";

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new WikiCompilationAdapter(
                chatClientBuilder,
                pageRepository,
                sourceRefRepository,
                contradictionRepository,
                revisionRepository,
                new WikiResponseParser(),
                PROMPT_TEMPLATE
        );
    }

    private void mockChatResponse(String response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec userSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }

    @Test
    void compileNewSources_createsNewPage_reportsCreated() {
        mockChatResponse(LLM_RESPONSE_WITH_PAGE);
        when(pageRepository.findAll()).thenReturn(List.of());
        when(pageRepository.existsById("fda-ai-device")).thenReturn(false);

        CompilationReport report = adapter.compileNewSources(List.of(ARTICLE));

        assertThat(report.articlesProcessed()).isEqualTo(1);
        assertThat(report.pagesCreated()).containsExactly("fda-ai-device");
        assertThat(report.pagesUpdated()).isEmpty();
        assertThat(report.contradictionsFlagged()).isEmpty();
        verify(pageRepository).save(any(WikiPageEntity.class));
        verify(revisionRepository).save(any());
    }

    @Test
    void compileNewSources_updatesExistingPage_reportsUpdated() {
        mockChatResponse(LLM_RESPONSE_WITH_CONTRADICTION);

        WikiPageEntity existing = new WikiPageEntity();
        existing.setSlug("fda-ai-guidance");
        existing.setRevision(1);
        existing.setCreatedAt(Instant.parse("2026-06-01T00:00:00Z"));

        when(pageRepository.findAll()).thenReturn(List.of(existing));
        when(pageRepository.existsById("fda-ai-guidance")).thenReturn(true);
        when(pageRepository.findById("fda-ai-guidance")).thenReturn(java.util.Optional.of(existing));

        CompilationReport report = adapter.compileNewSources(List.of(ARTICLE));

        assertThat(report.pagesUpdated()).containsExactly("fda-ai-guidance");
        assertThat(report.contradictionsFlagged()).hasSize(1);
        assertThat(report.contradictionsFlagged().get(0).priorClaim())
                .isEqualTo("All AI devices require premarket review");
        verify(contradictionRepository).save(any());
    }

    @Test
    void compileNewSources_warningOnly_returnsEmptyReport() {
        mockChatResponse(LLM_RESPONSE_WARNING_ONLY);
        when(pageRepository.findAll()).thenReturn(List.of());

        CompilationReport report = adapter.compileNewSources(List.of(ARTICLE));

        assertThat(report.pagesCreated()).isEmpty();
        assertThat(report.pagesUpdated()).isEmpty();
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0)).contains("No actionable content");
        verify(pageRepository, never()).save(any(WikiPageEntity.class));
    }

    @Test
    void compileNewSources_emptyArticleList_stillCallsLlm() {
        mockChatResponse(LLM_RESPONSE_WARNING_ONLY);
        when(pageRepository.findAll()).thenReturn(List.of());

        CompilationReport report = adapter.compileNewSources(List.of());

        assertThat(report.articlesProcessed()).isEqualTo(0);
        verify(chatClient).prompt();
    }

    @Test
    void buildPrompt_includesArticleDetails() {
        WikiPageEntity existing = new WikiPageEntity();
        existing.setSlug("existing-page");
        existing.setTitle("Existing Page");
        existing.setPageType("ENTITY");
        existing.setRevision(2);

        String prompt = adapter.buildPrompt(List.of(ARTICLE), List.of(existing));

        assertThat(prompt).contains("article-001");
        assertThat(prompt).contains("FDA Clears New AI Device");
        assertThat(prompt).contains("existing-page");
        assertThat(prompt).contains("rev 2");
    }

    @Test
    void buildPrompt_noExistingPages_showsFirstRunMessage() {
        String prompt = adapter.buildPrompt(List.of(ARTICLE), List.of());

        assertThat(prompt).contains("first compilation run");
    }
}
