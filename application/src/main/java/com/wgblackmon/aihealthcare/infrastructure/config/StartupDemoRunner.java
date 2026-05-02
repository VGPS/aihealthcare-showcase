package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.ai.EmbeddingScheduler;
import com.wgblackmon.aihealthcare.infrastructure.delivery.NotebookLMService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface.HuggingFaceHarvester;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot demo runner that executes the full harvest-to-NotebookLM pipeline
 * on application startup and logs every step — including DB contents and the
 * exported file.
 *
 * <p>Activated only when the {@code demo} Spring profile is active:
 * <pre>{@code
 * mvn spring-boot:run -Dspring-boot.run.profiles=demo
 * }</pre>
 *
 * <p>Pipeline steps:
 * <ol>
 *   <li>Harvest competitor web pages via {@link WebPageHarvester}</li>
 *   <li>Harvest HuggingFace healthcare models via {@link HuggingFaceHarvester}</li>
 *   <li>Persist all harvested articles via {@link ArticleStoragePort}</li>
 *   <li>Query and log all DB rows via {@link NewsArticleRepository}</li>
 *   <li>Embed all articles into the vector store via {@link EmbeddingScheduler}</li>
 *   <li>Run a semantic similarity search via {@link ArticleSearchPort}</li>
 *   <li>Fetch articles back through the ingestion port</li>
 *   <li>Export to NotebookLM directory via {@link NotebookLMService}</li>
 *   <li>Read and log the exported file contents</li>
 * </ol>
 *
 * <p>The {@link EmbeddingScheduler} and {@link ArticleSearchPort} dependencies
 * are {@code @Nullable} — if the vector store is not configured (e.g., H2
 * profile), those steps are skipped gracefully.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-20
 * @updated 2026-04-21
 */
@Slf4j
@Component
@Profile("demo")
public class StartupDemoRunner implements CommandLineRunner {

    private final ArticleHarvestingPort rssHarvestingPort;
    private final WebPageHarvester webPageHarvester;
    private final HuggingFaceHarvester huggingFaceHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final ArticleIngestionPort articleIngestionPort;
    private final NewsArticleRepository articleRepository;
    private final NotebookLMService notebookLMService;
    private final EmbeddingScheduler embeddingScheduler;
    private final ArticleSearchPort articleSearchPort;

    public StartupDemoRunner(ArticleHarvestingPort rssHarvestingPort,
                             WebPageHarvester webPageHarvester,
                             HuggingFaceHarvester huggingFaceHarvester,
                             ArticleStoragePort articleStoragePort,
                             ArticleIngestionPort articleIngestionPort,
                             NewsArticleRepository articleRepository,
                             NotebookLMService notebookLMService,
                             @Nullable EmbeddingScheduler embeddingScheduler,
                             @Nullable ArticleSearchPort articleSearchPort) {
        log.debug("StartupDemoRunner() | rssHarvestingPort={}, webPageHarvester={}, "
                  + "huggingFaceHarvester={}, articleStoragePort={}, articleIngestionPort={}, "
                  + "articleRepository={}, notebookLMService={}, "
                  + "embeddingScheduler={}, articleSearchPort={}",
                  rssHarvestingPort.getClass().getSimpleName(),
                  webPageHarvester.getClass().getSimpleName(),
                  huggingFaceHarvester.getClass().getSimpleName(),
                  articleStoragePort.getClass().getSimpleName(),
                  articleIngestionPort.getClass().getSimpleName(),
                  articleRepository.getClass().getSimpleName(),
                  notebookLMService.getClass().getSimpleName(),
                  embeddingScheduler != null ? embeddingScheduler.getClass().getSimpleName() : "NULL (vector store not configured)",
                  articleSearchPort != null ? articleSearchPort.getClass().getSimpleName() : "NULL (vector store not configured)");
        this.rssHarvestingPort = rssHarvestingPort;
        this.webPageHarvester = webPageHarvester;
        this.huggingFaceHarvester = huggingFaceHarvester;
        this.articleStoragePort = articleStoragePort;
        this.articleIngestionPort = articleIngestionPort;
        this.articleRepository = articleRepository;
        this.notebookLMService = notebookLMService;
        this.embeddingScheduler = embeddingScheduler;
        this.articleSearchPort = articleSearchPort;
    }

    @Override
    public void run(String... args) {
        log.debug("run() | args={}", (Object) args);
        try {
            runPipeline();
        } catch (Exception ex) {
            log.error("========== DEMO PIPELINE FAILED ==========");
            log.error("run() | Pipeline error (app stays alive): {}", ex.getMessage(), ex);
        }
        log.debug("run() | return=void");
    }

    private void runPipeline() throws Exception {
        log.info("========== DEMO PIPELINE START (10 steps) ==========");

        // Step 1: Harvest RSS feeds (ACADEMIC, REGULATORY, INDUSTRY)
        log.info("Step  1/10 | Harvesting RSS feeds (PubMed, FDA, Google News, etc.)...");
        List<NewsArticle> rssArticles;
        try {
            rssArticles = rssHarvestingPort.harvestAll();
            log.info("Step  1/10 | RSS harvest returned {} articles", rssArticles.size());
        } catch (Exception ex) {
            log.error("Step  1/10 | RSS harvest failed: {}", ex.getMessage(), ex);
            rssArticles = List.of();
        }

        // Step 2: Harvest competitor web pages
        log.info("Step  2/10 | Harvesting competitor web pages...");
        List<NewsArticle> webArticles;
        try {
            webArticles = webPageHarvester.harvestChangedPages();
            log.info("Step  2/10 | Web harvest returned {} articles", webArticles.size());
        } catch (Exception ex) {
            log.error("Step  2/10 | Web harvest failed: {}", ex.getMessage(), ex);
            webArticles = List.of();
        }

        // Step 3: Harvest HuggingFace models
        log.info("Step  3/10 | Harvesting HuggingFace healthcare models...");
        List<NewsArticle> hfArticles;
        try {
            hfArticles = huggingFaceHarvester.harvestModels();
            log.info("Step  3/10 | HuggingFace harvest returned {} articles", hfArticles.size());
        } catch (Exception ex) {
            log.error("Step  3/10 | HuggingFace harvest failed: {}", ex.getMessage(), ex);
            hfArticles = List.of();
        }

        // Step 4: Combine and persist
        List<NewsArticle> allHarvested = new ArrayList<>();
        for (NewsArticle a : rssArticles) {
            allHarvested.add(a);
        }
        for (NewsArticle a : webArticles) {
            allHarvested.add(a);
        }
        for (NewsArticle a : hfArticles) {
            allHarvested.add(a);
        }
        log.info("Step  4/10 | Saving {} total harvested articles to DB...", allHarvested.size());
        articleStoragePort.save(allHarvested);
        log.info("Step  4/10 | Save complete (duplicates silently skipped)");

        // Step 4: Query and log DB contents
        log.info("Step  5/10 | Querying database contents...");
        List<NewsArticleEntity> allEntities = articleRepository.findAll();
        log.info("Step  5/10 | Database contains {} article rows", allEntities.size());
        log.info("Step  5/10 | ┌────────────────────────────────────────────────────────────────────────────────");
        log.info("Step  5/10 | │ DB ARTICLE INVENTORY");
        log.info("Step  5/10 | ├────────────────────────────────────────────────────────────────────────────────");
        for (NewsArticleEntity entity : allEntities) {
            String titlePreview = entity.getTitle() != null && entity.getTitle().length() > 60
                    ? entity.getTitle().substring(0, 60) + "..."
                    : entity.getTitle();
            String bodyPreview = entity.getBodyText() != null && entity.getBodyText().length() > 80
                    ? entity.getBodyText().substring(0, 80) + "..."
                    : entity.getBodyText();
            log.info("Step  5/10 | │ id={}", entity.getArticleId());
            log.info("Step  5/10 | │   topic={}  tier={}  source={}",
                     entity.getTopic(), entity.getSourceTier(), entity.getSourceName());
            log.info("Step  5/10 | │   title={}", titlePreview);
            log.info("Step  5/10 | │   url={}", entity.getUrl());
            log.info("Step  5/10 | │   body={}", bodyPreview);
            log.info("Step  5/10 | │   publishedAt={}  createdAt={}", entity.getPublishedAt(), entity.getCreatedAt());
            log.info("Step  5/10 | │");
        }
        log.info("Step  5/10 | └────────────────────────────────────────────────────────────────────────────────");

        // Step 5: Embed all articles into the vector store (OpenAI Embeddings API)
        if (embeddingScheduler != null) {
            log.info("Step  6/10 | Embedding {} articles into vector store via OpenAI...", allEntities.size());
            try {
                embeddingScheduler.embedArticles();
                log.info("Step  6/10 | Embedding complete — articles are now searchable by semantic similarity");
            } catch (Exception ex) {
                log.error("Step  6/10 | Embedding failed: {}", ex.getMessage(), ex);
            }
        } else {
            log.warn("Step  6/10 | SKIPPED — EmbeddingScheduler not available (no VectorStore bean)");
        }

        // Step 6: Run a semantic similarity search against the vector store
        if (articleSearchPort != null) {
            log.info("Step  7/10 | Running vector similarity search: query='AI healthcare clinical trials'...");
            try {
                List<NewsArticle> similar = articleSearchPort.findSimilar("AI healthcare clinical trials", 5);
                log.info("Step  7/10 | Vector search returned {} semantically similar articles", similar.size());
                log.info("Step  7/10 | ┌────────────────────────────────────────────────────────────────────────────────");
                log.info("Step  7/10 | │ VECTOR SIMILARITY RESULTS (top 5)");
                log.info("Step  7/10 | ├────────────────────────────────────────────────────────────────────────────────");
                for (NewsArticle article : similar) {
                    log.info("Step  7/10 | │ id={}  title={}",
                             article.articleId(),
                             article.title() != null && article.title().length() > 70
                                     ? article.title().substring(0, 70) + "..." : article.title());
                    log.info("Step  7/10 | │   topic={}  tier={}", article.topic(), article.sourceTier());
                    log.info("Step  7/10 | │");
                }
                log.info("Step  7/10 | └────────────────────────────────────────────────────────────────────────────────");
            } catch (Exception ex) {
                log.error("Step  7/10 | Vector search failed: {}", ex.getMessage(), ex);
            }
        } else {
            log.warn("Step  7/10 | SKIPPED — ArticleSearchPort not available (no VectorStore bean)");
        }

        // Step 7: Fetch articles back through the ingestion port (broad match)
        log.info("Step  8/10 | Fetching articles from DB via ArticleIngestionPort...");
        List<NewsArticle> fetched = articleIngestionPort.fetchArticles("", 200);
        log.info("Step  8/10 | Ingestion port returned {} articles for broad match", fetched.size());

        if (fetched.isEmpty()) {
            log.warn("Step  8/10 | No articles fetched — trying without topic filter...");
            // Fall back to loading all entities directly
            for (NewsArticleEntity entity : allEntities) {
                fetched.add(toDomainFallback(entity));
            }
            log.info("Step  8/10 | Direct entity load returned {} articles", fetched.size());
        }

        // Step 9: Export individual files to NotebookLM directory
        log.info("Step  9/10 | Exporting {} articles as individual files to NotebookLM directory...", fetched.size());
        Path exportDir = notebookLMService.export("AI Healthcare Demo Round-Trip", fetched);
        log.info("Step  9/10 | NotebookLM export directory: {}", exportDir.toAbsolutePath());

        // Step 10: List and summarize exported files
        log.info("Step 10/10 | Listing exported files...");
        log.info("Step 10/10 | ┌────────────────────────────────────────────────────────────────────────────────");
        log.info("Step 10/10 | │ NOTEBOOKLM EXPORTED FILES");
        log.info("Step 10/10 | ├────────────────────────────────────────────────────────────────────────────────");
        java.io.File[] exportedFiles = exportDir.toFile().listFiles((d, name) -> name.endsWith(".txt"));
        int fileCount = 0;
        if (exportedFiles != null) {
            for (java.io.File file : exportedFiles) {
                fileCount++;
                long sizeBytes = file.length();
                log.info("Step 10/10 | │ [{}] {} ({} bytes)", fileCount, file.getName(), sizeBytes);
            }
        }
        log.info("Step 10/10 | ├────────────────────────────────────────────────────────────────────────────────");
        log.info("Step 10/10 | │ Total: {} .txt files in {}", fileCount, exportDir.toAbsolutePath());
        log.info("Step 10/10 | └────────────────────────────────────────────────────────────────────────────────");

        log.info("========== DEMO PIPELINE COMPLETE ==========");
        log.info("Summary: {} articles harvested, {} in DB, {} files exported to {}",
                 allHarvested.size(), allEntities.size(), fileCount,
                 exportDir.toAbsolutePath());
    }

    /**
     * Emergency fallback: converts an entity to a domain record when the
     * ingestion port returns nothing (e.g. topic filter mismatch).
     */
    private NewsArticle toDomainFallback(NewsArticleEntity entity) {
        log.debug("toDomainFallback() | articleId={}", entity.getArticleId());
        java.net.URI url;
        try {
            url = entity.getUrl() != null && !entity.getUrl().isBlank()
                    ? java.net.URI.create(entity.getUrl())
                    : java.net.URI.create("");
        } catch (IllegalArgumentException ex) {
            url = java.net.URI.create("");
        }
        NewsArticle result = new NewsArticle(
                entity.getArticleId(),
                entity.getTitle(),
                url,
                entity.getBodyText(),
                entity.getTopic(),
                entity.getAuthor(),
                entity.getTopicId(),
                entity.getSourceName(),
                entity.getSourceTier(),
                entity.getSourceWeight(),
                entity.getPublishedAt()
        );
        log.debug("toDomainFallback() | return={}", result.articleId());
        return result;
    }
}
