package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the public read-only wiki view.
 *
 * <p>Provides three pages:
 * <ul>
 *   <li>{@code GET /wiki} — browsable index of all wiki pages with search + type filter</li>
 *   <li>{@code GET /wiki/{slug}} — page detail with rendered markdown, provenance table,
 *       contradictions, related pages, and revision history</li>
 *   <li>{@code GET /wiki/contradictions} — recent contradictions feed (Reversal Watch preview)</li>
 * </ul>
 *
 * <p>All wiki pages are public ({@code /wiki/**} is {@code permitAll()} in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig}).
 * Provenance links resolve {@link SourceRef#articleId()} to the original article URL
 * via {@link NewsArticleRepository}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Slf4j
@Controller
@RequestMapping("/wiki")
public class WikiController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneOffset.UTC);

    private final WikiQueryPort wikiQueryPort;
    private final WikiPageRepository pageRepository;
    private final WikiPageRevisionRepository revisionRepository;
    private final WikiContradictionRepository contradictionRepository;
    private final NewsArticleRepository articleRepository;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public WikiController(WikiQueryPort wikiQueryPort,
                           WikiPageRepository pageRepository,
                           WikiPageRevisionRepository revisionRepository,
                           WikiContradictionRepository contradictionRepository,
                           NewsArticleRepository articleRepository) {
        log.debug("WikiController() | wikiQueryPort={}, pageRepository={}, revisionRepository={}, "
                + "contradictionRepository={}, articleRepository={}",
                wikiQueryPort.getClass().getSimpleName(),
                pageRepository.getClass().getSimpleName(),
                revisionRepository.getClass().getSimpleName(),
                contradictionRepository.getClass().getSimpleName(),
                articleRepository.getClass().getSimpleName());
        this.wikiQueryPort = wikiQueryPort;
        this.pageRepository = pageRepository;
        this.revisionRepository = revisionRepository;
        this.contradictionRepository = contradictionRepository;
        this.articleRepository = articleRepository;
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    /**
     * Renders the wiki index page with optional search and type filtering.
     *
     * @param query    optional keyword search term
     * @param pageType optional page type filter (ENTITY, CONCEPT, etc.)
     * @param model    Thymeleaf model
     * @return view name "wiki-index"
     */
    @GetMapping
    public String wikiIndex(@RequestParam(required = false) String query,
                             @RequestParam(required = false) String pageType,
                             Model model) {
        log.debug("wikiIndex() | query={}, pageType={}", query, pageType);

        List<WikiPage> pages;
        if (query != null && !query.isBlank()) {
            pages = wikiQueryPort.findRelevantPages(query.trim(), 100);
        } else if (pageType != null && !pageType.isBlank()) {
            List<WikiPageEntity> entities = pageRepository.findByPageType(pageType);
            pages = new ArrayList<>();
            for (WikiPageEntity entity : entities) {
                WikiPage page = wikiQueryPort.getPage(entity.getSlug());
                if (page != null) {
                    pages.add(page);
                }
            }
        } else {
            List<WikiPageEntity> entities = pageRepository.findAll();
            pages = new ArrayList<>();
            for (WikiPageEntity entity : entities) {
                WikiPage page = wikiQueryPort.getPage(entity.getSlug());
                if (page != null) {
                    pages.add(page);
                }
            }
        }

        Map<String, String> pageTimestamps = new HashMap<>();
        for (WikiPage page : pages) {
            Instant displayTime = page.updatedAt() != null ? page.updatedAt() : page.createdAt();
            pageTimestamps.put(page.slug(), DISPLAY_FMT.format(displayTime) + " UTC");
        }

        model.addAttribute("pages", pages);
        model.addAttribute("pageTimestamps", pageTimestamps);
        model.addAttribute("query", query);
        model.addAttribute("selectedType", pageType);
        model.addAttribute("pageTypes", WikiPageType.values());
        model.addAttribute("totalPages", pages.size());

        log.debug("wikiIndex() | return=wiki-index (totalPages={})", pages.size());
        return "wiki-index";
    }

    /**
     * Renders a single wiki page with rendered markdown content, provenance
     * table, contradictions, related pages, and revision history.
     *
     * @param slug  kebab-case page identifier
     * @param model Thymeleaf model
     * @return view name "wiki-detail" or redirect to index if not found
     */
    @GetMapping("/{slug}")
    public String wikiPage(@PathVariable String slug, Model model) {
        log.debug("wikiPage() | slug={}", slug);

        WikiPage page = wikiQueryPort.getPage(slug);
        if (page == null) {
            log.warn("wikiPage() | page not found for slug={}", slug);
            return "redirect:/wiki";
        }

        // Render markdown to HTML
        String renderedContent = htmlRenderer.render(markdownParser.parse(
                page.contentMarkdown() != null ? page.contentMarkdown() : ""));

        // Resolve provenance URLs
        List<String> articleIds = new ArrayList<>();
        for (SourceRef source : page.sources()) {
            articleIds.add(source.articleId());
        }
        Map<String, String> articleUrlMap = new HashMap<>();
        Map<String, String> articleTitleMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            List<NewsArticleEntity> articles = articleRepository.findByArticleIdIn(articleIds);
            for (NewsArticleEntity entity : articles) {
                articleUrlMap.put(entity.getArticleId(), entity.getUrl());
                articleTitleMap.put(entity.getArticleId(), entity.getTitle());
            }
        }

        // Load contradictions for this page
        List<WikiContradictionEntity> contradictionEntities =
                contradictionRepository.findByPageSlug(slug);
        List<Map<String, String>> contradictions = new ArrayList<>();
        for (WikiContradictionEntity entity : contradictionEntities) {
            Map<String, String> c = new HashMap<>();
            c.put("priorClaim", entity.getPriorClaim());
            c.put("newClaim", entity.getNewClaim());
            c.put("detectedAt", DISPLAY_FMT.format(entity.getDetectedAt()) + " UTC");
            contradictions.add(c);
        }

        // Resolve related pages (slug → title)
        Map<String, String> relatedPages = new HashMap<>();
        for (String relatedSlug : page.relatedSlugs()) {
            WikiPage related = wikiQueryPort.getPage(relatedSlug);
            if (related != null) {
                relatedPages.put(relatedSlug, related.title());
            } else {
                relatedPages.put(relatedSlug, relatedSlug);
            }
        }

        // Load revision history
        List<WikiPageRevisionEntity> revisions =
                revisionRepository.findByPageSlugOrderByRevisionDesc(slug);
        Map<Integer, String> revisionTimestamps = new HashMap<>();
        for (WikiPageRevisionEntity rev : revisions) {
            revisionTimestamps.put(rev.getRevision(),
                    DISPLAY_FMT.format(rev.getCompiledAt()) + " UTC");
        }

        // Page timestamp
        Instant displayTime = page.updatedAt() != null ? page.updatedAt() : page.createdAt();
        String pageTimestamp = DISPLAY_FMT.format(displayTime) + " UTC";

        model.addAttribute("page", page);
        model.addAttribute("renderedContent", renderedContent);
        model.addAttribute("articleUrlMap", articleUrlMap);
        model.addAttribute("articleTitleMap", articleTitleMap);
        model.addAttribute("contradictions", contradictions);
        model.addAttribute("relatedPages", relatedPages);
        model.addAttribute("revisions", revisions);
        model.addAttribute("revisionTimestamps", revisionTimestamps);
        model.addAttribute("pageTimestamp", pageTimestamp);

        log.debug("wikiPage() | return=wiki-detail (slug={}, sources={}, contradictions={})",
                slug, page.sources().size(), contradictions.size());
        return "wiki-detail";
    }

    /**
     * Renders the contradictions feed page showing recent contradictions
     * across all wiki pages.
     *
     * @param days  number of days to look back (default 90)
     * @param model Thymeleaf model
     * @return view name "wiki-contradictions"
     */
    @GetMapping("/contradictions")
    public String contradictions(@RequestParam(defaultValue = "90") int days,
                                  Model model) {
        log.debug("contradictions() | days={}", days);

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Contradiction> contradictions = wikiQueryPort.recentContradictions(since);

        Map<String, String> contradictionTimestamps = new HashMap<>();
        Map<String, String> pageSlugTitles = new HashMap<>();
        int index = 0;
        for (Contradiction c : contradictions) {
            contradictionTimestamps.put(String.valueOf(index),
                    DISPLAY_FMT.format(c.detectedAt()) + " UTC");

            if (!pageSlugTitles.containsKey(c.pageSlug())) {
                WikiPage page = wikiQueryPort.getPage(c.pageSlug());
                pageSlugTitles.put(c.pageSlug(),
                        page != null ? page.title() : c.pageSlug());
            }
            index++;
        }

        model.addAttribute("contradictions", contradictions);
        model.addAttribute("contradictionTimestamps", contradictionTimestamps);
        model.addAttribute("pageSlugTitles", pageSlugTitles);
        model.addAttribute("days", days);

        log.debug("contradictions() | return=wiki-contradictions (count={})", contradictions.size());
        return "wiki-contradictions";
    }
}
