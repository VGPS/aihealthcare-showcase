package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiSourceRefEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiSourceRefRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spring AI adapter implementing {@link KnowledgeCompilationPort}.
 *
 * <p>Orchestrates the full wiki compilation pipeline:
 * <ol>
 *   <li>Loads existing wiki pages from DB</li>
 *   <li>Builds a structured prompt from articles + existing pages</li>
 *   <li>Calls Claude via {@link ChatClient} to compile knowledge</li>
 *   <li>Parses the structured response via {@link WikiResponseParser}</li>
 *   <li>Persists new/updated pages, revisions, contradictions, and source refs</li>
 *   <li>Returns a {@link CompilationReport}</li>
 * </ol>
 *
 * <p>This adapter is the only class that touches both the AI layer and wiki
 * persistence.  The domain layer remains unaware of how compilation is implemented.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-09-12
 */
@Slf4j
public class WikiCompilationAdapter implements KnowledgeCompilationPort {

    private final ChatClient chatClient;
    private final WikiPageRepository pageRepository;
    private final WikiSourceRefRepository sourceRefRepository;
    private final WikiContradictionRepository contradictionRepository;
    private final WikiPageRevisionRepository revisionRepository;
    private final WikiResponseParser responseParser;
    private final String wikiCompilePrompt;

    public WikiCompilationAdapter(ChatClient.Builder chatClientBuilder,
                                   WikiPageRepository pageRepository,
                                   WikiSourceRefRepository sourceRefRepository,
                                   WikiContradictionRepository contradictionRepository,
                                   WikiPageRevisionRepository revisionRepository,
                                   WikiResponseParser responseParser,
                                   String wikiCompilePrompt) {
        log.debug("WikiCompilationAdapter() | constructing with prompt length={}",
                wikiCompilePrompt != null ? wikiCompilePrompt.length() : 0);
        this.chatClient = chatClientBuilder.build();
        this.pageRepository = pageRepository;
        this.sourceRefRepository = sourceRefRepository;
        this.contradictionRepository = contradictionRepository;
        this.revisionRepository = revisionRepository;
        this.responseParser = responseParser;
        this.wikiCompilePrompt = wikiCompilePrompt;
        log.debug("WikiCompilationAdapter() | return=void");
    }

    @Override
    @Transactional
    public CompilationReport compileNewSources(List<NewsArticle> newArticles) {
        log.debug("compileNewSources() | articles={}", newArticles.size());

        Instant started = Instant.now();

        // 2a. Load existing pages
        List<WikiPageEntity> existingPages = pageRepository.findAll();
        log.debug("compileNewSources() | existingPages={}", existingPages.size());

        // 2b. Build prompt
        String prompt = buildPrompt(newArticles, existingPages);
        log.debug("compileNewSources() | promptLength={}", prompt.length());

        // 2c. Call Claude with high token limit for multi-page output
        String response = chatClient.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder().maxTokens(16000).build())
                .call().content();
        log.debug("compileNewSources() | responseLength={}", response != null ? response.length() : 0);

        // 2d. Parse response
        List<WikiPage> pages = responseParser.parsePages(response, started);
        List<Contradiction> contradictions = responseParser.parseContradictions(response, started);
        List<String> warnings = responseParser.parseWarnings(response);

        // 2e. Persist
        List<String> createdSlugs = new ArrayList<>();
        List<String> updatedSlugs = new ArrayList<>();
        for (WikiPage page : pages) {
            boolean exists = pageRepository.existsById(page.slug());
            if (exists) {
                updatedSlugs.add(page.slug());
            } else {
                createdSlugs.add(page.slug());
            }
            persistPage(page, exists);
        }
        for (Contradiction c : contradictions) {
            persistContradiction(c);
        }

        // 2f. Build report
        Instant completed = Instant.now();
        CompilationReport report = new CompilationReport(
                started, completed, newArticles.size(),
                createdSlugs, updatedSlugs, contradictions, warnings
        );

        log.info("compileNewSources() | created={}, updated={}, contradictions={}, warnings={}",
                createdSlugs.size(), updatedSlugs.size(), contradictions.size(), warnings.size());
        log.debug("compileNewSources() | return={}", report);
        return report;
    }

    String buildPrompt(List<NewsArticle> articles, List<WikiPageEntity> existingPages) {
        log.debug("buildPrompt() | articles={}, existingPages={}", articles.size(), existingPages.size());

        StringBuilder articleList = new StringBuilder();
        int articleNum = 1;
        for (NewsArticle article : articles) {
            articleList.append("[").append(articleNum).append("] ID: ").append(article.articleId())
                    .append("\n  Title: ").append(article.title())
                    .append("\n  Source: ").append(article.sourceName() != null ? article.sourceName() : "unknown")
                    .append("\n  URL: ").append(article.url())
                    .append("\n  Body: ").append(truncateBody(article.bodyText()))
                    .append("\n\n");
            articleNum++;
        }

        StringBuilder pageSummaries = new StringBuilder();
        if (existingPages.isEmpty()) {
            pageSummaries.append("(No existing wiki pages — this is the first compilation run.)\n");
        } else {
            for (WikiPageEntity page : existingPages) {
                pageSummaries.append("- ").append(page.getSlug())
                        .append(" (").append(page.getPageType()).append("): ")
                        .append(page.getTitle())
                        .append(" [rev ").append(page.getRevision()).append("]\n");
            }
        }

        String prompt = wikiCompilePrompt
                .replace("{articleCount}", String.valueOf(articles.size()))
                .replace("{existingPageCount}", String.valueOf(existingPages.size()))
                .replace("{articleList}", articleList.toString())
                .replace("{existingPageSummaries}", pageSummaries.toString());

        log.debug("buildPrompt() | return=prompt (length={})", prompt.length());
        return prompt;
    }

    private void persistPage(WikiPage page, boolean isUpdate) {
        log.debug("persistPage() | slug={}, isUpdate={}", page.slug(), isUpdate);

        WikiPageEntity entity;
        if (isUpdate) {
            Optional<WikiPageEntity> existing = pageRepository.findById(page.slug());
            entity = existing.orElseGet(WikiPageEntity::new);
            entity.setRevision(entity.getRevision() + 1);
            entity.setUpdatedAt(Instant.now());
        } else {
            entity = new WikiPageEntity();
            entity.setSlug(page.slug());
            entity.setCreatedAt(Instant.now());
            entity.setRevision(1);
        }

        entity.setTitle(page.title());
        entity.setPageType(page.pageType().name());
        entity.setTags(PipeDelimitedUtils.join(page.tags()));
        entity.setContentMarkdown(page.contentMarkdown());
        entity.setRelatedSlugs(PipeDelimitedUtils.join(page.relatedSlugs()));
        pageRepository.save(entity);

        // Save revision for audit trail
        WikiPageRevisionEntity revision = new WikiPageRevisionEntity();
        revision.setPageSlug(page.slug());
        revision.setRevision(entity.getRevision());
        revision.setContentMarkdown(page.contentMarkdown());
        revision.setCompiledAt(Instant.now());
        revisionRepository.save(revision);

        // Replace source refs (delete old, insert new)
        sourceRefRepository.deleteByPageSlug(page.slug());
        for (SourceRef source : page.sources()) {
            WikiSourceRefEntity refEntity = new WikiSourceRefEntity();
            refEntity.setPageSlug(page.slug());
            refEntity.setArticleId(source.articleId());
            refEntity.setSourceName(source.sourceName());
            refEntity.setHarvestedOn(source.harvestedOn() != null ? source.harvestedOn() : LocalDate.now());
            refEntity.setExcerpt(source.excerpt());
            sourceRefRepository.save(refEntity);
        }

        log.debug("persistPage() | return=void");
    }

    private void persistContradiction(Contradiction contradiction) {
        log.debug("persistContradiction() | pageSlug={}", contradiction.pageSlug());

        WikiContradictionEntity entity = new WikiContradictionEntity();
        entity.setPageSlug(contradiction.pageSlug());
        entity.setPriorClaim(contradiction.priorClaim());
        entity.setNewClaim(contradiction.newClaim());
        entity.setPriorSourceIds(joinSourceRefIds(contradiction.priorSources()));
        entity.setNewSourceIds(joinSourceRefIds(contradiction.newSources()));
        entity.setDetectedAt(contradiction.detectedAt());
        contradictionRepository.save(entity);

        log.debug("persistContradiction() | return=void");
    }

    private String truncateBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "(no body text)";
        }
        if (bodyText.length() > 2000) {
            return bodyText.substring(0, 2000) + "...";
        }
        return bodyText;
    }

    private String joinSourceRefIds(List<SourceRef> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(sources.get(i).articleId());
        }
        return sb.toString();
    }
}
