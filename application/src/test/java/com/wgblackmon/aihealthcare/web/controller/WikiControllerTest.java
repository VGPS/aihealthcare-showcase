package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageIndexView;
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
import java.util.Optional;

import org.springframework.data.domain.Pageable;

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
 * @updated 2026-09-06
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

    @MockBean
    private SubscriberPort subscriberPort;

    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");

    private WikiPage buildTestPage(String slug, String title, WikiPageType type) {
        return new WikiPage(slug, title, type,
                List.of("ai", "healthcare"),
                "# Test\nSome **markdown** content.",
                List.of(new SourceRef("art-001", "FDA", LocalDate.of(2026, 7, 1), null)),
                List.of(),
                NOW, NOW, 1);
    }

    private WikiPageIndexView buildIndexView(String slug, String title, String pageType) {
        return new WikiPageIndexView() {
            public String getSlug()         { return slug; }
            public String getTitle()        { return title; }
            public String getPageType()     { return pageType; }
            public String getTags()         { return null; }
            public String getRelatedSlugs() { return null; }
            public Instant getCreatedAt()   { return NOW; }
            public Instant getUpdatedAt()   { return null; }
            public int getRevision()        { return 1; }
        };
    }

    @Test
    void wikiIndex_noPages_rendersEmptyState() throws Exception {
        when(pageRepository.findAllBy(any(Pageable.class))).thenReturn(List.of());
        when(pageRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/wiki"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 0L));
    }

    @Test
    void wikiIndex_withPages_rendersPageCards() throws Exception {
        when(pageRepository.findAllBy(any(Pageable.class)))
                .thenReturn(List.of(buildIndexView("fda-ai-guidance", "FDA AI Guidance", "ENTITY")));
        when(pageRepository.count()).thenReturn(1L);

        mockMvc.perform(get("/wiki"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1L));
    }

    @Test
    void wikiIndex_withQuery_filtersPages() throws Exception {
        when(pageRepository.searchByKeywordForIndex("FDA"))
                .thenReturn(List.of(buildIndexView("fda-ai-guidance", "FDA AI Guidance", "ENTITY")));

        mockMvc.perform(get("/wiki").param("query", "FDA"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1L));
    }

    @Test
    void wikiIndex_withPageType_filtersPages() throws Exception {
        when(pageRepository.findAllByPageType(eq("CONCEPT"), any(Pageable.class)))
                .thenReturn(List.of(buildIndexView("ai-concept", "AI Concept", "CONCEPT")));
        when(pageRepository.countByPageType("CONCEPT")).thenReturn(1L);

        mockMvc.perform(get("/wiki").param("pageType", "CONCEPT"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-index"))
                .andExpect(model().attribute("totalPages", 1L));
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

    // --- Tier gating tests ---

    @Test
    void wikiPage_freeUser_rendersTeaser() throws Exception {
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
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    void wikiPage_subscriberUser_rendersFullContent() throws Exception {
        WikiPage page = buildTestPage("fda-ai-guidance", "FDA AI Guidance", WikiPageType.ENTITY);
        when(wikiQueryPort.getPage("fda-ai-guidance")).thenReturn(page);
        when(revisionRepository.findByPageSlugOrderByRevisionDesc("fda-ai-guidance"))
                .thenReturn(List.of());
        when(contradictionRepository.findByPageSlug("fda-ai-guidance"))
                .thenReturn(List.of());
        when(articleRepository.findByArticleIdIn(List.of("art-001")))
                .thenReturn(List.of());
        when(subscriberPort.findByEmail("user"))
                .thenReturn(Optional.of(new Subscriber("user", "Test User", true,
                        NOW, SubscriptionTier.SUBSCRIBER, null, null, null)));

        mockMvc.perform(get("/wiki/fda-ai-guidance"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-detail"))
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void wikiPage_adminUser_getsFullAccess() throws Exception {
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
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    void contradictions_freeUser_rendersUpgrade() throws Exception {
        when(wikiQueryPort.recentContradictions(any(Instant.class))).thenReturn(List.of());

        mockMvc.perform(get("/wiki/contradictions"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-contradictions"))
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    void digest_freeUser_rendersUpgrade() throws Exception {
        when(pageRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(Instant.class)))
                .thenReturn(List.of());
        when(pageRepository.findByUpdatedAtAfterAndRevisionGreaterThanOrderByUpdatedAtDesc(
                any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of());
        when(pageRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/wiki/digest"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-digest"))
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    void askWiki_freeUser_rendersUpgrade() throws Exception {
        when(aiSearchPort.modelName()).thenReturn("Claude");
        when(aiSearchPort.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/wiki/ask"))
                .andExpect(status().isOk())
                .andExpect(view().name("wiki-ask"))
                .andExpect(model().attribute("fullAccess", false));
    }
}
