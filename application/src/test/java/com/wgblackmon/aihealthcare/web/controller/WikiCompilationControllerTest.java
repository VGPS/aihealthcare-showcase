package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link WikiCompilationController}.
 *
 * <p>Verifies the manual wiki compilation trigger endpoint returns
 * compilation reports as JSON.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
@WebMvcTest(WikiCompilationController.class)
class WikiCompilationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private KnowledgeCompilationPort compilationPort;

    @MockBean
    private NewsArticleRepository articleRepository;

    private static final Instant STARTED = Instant.parse("2026-07-04T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-04T10:05:00Z");

    @Test
    void triggerCompilation_withArticles_returnsReport() throws Exception {
        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setArticleId("article-001");
        entity.setTitle("FDA AI Device");
        entity.setUrl("https://example.com/fda");
        entity.setBodyText("Body text");
        entity.setTopic("FDA News");
        entity.setSourceName("FDA.gov");
        entity.setSourceTier("REGULATORY");
        entity.setSourceWeight(0.9);
        entity.setCreatedAt(Instant.now());

        when(articleRepository.findByCreatedAtAfterOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(entity));

        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 1,
                List.of("fda-ai-device"), List.of(),
                List.of(), List.of()
        );
        when(compilationPort.compileNewSources(anyList())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/compile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesProcessed").value(1))
                .andExpect(jsonPath("$.pagesCreated[0]").value("fda-ai-device"))
                .andExpect(jsonPath("$.contradictionCount").value(0));

        verify(compilationPort).compileNewSources(anyList());
    }

    @Test
    void triggerCompilation_noArticles_returnsEmptyReport() throws Exception {
        when(articleRepository.findByCreatedAtAfterOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of());

        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 0,
                List.of(), List.of(), List.of(),
                List.of("No actionable content in this batch")
        );
        when(compilationPort.compileNewSources(anyList())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/compile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesProcessed").value(0))
                .andExpect(jsonPath("$.pagesCreated").isEmpty())
                .andExpect(jsonPath("$.warnings[0]").value("No actionable content in this batch"));
    }

    @Test
    void triggerCompilation_withContradictions_reportsCount() throws Exception {
        when(articleRepository.findByCreatedAtAfterOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of());

        CompilationReport report = new CompilationReport(
                STARTED, COMPLETED, 5,
                List.of("fda-ai-guidance"), List.of("epic-ambient"),
                List.of(new com.wgblackmon.aihealthcare.domain.model.Contradiction(
                        "fda-ai-guidance", "Prior claim", "New claim",
                        List.of(), List.of(), STARTED)),
                List.of()
        );
        when(compilationPort.compileNewSources(anyList())).thenReturn(report);

        mockMvc.perform(post("/monitoring/wiki/compile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contradictionCount").value(1))
                .andExpect(jsonPath("$.pagesCreated[0]").value("fda-ai-guidance"))
                .andExpect(jsonPath("$.pagesUpdated[0]").value("epic-ambient"));
    }
}
