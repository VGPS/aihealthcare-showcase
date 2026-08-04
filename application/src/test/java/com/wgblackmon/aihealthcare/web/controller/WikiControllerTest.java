package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link WikiController}.
 *
 * <p>Verifies the wiki index, detail, and contradictions pages render
 * correctly with appropriate model attributes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(WikiController.class)
class WikiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyPort apiKeyPort;

    @MockBean
    private WikiQueryPort wikiQueryPort;

    @MockBean
    private WikiPageRepository pageRepository;

    @MockBean
    private WikiPageRevisionRepository revisionRepository;

    @MockBean
    private WikiContradictionRepository contradictionRepository;

    @MockBean
    private NewsArticleRepository articleRepository;

    @MockBean
    private AiSearchPort aiSearchPort;

    @MockBean
    private AnalystNotePort analystNotePort;

    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");

    private WikiPage buildTestPage(String slug, String title, WikiPageType type) {
        return new WikiPage(slug, title, type,
                List.of("ai", "healthcare"),
                "# Test\nSome **markdown** content.",
                List.of(new SourceRef("art-001", "FDA", LocalDate.of(2026, 7, 1), null)),
                List.of(),
                NOW, NOW, 1);
    }

    @Test
    void wikiIndex_noPages_rendersEmptyState() throws Exception {
        when(pageRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/wiki"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 0));
    }

    @Test
    void wikiIndex_withPages_rendersPageCards() throws Exception {
        WikiPageEntity entity = new WikiPageEntity();
        entity.setSlug("fda-ai-guidance");
        entity.setTitle("FDA AI Guidance");
        entity.setPageType("ENTITY");
        entity.setCreatedAt(NOW);
        entity.setRevision(1);
        when(pageRepository.findAll()).thenReturn(List.of(entity));

        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);

        mockMvc.perform(get("/wiki"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    void wikiIndex_withQuery_filtersPages() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        WikiPageEntity entity = new WikiPageEntity();
        entity.setSlug("fda-ai-guidance");
        entity.setTitle("FDA AI Guidance");
        entity.setPageType("ENTITY");
        entity.setCreatedAt(NOW);
        when(pageRepository.searchByKeyword("FDA")).thenReturn(List.of(entity));
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);

        mockMvc.perform(get("/wiki").param("query", "FDA"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    void wikiIndex_withPageType_filtersPages() throws Exception {
        WikiPageEntity entity = new WikiPageEntity();
        entity.setSlug("ai-concept");
        entity.setTitle("AI Concept");
        entity.setPageType("CONCEPT");
        entity.setCreatedAt(NOW);
        entity.setRevision(1);
        when(pageRepository.findByPageType("CONCEPT")).thenReturn(List.of(entity));

        WikiPage page = buildTestPage("ai-concept", "AI Concept", WikiPageType.CONCEPT);
        when(wikiQueryPort.getPage("ai-concept")).thenReturn(page);

        mockMvc.perform(get("/wiki").param("pageType", "CONCEPT"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    void wikiPage_existingSlug_rendersDetail() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);
        when(revisionRepository.findByPageSlugOrderByRevisionDesc("fda-ai-guidance"))
                .thenReturn(List.of());
        when(contradictionRepository.findByPageSlug("fda-ai-guidance"))
                .thenReturn(List.of());
        when(articleRepository.findByArticleIdIn(List.of("art-001")))
                .thenReturn(List.of());

        mockMvc.perform(get("/wiki/fda-ai-guidance"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-detail"))
                .andExpect(model().attributeExists("page", "renderedContent",
                        "articleUrlMap", "contradictions", "relatedPages", "revisions"));
    }

    @Test
    void wikiPage_nonexistentSlug_redirectsToIndex() throws Exception {
        when(wikiQueryPort.getPage("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/wiki/nonexistent"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void wikiPage_withSources_rendersProvenanceTable() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);
        when(revisionRepository.findByPageSlugOrderByRevisionDesc("fda-ai-guidance"))
                .thenReturn(List.of());
        when(contradictionRepository.findByPageSlug("fda-ai-guidance"))
                .thenReturn(List.of());

        NewsArticleEntity article = new NewsArticleEntity();
        article.setArticleId("art-001");
        article.setTitle("FDA Approves AI Device");
        article.setUrl("https://fda.gov/ai-device");
        when(articleRepository.findByArticleIdIn(List.of("art-001")))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/wiki/fda-ai-guidance"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-detail"))
                .andExpect(model().attributeExists("articleUrlMap", "articleTitleMap"));
    }

    @Test
    void contradictions_rendersFeed() throws Exception {
        Contradiction c = new Contradiction("fda-ai-guidance",
                "Prior claim text", "New contradicting claim",
                List.of(new SourceRef("art-001", "FDA", LocalDate.of(2026, 6, 1), null)),
                List.of(new SourceRef("art-002", "PubMed", LocalDate.of(2026, 7, 1), null)),
                NOW);
        when(wikiQueryPort.recentContradictions(any(Instant.class))).thenReturn(List.of(c));

        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);

        mockMvc.perform(get("/wiki/contradictions"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-contradictions"))
                .andExpect(model().attributeExists("contradictions",
                        "contradictionTimestamps", "pageSlugTitles"));
    }

    @Test
    void digest_rendersWithDefaults() throws Exception {
        when(pageRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class)))
                .thenReturn(List.of());
        when(pageRepository.findByUpdatedAtAfterAndRevisionGreaterThanOrderByUpdatedAtDesc(
                any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of());
        when(pageRepository.count()).thenReturn(23L);

        mockMvc.perform(get("/wiki/digest"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-digest"))
                .andExpect(model().attributeExists("newPages", "updatedPages",
                        "contradictions", "days", "totalPages"))
                .andExpect(model().attribute("days", 7))
                .andExpect(model().attribute("totalPages", 23L));
    }

    @Test
    void digest_withNewAndUpdatedPages_rendersAll() throws Exception {
        WikiPageEntity newPage = new WikiPageEntity();
        newPage.setSlug("new-page");
        newPage.setTitle("New Page");
        newPage.setPageType("CONCEPT");
        newPage.setCreatedAt(NOW);
        newPage.setRevision(1);

        WikiPageEntity updatedPage = new WikiPageEntity();
        updatedPage.setSlug("updated-page");
        updatedPage.setTitle("Updated Page");
        updatedPage.setPageType("ENTITY");
        updatedPage.setCreatedAt(NOW);
        updatedPage.setUpdatedAt(NOW);
        updatedPage.setRevision(2);

        when(pageRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class)))
                .thenReturn(List.of(newPage));
        when(pageRepository.findByUpdatedAtAfterAndRevisionGreaterThanOrderByUpdatedAtDesc(
                any(Instant.class), anyInt()))
                .thenReturn(List.of(updatedPage));
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of());
        when(pageRepository.count()).thenReturn(23L);

        mockMvc.perform(get("/wiki/digest").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-digest"))
                .andExpect(model().attribute("days", 30));
    }

    @Test
    void askWiki_noQuery_rendersEmptyForm() throws Exception {
        when(aiSearchPort.modelName()).thenReturn("Claude");
        when(aiSearchPort.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/wiki/ask"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-ask"))
                .andExpect(model().attributeExists("availableModels"));
    }

    @Test
    void askWiki_withQuery_noPages_rendersEmptyState() throws Exception {
        when(aiSearchPort.modelName()).thenReturn("Claude");
        when(aiSearchPort.isAvailable()).thenReturn(true);
        when(wikiQueryPort.findRelevantPages(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/wiki/ask").param("q", "FDA AI regulation"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-ask"))
                .andExpect(model().attribute("q", "FDA AI regulation"))
                .andExpect(model().attribute("pageCount", 0));
    }

    @Test
    void askWiki_withQuery_andPages_rendersSynthesis() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.findRelevantPages(anyString(), anyInt())).thenReturn(List.of(page));
        when(aiSearchPort.modelName()).thenReturn("Claude");
        when(aiSearchPort.isAvailable()).thenReturn(true);
        when(aiSearchPort.synthesize(anyString(), any())).thenReturn(
                new AiSearchSynthesis("Claude", "Summary of wiki content.",
                        List.of("Finding 1", "Finding 2"), NOW));

        mockMvc.perform(get("/wiki/ask").param("q", "FDA AI regulation")
                        .param("models", "Claude"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-ask"))
                .andExpect(model().attributeExists("syntheses", "pages", "pageTimestamps"));
    }

    @Test
    void wikiPage_rendersEvidenceGrades() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);
        when(revisionRepository.findByPageSlugOrderByRevisionDesc("fda-ai-guidance"))
                .thenReturn(List.of());
        when(contradictionRepository.findByPageSlug("fda-ai-guidance"))
                .thenReturn(List.of());

        NewsArticleEntity article = new NewsArticleEntity();
        article.setArticleId("art-001");
        article.setTitle("FDA Approves AI Device");
        article.setUrl("https://fda.gov/ai-device");
        when(articleRepository.findByArticleIdIn(List.of("art-001")))
                .thenReturn(List.of(article));

        mockMvc.perform(get("/wiki/fda-ai-guidance"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-detail"))
                .andExpect(model().attributeExists("evidenceGrades", "evidenceColors"));
    }
}
