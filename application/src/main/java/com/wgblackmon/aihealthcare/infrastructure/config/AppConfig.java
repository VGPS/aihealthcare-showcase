package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestDocumentsUseCase;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FileParserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalyticsPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import com.wgblackmon.aihealthcare.domain.model.TierLimits;
import com.wgblackmon.aihealthcare.domain.port.outbound.AdminNotificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.domain.service.AiSearchService;
import com.wgblackmon.aihealthcare.domain.service.AnalyticsService;
import com.wgblackmon.aihealthcare.domain.service.ArticleSearchService;
import com.wgblackmon.aihealthcare.domain.service.CitationAssembler;
import com.wgblackmon.aihealthcare.domain.service.CompanyClassifier;
import com.wgblackmon.aihealthcare.domain.service.CompanyDeduplicator;
import com.wgblackmon.aihealthcare.domain.service.CompanyDiscoveryService;
import com.wgblackmon.aihealthcare.domain.service.CompanyNewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyScrapingPort;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.DigestNewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterTeaserBuilder;
import com.wgblackmon.aihealthcare.domain.service.RegistrationService;
import com.wgblackmon.aihealthcare.domain.port.outbound.DailySummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService;
import com.wgblackmon.aihealthcare.domain.service.MarketIntelligenceService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.service.ReversalWatchSectionBuilder;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.PromptEvaluationService;
import com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService;
import com.wgblackmon.aihealthcare.domain.service.ResearchPlanningService;
import com.wgblackmon.aihealthcare.domain.service.ResearchSynthesisService;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.domain.service.VendorAssessmentService;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialService;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialWatchlistMatcher;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryEventService;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryWatchlistMatcher;
import com.wgblackmon.aihealthcare.domain.service.WatchlistMatchingService;
import com.wgblackmon.aihealthcare.domain.service.TrendDetectionService;
import com.wgblackmon.aihealthcare.domain.service.TrendOrchestrationService;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchQueryPort;
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
import com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort;
import com.wgblackmon.aihealthcare.infrastructure.ai.WikiCompilationAdapter;
import com.wgblackmon.aihealthcare.infrastructure.ai.WikiResponseParser;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiSourceRefRepository;
import com.wgblackmon.aihealthcare.infrastructure.research.LegacyGoogleResearchAdapter;
import com.wgblackmon.aihealthcare.infrastructure.research.PerplexityResearchAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.cache.annotation.EnableCaching;
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
 * @updated 2026-07-20
 */

@Slf4j
@Configuration
@EnableCaching
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
        log.debug("tierGatingService() | freeLimits=[archive={}, queries={}], subscriberLimits=[archive={}, queries={}], "
                  + "demoLimits=[archive={}, queries={}], freePendingLimits=[archive={}, queries={}]",
                  props.getFree().getArchiveDays(), props.getFree().getMonthlyQueryLimit(),
                  props.getSubscriber().getArchiveDays(), props.getSubscriber().getMonthlyQueryLimit(),
                  props.getDemo().getArchiveDays(), props.getDemo().getMonthlyQueryLimit(),
                  props.getFreePending().getArchiveDays(), props.getFreePending().getMonthlyQueryLimit());
        TierLimits freeLimits = new TierLimits(
                props.getFree().getArchiveDays(),
                props.getFree().getMonthlyQueryLimit());
        TierLimits subscriberLimits = new TierLimits(
                props.getSubscriber().getArchiveDays(),
                props.getSubscriber().getMonthlyQueryLimit());
        TierLimits demoLimits = new TierLimits(
                props.getDemo().getArchiveDays(),
                props.getDemo().getMonthlyQueryLimit());
        TierLimits freePendingLimits = new TierLimits(
                props.getFreePending().getArchiveDays(),
                props.getFreePending().getMonthlyQueryLimit());
        TierGatingService result = new TierGatingService(freeLimits, subscriberLimits, demoLimits, freePendingLimits);
        log.debug("tierGatingService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link RegistrationService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.RegisterUserUseCase}.
     *
     * @param appUserPort         Adapter implementing app user persistence (auto-detected).
     * @param subscriberPort      Adapter implementing subscriber persistence (auto-detected).
     * @param passwordHashingPort Adapter implementing password hashing (auto-detected).
     * @return The wired {@link RegistrationService} instance.
     */
    @Bean
    public RegistrationService registrationService(AppUserPort appUserPort,
                                                   SubscriberPort subscriberPort,
                                                   PasswordHashingPort passwordHashingPort) {
        log.debug("registrationService() | appUserPort={}, subscriberPort={}, passwordHashingPort={}",
                  appUserPort.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  passwordHashingPort.getClass().getSimpleName());
        RegistrationService result = new RegistrationService(appUserPort, subscriberPort, passwordHashingPort);
        log.debug("registrationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Registers the {@link DemoExpirationFilter} as a servlet filter that runs
     * after the Spring Security filter chain.
     *
     * <p>This filter checks DEMO users on each request and transitions expired
     * demos to FREE_PENDING.  Registered via {@link FilterRegistrationBean}
     * rather than in {@link SecurityConfig} to avoid adding port dependencies
     * to the security configuration (which would impact all 35+ WebMvcTest classes).
     *
     * @param appUserPort    Adapter implementing app user persistence (auto-detected).
     * @param subscriberPort Adapter implementing subscriber persistence (auto-detected).
     * @return The filter registration bean.
     */
    @Bean
    public FilterRegistrationBean<DemoExpirationFilter> demoExpirationFilter(
            AppUserPort appUserPort,
            SubscriberPort subscriberPort) {
        log.debug("demoExpirationFilter() | appUserPort={}, subscriberPort={}",
                  appUserPort.getClass().getSimpleName(), subscriberPort.getClass().getSimpleName());

        DemoExpirationFilter filter = new DemoExpirationFilter(appUserPort, subscriberPort);
        FilterRegistrationBean<DemoExpirationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER + 1);

        log.debug("demoExpirationFilter() | return=FilterRegistrationBean");
        return registration;
    }

    /**
     * Creates the {@link DigestNewsletterRenderer} that builds the FREE-tier
     * daily article digest by wrapping NotebookLM summary files in an email layout.
     *
     * @param dailySummaryPort Adapter that reads daily summary files (auto-detected).
     * @return The wired {@link DigestNewsletterRenderer} instance.
     */
    @Bean
    public DigestNewsletterRenderer digestNewsletterRenderer(DailySummaryPort dailySummaryPort) {
        log.debug("digestNewsletterRenderer() | dailySummaryPort={}", dailySummaryPort.getClass().getSimpleName());
        DigestNewsletterRenderer result = new DigestNewsletterRenderer(dailySummaryPort);
        log.debug("digestNewsletterRenderer() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DeliveryService} instance that implements subscriber management
     * and 4-tier newsletter delivery (SUBSCRIBER/DEMO=full, FREE=digest, FREE_PENDING=skip).
     *
     * @param subscriberPort  Adapter implementing subscriber persistence (auto-detected).
     * @param teaserBuilder   Builder for legacy teaser content.
     * @param digestRenderer  Renderer for FREE-tier daily digest.
     * @return The wired {@link DeliveryService} instance.
     */
    @Bean
    public DeliveryService deliveryService(SubscriberPort subscriberPort,
                                           NewsletterRunPort newsletterRunPort,
                                           NewsletterDeliveryPort newsletterDeliveryPort,
                                           NewsletterTeaserBuilder teaserBuilder,
                                           DigestNewsletterRenderer digestRenderer) {
        log.debug("deliveryService() | subscriberPort={}, newsletterRunPort={}, newsletterDeliveryPort={}, teaserBuilder={}, digestRenderer={}",
                  subscriberPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  newsletterDeliveryPort.getClass().getSimpleName(),
                  teaserBuilder.getClass().getSimpleName(),
                  digestRenderer.getClass().getSimpleName());
        DeliveryService result = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort, teaserBuilder, digestRenderer);
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
                                               ArticleSearchPort searchPort,
                                               WikiQueryPort wikiQueryPort) {
        log.debug("newsletterService() | ingestionPort={}, summarizationPort={}, newsletterRunPort={}, searchPort={}, wikiQueryPort={}",
                  ingestionPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  searchPort.getClass().getSimpleName(),
                  wikiQueryPort.getClass().getSimpleName());
        NewsletterRenderer renderer = new NewsletterRenderer();
        ReversalWatchSectionBuilder reversalWatchBuilder = new ReversalWatchSectionBuilder();
        NewsletterService result = new NewsletterService(
                ingestionPort, summarizationPort, renderer, newsletterRunPort, searchPort,
                wikiQueryPort, reversalWatchBuilder);
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
     * Creates the {@link ArticleSearchService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase}.
     *
     * @param queryPort Adapter implementing criteria-based article search (auto-detected).
     * @return The wired {@link ArticleSearchService} instance.
     */
    @Bean
    public ArticleSearchService articleSearchService(ArticleSearchQueryPort queryPort) {
        log.debug("articleSearchService() | queryPort={}", queryPort.getClass().getSimpleName());
        ArticleSearchService result = new ArticleSearchService(queryPort);
        log.debug("articleSearchService() | return={}", result.getClass().getSimpleName());
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
     * Creates the {@link AiSearchService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase}.
     *
     * <p>Spring injects all {@link AiSearchPort} {@code @Component} beans as a list
     * automatically (one per model — Anthropic, OpenAI, Perplexity, Gemini, AWS),
     * enabling multi-model synthesis. The {@link AdminNotificationPort} receives
     * alerts when a model synthesis call fails.
     *
     * @param articleSearchPort      vector-store search port (auto-detected).
     * @param aiSearchPorts          all registered AI search adapters (auto-detected).
     * @param adminNotificationPort  admin notification port for model failure alerts.
     * @return The wired {@link AiSearchService} instance.
     */
    @Bean
    public AiSearchService aiSearchService(ArticleSearchPort articleSearchPort,
                                           List<AiSearchPort> aiSearchPorts,
                                           AdminNotificationPort adminNotificationPort) {
        log.debug("aiSearchService() | articleSearchPort={}, aiSearchPortCount={}, adminNotifier={}",
                  articleSearchPort.getClass().getSimpleName(), aiSearchPorts.size(),
                  adminNotificationPort.getClass().getSimpleName());
        AiSearchService result = new AiSearchService(articleSearchPort, aiSearchPorts, adminNotificationPort);
        log.debug("aiSearchService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wraps the {@link AiSearchService} with a Caffeine TTL cache to reduce
     * redundant AI API calls for repeated queries.
     *
     * @param aiSearchService the raw domain search service.
     * @return a caching decorator implementing {@link ConductAiSearchUseCase}.
     */
    @Bean
    @Primary
    public CachingAiSearchDecorator cachingAiSearchDecorator(AiSearchService aiSearchService) {
        log.debug("cachingAiSearchDecorator() | wrapping AiSearchService");
        CachingAiSearchDecorator result = new CachingAiSearchDecorator(aiSearchService);
        log.debug("cachingAiSearchDecorator() | return={}", result.getClass().getSimpleName());
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
    /**
     * Creates the {@link CompanyDiscoveryService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase}.
     *
     * @param scrapingPort  Adapter implementing startup directory scraping (auto-detected).
     * @param storagePort   Adapter implementing article persistence (auto-detected).
     * @return The wired {@link CompanyDiscoveryService} instance.
     */
    @Bean
    public CompanyDiscoveryService companyDiscoveryService(
            CompanyScrapingPort scrapingPort,
            ArticleStoragePort storagePort) {
        log.debug("companyDiscoveryService() | scrapingPort={}, storagePort={}",
                  scrapingPort.getClass().getSimpleName(),
                  storagePort.getClass().getSimpleName());
        CompanyDiscoveryService result = new CompanyDiscoveryService(
                scrapingPort, storagePort,
                new CompanyClassifier(),
                new CompanyDeduplicator(),
                new CompanyNewsletterRenderer());
        log.debug("companyDiscoveryService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TrendDetectionService} bean — a stateless, pure-Java domain
     * service that performs keyword frequency analysis across rolling time windows.
     *
     * @param minOccurrences minimum keyword occurrences to be included in analysis
     * @param risingLimit    maximum rising signals to return
     * @param fadingLimit    maximum fading signals to return
     * @return The wired {@link TrendDetectionService} instance.
     */
    @Bean
    public TrendDetectionService trendDetectionService(
            @Value("${aihealthcare.trends.min-occurrences:3}") int minOccurrences,
            @Value("${aihealthcare.trends.rising-limit:20}") int risingLimit,
            @Value("${aihealthcare.trends.fading-limit:10}") int fadingLimit) {
        log.debug("trendDetectionService() | minOccurrences={}, risingLimit={}, fadingLimit={}",
                  minOccurrences, risingLimit, fadingLimit);
        TrendDetectionService result = new TrendDetectionService(minOccurrences, risingLimit, fadingLimit);
        log.debug("trendDetectionService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TrendOrchestrationService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase}.
     *
     * @param articleIngestionPort    Adapter implementing article fetching (auto-detected).
     * @param trendDetectionService   The trend analysis engine.
     * @param trendSnapshotPort       Adapter implementing snapshot persistence (auto-detected).
     * @return The wired {@link TrendOrchestrationService} instance.
     */
    @Bean
    public TrendOrchestrationService trendOrchestrationService(
            ArticleIngestionPort articleIngestionPort,
            TrendDetectionService trendDetectionService,
            TrendSnapshotPort trendSnapshotPort) {
        log.debug("trendOrchestrationService() | articleIngestionPort={}, trendDetectionService={}, trendSnapshotPort={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  trendDetectionService.getClass().getSimpleName(),
                  trendSnapshotPort.getClass().getSimpleName());
        TrendOrchestrationService result = new TrendOrchestrationService(
                articleIngestionPort, trendDetectionService, trendSnapshotPort);
        log.debug("trendOrchestrationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link CompanyProfileService} bean — a stateless, pure-Java domain
     * service that manages company intelligence profiles and event detection.
     *
     * @return The wired {@link CompanyProfileService} instance.
     */
    @Bean
    public CompanyProfileService companyProfileService() {
        log.debug("companyProfileService() | creating stateless service");
        CompanyProfileService result = new CompanyProfileService();
        log.debug("companyProfileService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link WatchlistMatchingService} bean — a stateless, pure-Java
     * domain service that matches incoming articles against subscriber watchlist items.
     *
     * @return The wired {@link WatchlistMatchingService} instance.
     */
    @Bean
    public WatchlistMatchingService watchlistMatchingService() {
        log.debug("watchlistMatchingService() | creating stateless service");
        WatchlistMatchingService result = new WatchlistMatchingService();
        log.debug("watchlistMatchingService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link WikiLintService} bean — a stateless, pure-Java domain
     * service that performs automated quality checks on the wiki knowledge base.
     *
     * @return The wired {@link WikiLintService} instance.
     */
    @Bean
    public WikiLintService wikiLintService() {
        log.debug("wikiLintService() | creating stateless lint service");
        WikiLintService result = new WikiLintService();
        log.debug("wikiLintService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link WikiCompilationAdapter} bean that implements
     * {@link KnowledgeCompilationPort} — the LLM-powered wiki compilation pipeline.
     *
     * <p>Loads the {@code wiki-compile.txt} prompt template at startup.
     * The adapter receives the auto-configured {@link ChatClient.Builder}
     * and all wiki repository beans via constructor injection.
     *
     * @param chatClientBuilder       Auto-configured ChatClient builder.
     * @param promptLoaderService     Template loader for prompt files.
     * @param pageRepository          Wiki page persistence.
     * @param sourceRefRepository     Source ref persistence.
     * @param contradictionRepository Contradiction persistence.
     * @param revisionRepository      Revision audit trail persistence.
     * @return The wired {@link WikiCompilationAdapter} instance.
     */
    @Bean
    public WikiCompilationAdapter wikiCompilationAdapter(
            ChatClient.Builder chatClientBuilder,
            PromptLoaderService promptLoaderService,
            WikiPageRepository pageRepository,
            WikiSourceRefRepository sourceRefRepository,
            WikiContradictionRepository contradictionRepository,
            WikiPageRevisionRepository revisionRepository) {
        log.debug("wikiCompilationAdapter() | wiring wiki compilation pipeline");
        String wikiCompilePrompt = promptLoaderService.load("wiki-compile.txt");
        WikiResponseParser responseParser = new WikiResponseParser();
        WikiCompilationAdapter result = new WikiCompilationAdapter(
                chatClientBuilder, pageRepository, sourceRefRepository,
                contradictionRepository, revisionRepository,
                responseParser, wikiCompilePrompt);
        log.debug("wikiCompilationAdapter() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link RegulatoryEventService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase}.
     *
     * @param regulatoryEventPort      Adapter implementing regulatory event persistence (auto-detected).
     * @param regulatoryHarvestingPort  Adapter implementing regulatory harvesting (auto-detected).
     * @return The wired {@link RegulatoryEventService} instance.
     */
    @Bean
    public RegulatoryEventService regulatoryEventService(
            RegulatoryEventPort regulatoryEventPort,
            RegulatoryHarvestingPort regulatoryHarvestingPort) {
        log.debug("regulatoryEventService() | regulatoryEventPort={}, regulatoryHarvestingPort={}",
                  regulatoryEventPort.getClass().getSimpleName(),
                  regulatoryHarvestingPort.getClass().getSimpleName());
        RegulatoryEventService result = new RegulatoryEventService(regulatoryEventPort, regulatoryHarvestingPort);
        log.debug("regulatoryEventService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link RegulatoryWatchlistMatcher} bean — a stateless, pure-Java
     * domain service that matches regulatory events against subscriber watchlist items.
     *
     * @return The wired {@link RegulatoryWatchlistMatcher} instance.
     */
    @Bean
    public RegulatoryWatchlistMatcher regulatoryWatchlistMatcher() {
        log.debug("regulatoryWatchlistMatcher() | creating stateless service");
        RegulatoryWatchlistMatcher result = new RegulatoryWatchlistMatcher();
        log.debug("regulatoryWatchlistMatcher() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link ClinicalTrialService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.MonitorClinicalTrialsUseCase}.
     *
     * @param clinicalTrialPort          Adapter implementing clinical trial persistence (auto-detected).
     * @param clinicalTrialHarvestingPort Adapter implementing clinical trial harvesting (auto-detected).
     * @return The wired {@link ClinicalTrialService} instance.
     */
    @Bean
    public ClinicalTrialService clinicalTrialService(
            ClinicalTrialPort clinicalTrialPort,
            ClinicalTrialHarvestingPort clinicalTrialHarvestingPort) {
        log.debug("clinicalTrialService() | clinicalTrialPort={}, clinicalTrialHarvestingPort={}",
                  clinicalTrialPort.getClass().getSimpleName(),
                  clinicalTrialHarvestingPort.getClass().getSimpleName());
        ClinicalTrialService result = new ClinicalTrialService(clinicalTrialPort, clinicalTrialHarvestingPort);
        log.debug("clinicalTrialService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link ClinicalTrialWatchlistMatcher} bean — a stateless, pure-Java
     * domain service that matches clinical trials against subscriber watchlist items.
     *
     * @return The wired {@link ClinicalTrialWatchlistMatcher} instance.
     */
    @Bean
    public ClinicalTrialWatchlistMatcher clinicalTrialWatchlistMatcher() {
        log.debug("clinicalTrialWatchlistMatcher() | creating stateless service");
        ClinicalTrialWatchlistMatcher result = new ClinicalTrialWatchlistMatcher();
        log.debug("clinicalTrialWatchlistMatcher() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {
        log.debug("primaryChatModel() | anthropicChatModel={}", anthropicChatModel.getClass().getSimpleName());
        return anthropicChatModel;
    }
}
