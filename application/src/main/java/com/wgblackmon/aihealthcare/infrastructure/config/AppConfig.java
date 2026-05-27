package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalyticsPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import com.wgblackmon.aihealthcare.domain.model.TierLimits;
import com.wgblackmon.aihealthcare.domain.service.AnalyticsService;
import com.wgblackmon.aihealthcare.domain.service.CitationAssembler;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterTeaserBuilder;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService;
import com.wgblackmon.aihealthcare.domain.service.MarketIntelligenceService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.service.PromptEvaluationService;
import com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService;
import com.wgblackmon.aihealthcare.domain.service.ResearchPlanningService;
import com.wgblackmon.aihealthcare.domain.service.ResearchSynthesisService;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.domain.service.VendorAssessmentService;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.EvaluationResultPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PromptVariantPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchExportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;
import com.wgblackmon.aihealthcare.infrastructure.research.LegacyGoogleResearchAdapter;
import com.wgblackmon.aihealthcare.infrastructure.research.PerplexityResearchAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collections;
import java.util.List;

/**
 * Spring configuration class that wires the application-layer service as a bean.
 *
 * <p><b>Why is NewsletterService not annotated with @Service?</b>
 * {@link NewsletterService} lives in the application layer, which is intentionally
 * free of Spring annotations.  This keeps the application layer testable without a
 * Spring context — unit tests construct it directly with mock ports and a real
 * {@link NewsletterRenderer}, no framework involved.
 *
 * <p>This {@code @Configuration} class acts as the wiring bridge: it lives in
 * {@code infrastructure/config} (where Spring is allowed), pulls in the outbound-port
 * adapters that Spring has auto-detected via {@code @Component}, constructs a
 * stateless {@link NewsletterRenderer}, and manually builds the {@link NewsletterService}.
 *
 * <p>{@code @EnableScheduling} activates {@code @Scheduled} methods in
 * {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedHarvestScheduler}.
 * {@code @EnableConfigurationProperties} registers {@link FeedSourceProperties}
 * for {@code @ConfigurationProperties} binding.
 *
 * <p>The {@code PgVectorStore} bean is auto-configured by the
 * {@code spring-ai-starter-vector-store-pgvector} starter — no manual bean
 * registration is needed here.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-05-26
 */

@Slf4j
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Creates the {@link NewsletterTeaserBuilder} that truncates newsletter content
     * for FREE-tier subscribers.  The CTA wording adapts to the configured
     * {@code aihealthcare.articles.days-back} value.
     *
     * @param daysBack article look-back window from configuration.
     * @return The wired {@link NewsletterTeaserBuilder} instance.
     */
    @Bean
    public NewsletterTeaserBuilder newsletterTeaserBuilder(
            @Value("${aihealthcare.articles.days-back:1}") int daysBack) {
        log.debug("newsletterTeaserBuilder() | daysBack={}", daysBack);
        NewsletterTeaserBuilder result = new NewsletterTeaserBuilder(daysBack);
        log.debug("newsletterTeaserBuilder() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TierGatingService} that evaluates feature access and
     * usage limits per subscription tier.
     *
     * @param props Tier limit configuration from {@code aihealthcare.tiers.*}.
     * @return The wired {@link TierGatingService} instance.
     */
    @Bean
    public TierGatingService tierGatingService(TierLimitProperties props) {
        log.debug("tierGatingService() | freeLimits=[archive={}, queries={}], memberLimits=[archive={}, queries={}]",
                  props.getFree().getArchiveDays(), props.getFree().getMonthlyQueryLimit(),
                  props.getMember().getArchiveDays(), props.getMember().getMonthlyQueryLimit());
        TierLimits freeLimits = new TierLimits(
                props.getFree().getArchiveDays(),
                props.getFree().getMonthlyQueryLimit());
        TierLimits memberLimits = new TierLimits(
                props.getMember().getArchiveDays(),
                props.getMember().getMonthlyQueryLimit());
        TierGatingService result = new TierGatingService(freeLimits, memberLimits);
        log.debug("tierGatingService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DeliveryService} instance that implements subscriber management
     * and tier-aware newsletter delivery.
     *
     * @param subscriberPort Adapter implementing subscriber persistence (auto-detected).
     * @param teaserBuilder  Builder for FREE-tier teaser content.
     * @return The wired {@link DeliveryService} instance.
     */
    @Bean
    public DeliveryService deliveryService(SubscriberPort subscriberPort,
                                           NewsletterRunPort newsletterRunPort,
                                           NewsletterDeliveryPort newsletterDeliveryPort,
                                           NewsletterTeaserBuilder teaserBuilder) {
        log.debug("deliveryService() | subscriberPort={}, newsletterRunPort={}, newsletterDeliveryPort={}, teaserBuilder={}",
                  subscriberPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  newsletterDeliveryPort.getClass().getSimpleName(),
                  teaserBuilder.getClass().getSimpleName());
        DeliveryService result = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort, teaserBuilder);
        log.debug("deliveryService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * No-op fallback {@link ArticleSearchPort} registered only when the pgvector
     * {@link com.wgblackmon.aihealthcare.infrastructure.ai.VectorStoreArticleSearchAdapter}
     * is absent (e.g. h2 profile).  RAG features degrade gracefully to empty context.
     *
     * @return a port that always returns an empty list
     */
    @Bean
    @ConditionalOnMissingBean(ArticleSearchPort.class)
    public ArticleSearchPort noOpArticleSearchPort() {
        log.debug("noOpArticleSearchPort() | pgvector not configured — RAG disabled");
        ArticleSearchPort result = (query, topK) -> Collections.emptyList();
        log.debug("noOpArticleSearchPort() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the single {@link NewsletterService} instance shared across the application.
     *
     * <p>Spring injects the {@code @Component}-annotated adapters automatically.
     * {@link NewsletterRenderer} is stateless and constructed inline — it requires
     * no Spring lifecycle management.
     *
     * @param ingestionPort      Adapter implementing article fetching (auto-detected).
     * @param summarizationPort  Adapter implementing AI summarization (auto-detected).
     * @param newsletterRunPort  Adapter implementing run persistence (auto-detected).
     * @param searchPort         Adapter implementing vector-store similarity search (auto-detected).
     * @return The wired {@link NewsletterService} instance.
     */
    @Bean
    public NewsletterService newsletterService(ArticleIngestionPort ingestionPort,
                                               AiSummarizationPort summarizationPort,
                                               NewsletterRunPort newsletterRunPort,
                                               ArticleSearchPort searchPort) {
        log.debug("newsletterService() | ingestionPort={}, summarizationPort={}, newsletterRunPort={}, searchPort={}",
                  ingestionPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  searchPort.getClass().getSimpleName());
        NewsletterRenderer renderer = new NewsletterRenderer();
        NewsletterService result = new NewsletterService(
                ingestionPort, summarizationPort, renderer, newsletterRunPort, searchPort);
        log.debug("newsletterService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link PromptEvaluationService} instance that implements
     * the prompt evaluation and comparison workflow.
     *
     * @param variantPort       Adapter implementing variant persistence (auto-detected).
     * @param summarizationPort Adapter implementing AI summarization (auto-detected).
     * @param evaluationPort    Adapter implementing AI evaluation (auto-detected).
     * @param ingestionPort     Adapter implementing article fetching (auto-detected).
     * @param resultPort        Adapter implementing evaluation result persistence (auto-detected).
     * @return The wired {@link PromptEvaluationService} instance.
     */
    @Bean
    public PromptEvaluationService promptEvaluationService(
            PromptVariantPort variantPort,
            AiSummarizationPort summarizationPort,
            AiEvaluationPort evaluationPort,
            ArticleIngestionPort ingestionPort,
            EvaluationResultPort resultPort) {
        log.debug("promptEvaluationService() | variantPort={}, summarizationPort={}, "
                  + "evaluationPort={}, ingestionPort={}, resultPort={}",
                  variantPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName(),
                  evaluationPort.getClass().getSimpleName(),
                  ingestionPort.getClass().getSimpleName(),
                  resultPort.getClass().getSimpleName());
        PromptEvaluationService result = new PromptEvaluationService(
                variantPort, summarizationPort, evaluationPort, ingestionPort, resultPort);
        log.debug("promptEvaluationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DocumentIngestionService} when a {@link DocumentVectorPort}
     * bean is available (i.e., when pgvector is configured).
     *
     * <p>Spring injects all {@link FileParserPort} {@code @Component} beans as a list
     * automatically, so adding a new parser requires no change to this method.
     *
     * @param parsers     All registered file-parser adapters (PDF, DOCX, plain-text).
     * @param vectorPort  Adapter implementing vector-store writes (auto-detected).
     * @return The wired {@link DocumentIngestionService} instance.
     */
    @Bean
    @ConditionalOnBean(DocumentVectorPort.class)
    public DocumentIngestionService documentIngestionService(List<FileParserPort> parsers,
                                                              DocumentVectorPort vectorPort) {
        log.debug("documentIngestionService() | parserCount={}, vectorPort={}",
                  parsers.size(), vectorPort.getClass().getSimpleName());
        DocumentIngestionService result = new DocumentIngestionService(parsers, vectorPort);
        log.debug("documentIngestionService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * No-op fallback {@link IngestDocumentsUseCase} used when pgvector is not configured.
     * Returns a result indicating that document ingestion is unavailable in this environment.
     *
     * @return a use case that always returns a descriptive failure result
     */
    @Bean
    @ConditionalOnMissingBean(IngestDocumentsUseCase.class)
    public IngestDocumentsUseCase noOpIngestDocumentsUseCase() {
        log.debug("noOpIngestDocumentsUseCase() | pgvector not configured — document ingestion disabled");
        IngestDocumentsUseCase result = (directory, sourceLabel, chunkSize) ->
                new DocumentIngestionResult(0, 0,
                        List.of("Document ingestion is unavailable — pgvector is not configured in this environment."));
        log.debug("noOpIngestDocumentsUseCase() | return=lambda");
        return result;
    }

    /**
     * Creates the {@link MarketIntelligenceService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.GenerateMarketIntelligenceUseCase}.
     *
     * @param searchPromptPort Port for loading the configured prompt template (auto-detected).
     * @param aiReportPort     Port for sending the prompt to the AI model (auto-detected).
     * @return The wired {@link MarketIntelligenceService} instance.
     */
    @Bean
    public MarketIntelligenceService marketIntelligenceService(SearchPromptPort searchPromptPort,
                                                                AiReportPort aiReportPort) {
        log.debug("marketIntelligenceService() | searchPromptPort={}, aiReportPort={}",
                  searchPromptPort.getClass().getSimpleName(),
                  aiReportPort.getClass().getSimpleName());
        MarketIntelligenceService result = new MarketIntelligenceService(searchPromptPort, aiReportPort);
        log.debug("marketIntelligenceService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link AnalyticsService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase}.
     *
     * @param analyticsPort Adapter implementing aggregate DB queries (auto-detected).
     * @return The wired {@link AnalyticsService} instance.
     */
    @Bean
    public AnalyticsService analyticsService(AnalyticsPort analyticsPort) {
        log.debug("analyticsService() | analyticsPort={}", analyticsPort.getClass().getSimpleName());
        AnalyticsService result = new AnalyticsService(analyticsPort);
        log.debug("analyticsService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link ResearchOrchestratorService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase}.
     *
     * <p>Prompt templates for the planning and synthesis services are loaded once at
     * startup via {@link PromptLoaderService}.  Both adapters are injected by name
     * to avoid ambiguity (both implement {@link SourceRetrievalPort}).
     *
     * @param researchModeStr         Configured default mode from
     *                                {@code aihealthcare.research.mode}.
     * @param aiReportPort            Generic AI prompt→text port (auto-detected).
     * @param promptLoaderService     Template loader (auto-detected).
     * @param legacyGoogleAdapter     Legacy ingestion-backed retrieval adapter.
     * @param perplexityAdapter       Perplexity-backed retrieval adapter.
     * @param articleStoragePort      Port for persisting Perplexity-sourced articles.
     * @param researchRunPort         Port for persisting research run audit records.
     * @param researchExportPort      Port for exporting articles to the NotebookLM corpus.
     * @return The wired {@link ResearchOrchestratorService} instance.
     */
    @Bean
    public ResearchOrchestratorService researchOrchestratorService(
            @Value("${aihealthcare.research.mode:LEGACY_GOOGLE}") String researchModeStr,
            AiReportPort aiReportPort,
            PromptLoaderService promptLoaderService,
            LegacyGoogleResearchAdapter legacyGoogleAdapter,
            PerplexityResearchAdapter perplexityAdapter,
            ArticleStoragePort articleStoragePort,
            ResearchRunPort researchRunPort,
            ResearchExportPort researchExportPort) {

        log.debug("researchOrchestratorService() | researchMode={}", researchModeStr);

        ResearchMode defaultMode;
        try {
            defaultMode = ResearchMode.valueOf(researchModeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("researchOrchestratorService() | Unrecognised mode '{}' — defaulting to LEGACY_GOOGLE",
                     researchModeStr);
            defaultMode = ResearchMode.LEGACY_GOOGLE;
        }

        String planTemplate         = promptLoaderService.load("research-plan.txt");
        String synthesisTemplate    = promptLoaderService.load("research-synthesis.txt");
        String vendorCompareTemplate = promptLoaderService.load("vendor-compare.txt");

        CitationAssembler        citationAssembler    = new CitationAssembler();
        ResearchPlanningService  planningService      = new ResearchPlanningService(aiReportPort, planTemplate);
        ResearchSynthesisService synthesisService     = new ResearchSynthesisService(aiReportPort, synthesisTemplate);
        VendorAssessmentService  vendorAssessmentService = new VendorAssessmentService(aiReportPort, vendorCompareTemplate);

        ResearchOrchestratorService result = new ResearchOrchestratorService(
                defaultMode, legacyGoogleAdapter, perplexityAdapter,
                planningService, synthesisService, citationAssembler,
                articleStoragePort, researchRunPort, researchExportPort,
                vendorAssessmentService);

        log.debug("researchOrchestratorService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TopicSummaryGenerationService} bean that generates
     * 3-sentence AI summaries for topic sections on the news listing page.
     *
     * @param aiPort        Adapter implementing AI summarization (auto-detected).
     * @param summaryPort   Adapter implementing topic summary persistence (auto-detected).
     * @param ingestionPort Adapter implementing article fetching (auto-detected).
     * @return The wired {@link TopicSummaryGenerationService} instance.
     */
    @Bean
    public TopicSummaryGenerationService topicSummaryGenerationService(
            AiSummarizationPort aiPort,
            TopicSummaryPort summaryPort,
            ArticleIngestionPort ingestionPort) {
        log.debug("topicSummaryGenerationService() | aiPort={}, summaryPort={}, ingestionPort={}",
                  aiPort.getClass().getSimpleName(),
                  summaryPort.getClass().getSimpleName(),
                  ingestionPort.getClass().getSimpleName());
        TopicSummaryGenerationService result =
                new TopicSummaryGenerationService(aiPort, summaryPort, ingestionPort);
        log.debug("topicSummaryGenerationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Designates the Anthropic {@link ChatModel} as the primary chat model.
     *
     * <p>Both {@code spring-ai-starter-model-anthropic} and
     * {@code spring-ai-starter-model-openai} register a {@link ChatModel} bean.
     * OpenAI is on the classpath solely for its {@code EmbeddingModel} (used by
     * PgVector); Anthropic is the intended LLM for all chat/summarization work.
     * Marking it {@code @Primary} resolves the ambiguity for
     * {@link org.springframework.ai.chat.client.ChatClient.Builder} injection.
     *
     * @param anthropicChatModel the auto-configured Anthropic chat model
     * @return the same instance, now marked as primary
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {
        log.debug("primaryChatModel() | anthropicChatModel={}", anthropicChatModel.getClass().getSimpleName());
        return anthropicChatModel;
    }
}
