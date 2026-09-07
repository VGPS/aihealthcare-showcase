package com.wgblackmon.aihealthcare.infrastructure.config;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.wgblackmon.aihealthcare.domain.service.IntelReportService;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.IntelReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyScrapingPort;
import com.wgblackmon.aihealthcare.domain.service.ArticleQualityFilter;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.DigestNewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterTeaserBuilder;
import com.wgblackmon.aihealthcare.domain.service.SampleNewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.RegistrationService;
import com.wgblackmon.aihealthcare.domain.service.PasswordResetService;

import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordResetPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import com.wgblackmon.aihealthcare.domain.service.DocumentIngestionService;
import com.wgblackmon.aihealthcare.domain.service.DocumentUploadService;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.service.LegalBriefSectionBuilder;
import com.wgblackmon.aihealthcare.domain.service.ReversalWatchSectionBuilder;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.PromptEvaluationService;
import com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService;
import com.wgblackmon.aihealthcare.domain.service.ResearchPlanningService;
import com.wgblackmon.aihealthcare.domain.service.ResearchSynthesisService;
import com.wgblackmon.aihealthcare.domain.service.TopicSummaryGenerationService;
import com.wgblackmon.aihealthcare.domain.service.VendorAssessmentService;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealClassificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TeamPort;
import com.wgblackmon.aihealthcare.domain.service.TeamManagementService;
import com.wgblackmon.aihealthcare.domain.service.CompanyRelationshipService;
import com.wgblackmon.aihealthcare.domain.service.CompanySentimentService;
import com.wgblackmon.aihealthcare.domain.service.DataExportService;
import com.wgblackmon.aihealthcare.domain.service.DealEnrichmentService;
import com.wgblackmon.aihealthcare.domain.service.DealSignalDetectionService;
import com.wgblackmon.aihealthcare.domain.service.FrameworkAnalysisService;
import com.wgblackmon.aihealthcare.domain.model.FrameworkCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.service.DailyBriefingRenderer;
import com.wgblackmon.aihealthcare.domain.service.DailyBriefingService;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkLlmPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SentimentAnalysisPort;
import com.wgblackmon.aihealthcare.domain.service.BrowseCompaniesService;
import com.wgblackmon.aihealthcare.domain.service.CompanySignalService;
import com.wgblackmon.aihealthcare.domain.service.HealthcareAiCompanyClassifier;
import com.wgblackmon.aihealthcare.domain.service.PerplexityCompanyDiscoveryService;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyResearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PerplexityCitationPort;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialService;
import com.wgblackmon.aihealthcare.domain.service.ClinicalTrialWatchlistMatcher;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryEventService;
import com.wgblackmon.aihealthcare.domain.service.RegulatoryWatchlistMatcher;
import com.wgblackmon.aihealthcare.domain.service.WatchlistMatchingService;
import com.wgblackmon.aihealthcare.domain.service.LegalTrendDetectionService;
import com.wgblackmon.aihealthcare.domain.service.TrendDetectionService;
import com.wgblackmon.aihealthcare.domain.service.TrendOrchestrationService;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClinicalTrialPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryHarvestingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LegalTrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendTopicExtractionPort;
import com.wgblackmon.aihealthcare.domain.service.WikiLintService;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchQueryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleBodyFormattingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
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
import com.wgblackmon.aihealthcare.infrastructure.ai.WikiGapAnalysisAdapter;
import com.wgblackmon.aihealthcare.infrastructure.ai.WikiGapResponseParser;
import com.wgblackmon.aihealthcare.infrastructure.ai.WikiResponseParser;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiGapAnalysisPort;
import com.wgblackmon.aihealthcare.domain.service.WikiGapAnalysisService;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapRunRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapItemRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiContradictionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRevisionRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiSourceRefRepository;
import com.wgblackmon.aihealthcare.infrastructure.research.LegacyGoogleResearchAdapter;
import com.wgblackmon.aihealthcare.infrastructure.research.PerplexityResearchAdapter;
import com.wgblackmon.aihealthcare.infrastructure.ai.DocumentIngestionAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
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
        TierLimits enterpriseLimits = new TierLimits(
                props.getEnterprise().getArchiveDays(),
                props.getEnterprise().getMonthlyQueryLimit());
        TierGatingService result = new TierGatingService(freeLimits, subscriberLimits, demoLimits, freePendingLimits, enterpriseLimits);
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
                                                   PasswordHashingPort passwordHashingPort,
                                                   TransactionalEmailPort transactionalEmailPort) {
        log.debug("registrationService() | appUserPort={}, subscriberPort={}, passwordHashingPort={}, transactionalEmailPort={}",
                  appUserPort.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  passwordHashingPort.getClass().getSimpleName(),
                  transactionalEmailPort.getClass().getSimpleName());
        RegistrationService result = new RegistrationService(appUserPort, subscriberPort, passwordHashingPort, transactionalEmailPort);
        log.debug("registrationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link PasswordResetService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.PasswordResetUseCase}.
     *
     * @param appUserPort           Adapter implementing app user persistence (auto-detected).
     * @param passwordResetPort     Adapter implementing reset token persistence (auto-detected).
     * @param passwordHashingPort   Adapter implementing password hashing (auto-detected).
     * @param transactionalEmailPort Adapter implementing transactional email delivery (auto-detected).
     * @return The wired {@link PasswordResetService} instance.
     */
    @Bean
    public PasswordResetService passwordResetService(AppUserPort appUserPort,
                                                      PasswordResetPort passwordResetPort,
                                                      PasswordHashingPort passwordHashingPort,
                                                      TransactionalEmailPort transactionalEmailPort) {
        log.debug("passwordResetService() | appUserPort={}, passwordResetPort={}, passwordHashingPort={}, transactionalEmailPort={}",
                  appUserPort.getClass().getSimpleName(),
                  passwordResetPort.getClass().getSimpleName(),
                  passwordHashingPort.getClass().getSimpleName(),
                  transactionalEmailPort.getClass().getSimpleName());
        PasswordResetService result = new PasswordResetService(appUserPort, passwordResetPort, passwordHashingPort, transactionalEmailPort);
        log.debug("passwordResetService() | return={}", result.getClass().getSimpleName());
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

    @Bean
    public ArticleQualityFilter articleQualityFilter() {
        log.debug("articleQualityFilter() | return=ArticleQualityFilter");
        return new ArticleQualityFilter();
    }

    /**
     * Creates the {@link DigestNewsletterRenderer} that builds the FREE-tier
     * daily article digest from recent RSS articles in the database.
     *
     * <p>The renderer scores the top-20 articles via {@link ArticleScoringPort} and
     * formats the highest-scored article's body text via {@link ArticleBodyFormattingPort}
     * for the "Article of the Day" featured block.
     *
     * @param articleIngestionPort Port for fetching recent articles.
     * @param articleScoringPort   Port for LLM-based 1–10 significance scoring.
     * @param bodyFormattingPort   Port for LLM entity bolding of article body text.
     * @param articleQualityFilter Filter that excludes source-label-only articles.
     * @return The wired {@link DigestNewsletterRenderer} instance.
     */
    @Bean
    public DigestNewsletterRenderer digestNewsletterRenderer(
            ArticleIngestionPort articleIngestionPort,
            ArticleScoringPort articleScoringPort,
            ArticleBodyFormattingPort bodyFormattingPort,
            ArticleQualityFilter articleQualityFilter) {
        log.debug("digestNewsletterRenderer() | articleIngestionPort={}, articleScoringPort={}, bodyFormattingPort={}, articleQualityFilter={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  articleScoringPort.getClass().getSimpleName(),
                  bodyFormattingPort.getClass().getSimpleName(),
                  articleQualityFilter.getClass().getSimpleName());
        DigestNewsletterRenderer result = new DigestNewsletterRenderer(
                articleIngestionPort, articleScoringPort, bodyFormattingPort, articleQualityFilter);
        log.debug("digestNewsletterRenderer() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link SampleNewsletterRenderer} that builds complimentary
     * sample newsletter issues for prospect outreach.
     */
    @Bean
    public SampleNewsletterRenderer sampleNewsletterRenderer(ArticleIngestionPort articleIngestionPort) {
        log.debug("sampleNewsletterRenderer() | articleIngestionPort={}", articleIngestionPort.getClass().getSimpleName());
        SampleNewsletterRenderer result = new SampleNewsletterRenderer(articleIngestionPort);
        log.debug("sampleNewsletterRenderer() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DailyBriefingRenderer} for personalized daily briefing emails.
     */
    @Bean
    public DailyBriefingRenderer dailyBriefingRenderer() {
        log.debug("dailyBriefingRenderer() | creating stateless renderer");
        DailyBriefingRenderer result = new DailyBriefingRenderer();
        log.debug("dailyBriefingRenderer() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DailyBriefingService} that orchestrates per-subscriber
     * daily briefing assembly and delivery.
     */
    @Bean
    public DailyBriefingService dailyBriefingService(
            SubscriberPort subscriberPort,
            WatchlistPort watchlistPort,
            WatchlistMatchPort watchlistMatchPort,
            CompanySentimentPort companySentimentPort,
            AnalystNotePort analystNotePort,
            NewsletterDeliveryPort newsletterDeliveryPort,
            DailyBriefingRenderer dailyBriefingRenderer,
            @Value("${aihealthcare.briefing.lookback-hours:24}") int lookbackHours) {
        log.debug("dailyBriefingService() | lookbackHours={}", lookbackHours);
        DailyBriefingService result = new DailyBriefingService(
                subscriberPort, watchlistPort, watchlistMatchPort,
                companySentimentPort, analystNotePort,
                newsletterDeliveryPort, dailyBriefingRenderer, lookbackHours);
        log.debug("dailyBriefingService() | return={}", result.getClass().getSimpleName());
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
                                               WikiQueryPort wikiQueryPort,
                                               MonitorRegulatoryEventsUseCase regulatoryUseCase) {
        log.debug("newsletterService() | ingestionPort={}, summarizationPort={}, newsletterRunPort={}, searchPort={}, wikiQueryPort={}, regulatoryUseCase={}",
                  ingestionPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  searchPort.getClass().getSimpleName(),
                  wikiQueryPort.getClass().getSimpleName(),
                  regulatoryUseCase.getClass().getSimpleName());
        NewsletterRenderer renderer = new NewsletterRenderer();
        ReversalWatchSectionBuilder reversalWatchBuilder = new ReversalWatchSectionBuilder();
        LegalBriefSectionBuilder legalBriefBuilder = new LegalBriefSectionBuilder(
                ingestionPort, regulatoryUseCase, articleQualityFilter());
        NewsletterService result = new NewsletterService(
                ingestionPort, summarizationPort, renderer, newsletterRunPort, searchPort,
                wikiQueryPort, reversalWatchBuilder, legalBriefBuilder);
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
     * Creates the single {@link IngestDocumentsUseCase} bean, choosing between the
     * real vector-store-backed {@link DocumentIngestionService} and a no-op fallback
     * based on whether a {@link VectorStore} is actually available.
     *
     * <p>This deliberately avoids {@code @ConditionalOnBean(VectorStore.class)} on a
     * component-scanned class or {@code @Bean} method: Spring Boot evaluates such
     * conditions while regular {@code @Configuration}/{@code @Component} classes are
     * parsed, which happens BEFORE the {@code PgVectorStoreAutoConfiguration} (a
     * deferred import) registers its bean definition. That ordering pitfall silently
     * disabled document ingestion even when pgvector was fully configured and working
     * — the {@code VectorStore} bean existed, but the condition checking for it had
     * already evaluated to {@code false} by the time it ran. Resolving via
     * {@link ObjectProvider#getIfAvailable()} inside the method body defers the check
     * to actual singleton instantiation, by which point every auto-configured bean,
     * including {@code PgVectorStore}, is guaranteed to be registered.
     *
     * <p>Spring injects all {@link FileParserPort} {@code @Component} beans as a list
     * automatically, so adding a new parser requires no change to this method.
     *
     * @param parsers             All registered file-parser adapters (PDF, DOCX, plain-text).
     * @param vectorStoreProvider Lazily-resolved {@link VectorStore}; absent when pgvector
     *                            is not configured (e.g. the {@code h2} profile).
     * @return The wired {@link IngestDocumentsUseCase} — real or no-op.
     */
    @Bean
    public IngestDocumentsUseCase ingestDocumentsUseCase(List<FileParserPort> parsers,
                                                          ObjectProvider<VectorStore> vectorStoreProvider) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        log.debug("ingestDocumentsUseCase() | parserCount={}, vectorStoreAvailable={}",
                  parsers.size(), vectorStore != null);

        if (vectorStore == null) {
            log.debug("ingestDocumentsUseCase() | pgvector not configured — document ingestion disabled");
            IngestDocumentsUseCase result = new IngestDocumentsUseCase() {
                @Override
                public DocumentIngestionResult ingest(String directory, String sourceLabel, int chunkSize) {
                    return new DocumentIngestionResult(0, 0,
                            List.of("Document ingestion is unavailable — pgvector is not configured in this environment."));
                }
                @Override
                public DocumentIngestionResult ingestFile(java.nio.file.Path file, String sourceLabel) {
                    return new DocumentIngestionResult(0, 0,
                            List.of("Document ingestion is unavailable — pgvector is not configured in this environment."));
                }
            };
            log.debug("ingestDocumentsUseCase() | return=no-op");
            return result;
        }

        DocumentVectorPort vectorPort = new DocumentIngestionAdapter(vectorStore);
        DocumentIngestionService result = new DocumentIngestionService(parsers, vectorPort);
        log.debug("ingestDocumentsUseCase() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DocumentUploadService} that orchestrates the full
     * browser-upload pipeline: vector ingestion + wiki compilation + status tracking.
     *
     * @param ingestUseCase           Use case for vector store ingestion.
     * @param documentLibraryPort     Adapter for persisting document library records.
     * @param knowledgeCompilationPort Wiki compilation port (may be null when wiki adapter is unavailable).
     * @param parsers                 All registered file-parser adapters.
     * @return The wired {@link DocumentUploadService} instance.
     */
    @Bean
    public DocumentUploadService documentUploadService(
            IngestDocumentsUseCase ingestUseCase,
            DocumentLibraryPort documentLibraryPort,
            @Autowired(required = false) KnowledgeCompilationPort knowledgeCompilationPort,
            List<FileParserPort> parsers) {
        log.debug("documentUploadService() | ingestUseCase={}, documentLibraryPort={}, knowledgeCompilationPort={}, parsers={}",
                ingestUseCase.getClass().getSimpleName(),
                documentLibraryPort.getClass().getSimpleName(),
                knowledgeCompilationPort != null ? knowledgeCompilationPort.getClass().getSimpleName() : "null",
                parsers.size());
        DocumentUploadService result = new DocumentUploadService(
                ingestUseCase, documentLibraryPort, knowledgeCompilationPort, parsers);
        log.debug("documentUploadService() | return={}", result.getClass().getSimpleName());
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
     * Creates the {@link IntelReportService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.GenerateIntelReportUseCase}.
     *
     * <p>Reuses the existing COMBINED research pipeline for source gathering,
     * then applies a specialized intel-report prompt for deep-dive synthesis.
     *
     * @param researchUseCase    existing research pipeline (auto-detected).
     * @param aiReportPort       generic AI prompt-to-text port (auto-detected).
     * @param intelReportPort    persistence port for intel reports (auto-detected).
     * @param promptLoaderService template loader (auto-detected).
     * @return The wired {@link IntelReportService} instance.
     */
    @Bean
    public IntelReportService intelReportService(
            ConductResearchUseCase researchUseCase,
            AiReportPort aiReportPort,
            IntelReportPort intelReportPort,
            PromptLoaderService promptLoaderService) {
        log.debug("intelReportService() | researchUseCase={}, aiReportPort={}, intelReportPort={}",
                  researchUseCase.getClass().getSimpleName(),
                  aiReportPort.getClass().getSimpleName(),
                  intelReportPort.getClass().getSimpleName());
        String template = promptLoaderService.load("intel-report.txt");
        IntelReportService result = new IntelReportService(
                researchUseCase, aiReportPort, intelReportPort, template);
        log.debug("intelReportService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TrendDetectionService} bean — a pure-domain keyword
     * frequency analyzer used as a fallback when the LLM-based trend detection
     * has not run or produced an empty snapshot.
     *
     * @param minOccurrences minimum keyword occurrences to be included in analysis
     * @param risingLimit    maximum number of rising signals to return
     * @return The wired {@link TrendDetectionService} instance.
     */
    @Bean
    public TrendDetectionService trendDetectionService(
            @Value("${aihealthcare.trends.min-occurrences:2}") int minOccurrences,
            @Value("${aihealthcare.trends.rising-limit:20}") int risingLimit) {
        log.debug("trendDetectionService() | minOccurrences={}, risingLimit={}", minOccurrences, risingLimit);
        TrendDetectionService result = new TrendDetectionService(minOccurrences, risingLimit);
        log.debug("trendDetectionService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link TrendOrchestrationService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase}.
     *
     * <p>Uses LLM-based topic extraction instead of n-gram frequency counting.
     * The {@link TrendTopicExtractionPort} adapter (auto-detected) sends article
     * titles to an LLM to identify emerging themes.
     *
     * @param articleIngestionPort       Adapter implementing article fetching (auto-detected).
     * @param trendTopicExtractionPort   LLM-based topic extraction adapter (auto-detected).
     * @param trendSnapshotPort          Adapter implementing snapshot persistence (auto-detected).
     * @param articleScoringPort         Adapter implementing article scoring (auto-detected).
     * @param trendSummaryPort           Adapter implementing deep research summaries (auto-detected).
     * @param scoringEnabled             whether to enable LLM article scoring.
     * @param scoreThreshold             minimum score to include articles.
     * @param maxSummariesPerRun         maximum deep research summaries per run.
     * @param maxTopics                  maximum trend topics to extract per window.
     * @return The wired {@link TrendOrchestrationService} instance.
     */
    @Bean
    public TrendOrchestrationService trendOrchestrationService(
            ArticleIngestionPort articleIngestionPort,
            TrendTopicExtractionPort trendTopicExtractionPort,
            TrendSnapshotPort trendSnapshotPort,
            ArticleScoringPort articleScoringPort,
            TrendSummaryPort trendSummaryPort,
            @Value("${aihealthcare.tech-trends.scoring-enabled:false}") boolean scoringEnabled,
            @Value("${aihealthcare.tech-trends.score-threshold:7}") int scoreThreshold,
            @Value("${aihealthcare.deep-research.max-summaries-per-run:5}") int maxSummariesPerRun,
            @Value("${aihealthcare.trends.rising-limit:20}") int maxTopics) {
        log.debug("trendOrchestrationService() | articleIngestionPort={}, trendTopicExtractionPort={}, " +
                  "trendSnapshotPort={}, articleScoringPort={}, trendSummaryPort={}, " +
                  "scoringEnabled={}, scoreThreshold={}, maxSummariesPerRun={}, maxTopics={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  trendTopicExtractionPort.getClass().getSimpleName(),
                  trendSnapshotPort.getClass().getSimpleName(),
                  articleScoringPort.getClass().getSimpleName(),
                  trendSummaryPort.getClass().getSimpleName(),
                  scoringEnabled, scoreThreshold, maxSummariesPerRun, maxTopics);
        TrendOrchestrationService result = new TrendOrchestrationService(
                articleIngestionPort, trendTopicExtractionPort, trendSnapshotPort,
                articleScoringPort, trendSummaryPort, scoringEnabled, scoreThreshold,
                maxSummariesPerRun, maxTopics);
        log.debug("trendOrchestrationService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link LegalTrendDetectionService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase}.
     *
     * <p>Reuses the existing {@link TrendTopicExtractionPort} for LLM-based theme
     * extraction, scoped to legal/policy articles and regulatory events.
     *
     * @param articleIngestionPort       Adapter implementing article fetching (auto-detected).
     * @param regulatoryUseCase          Regulatory event monitoring use case (auto-detected).
     * @param trendTopicExtractionPort   LLM-based topic extraction adapter (auto-detected).
     * @param legalTrendSnapshotPort     Legal trend snapshot persistence (auto-detected).
     * @param trendSummaryPort           Deep research summary adapter (auto-detected).
     * @param maxTopics                  Maximum trend topics to extract per window.
     * @return The wired {@link LegalTrendDetectionService} instance.
     */
    @Bean
    public LegalTrendDetectionService legalTrendDetectionService(
            ArticleIngestionPort articleIngestionPort,
            MonitorRegulatoryEventsUseCase regulatoryUseCase,
            TrendTopicExtractionPort trendTopicExtractionPort,
            LegalTrendSnapshotPort legalTrendSnapshotPort,
            TrendSummaryPort trendSummaryPort,
            @Value("${aihealthcare.legal-trends.max-topics:15}") int maxTopics) {
        log.debug("legalTrendDetectionService() | articleIngestionPort={}, regulatoryUseCase={}, " +
                  "trendTopicExtractionPort={}, legalTrendSnapshotPort={}, trendSummaryPort={}, maxTopics={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  regulatoryUseCase.getClass().getSimpleName(),
                  trendTopicExtractionPort.getClass().getSimpleName(),
                  legalTrendSnapshotPort.getClass().getSimpleName(),
                  trendSummaryPort.getClass().getSimpleName(),
                  maxTopics);
        LegalTrendDetectionService result = new LegalTrendDetectionService(
                articleIngestionPort, regulatoryUseCase, trendTopicExtractionPort,
                legalTrendSnapshotPort, trendSummaryPort, maxTopics);
        log.debug("legalTrendDetectionService() | return={}", result.getClass().getSimpleName());
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
     * Creates the {@link WikiGapAnalysisAdapter} bean that implements
     * {@link WikiGapAnalysisPort} — the LLM-powered wiki gap analysis pipeline.
     *
     * @param chatClientBuilder   Auto-configured ChatClient builder.
     * @param promptLoaderService Template loader for prompt files.
     * @return The wired {@link WikiGapAnalysisAdapter} instance.
     */
    @Bean
    public WikiGapAnalysisAdapter wikiGapAnalysisAdapter(
            ChatClient.Builder chatClientBuilder,
            PromptLoaderService promptLoaderService) {
        log.debug("wikiGapAnalysisAdapter() | wiring wiki gap analysis pipeline");
        String gapPrompt = promptLoaderService.load("wiki-gap-analysis.txt");
        WikiGapResponseParser parser = new WikiGapResponseParser();
        WikiGapAnalysisAdapter result = new WikiGapAnalysisAdapter(
                chatClientBuilder, parser, gapPrompt);
        log.debug("wikiGapAnalysisAdapter() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link WikiGapAnalysisService} bean — orchestrates gap
     * analysis, persistence, and the approval workflow.
     *
     * @param gapAnalysisPort  The LLM adapter for gap detection.
     * @param runRepository    Gap run persistence.
     * @param itemRepository   Gap item persistence.
     * @return The wired {@link WikiGapAnalysisService} instance.
     */
    @Bean
    public WikiGapAnalysisService wikiGapAnalysisService(
            WikiGapAnalysisPort gapAnalysisPort,
            WikiGapRunRepository runRepository,
            WikiGapItemRepository itemRepository) {
        log.debug("wikiGapAnalysisService() | wiring gap analysis service");
        WikiGapAnalysisService result = new WikiGapAnalysisService(
                gapAnalysisPort, runRepository, itemRepository);
        log.debug("wikiGapAnalysisService() | return={}", result.getClass().getSimpleName());
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

    /**
     * Creates the {@link PerplexityCompanyDiscoveryService} bean — orchestrates the
     * Perplexity-powered company discovery pipeline: discover → dedup → extract →
     * validate → persist.
     *
     * @param researchPort       Perplexity API adapter (auto-detected).
     * @param companyPort        Company persistence adapter (auto-detected).
     * @param citationPort       Citation audit trail adapter (auto-detected).
     * @param maxCompaniesPerRun Maximum companies to process per discovery run.
     * @return The wired {@link PerplexityCompanyDiscoveryService} instance.
     */
    @Bean
    public HealthcareAiCompanyClassifier healthcareAiCompanyClassifier() {
        log.debug("healthcareAiCompanyClassifier() | return=HealthcareAiCompanyClassifier");
        return new HealthcareAiCompanyClassifier();
    }

    @Bean
    public PerplexityCompanyDiscoveryService perplexityCompanyDiscoveryService(
            CompanyResearchPort researchPort,
            HealthcareAiCompanyPort companyPort,
            PerplexityCitationPort citationPort,
            HealthcareAiCompanyClassifier healthcareAiCompanyClassifier,
            @Value("${aihealthcare.company-discovery.max-companies-per-run:30}") int maxCompaniesPerRun) {
        log.debug("perplexityCompanyDiscoveryService() | researchPort={}, companyPort={}, citationPort={}, max={}",
                researchPort.getClass().getSimpleName(),
                companyPort.getClass().getSimpleName(),
                citationPort.getClass().getSimpleName(),
                maxCompaniesPerRun);
        PerplexityCompanyDiscoveryService result = new PerplexityCompanyDiscoveryService(
                researchPort, companyPort, citationPort, healthcareAiCompanyClassifier, maxCompaniesPerRun);
        log.debug("perplexityCompanyDiscoveryService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link CompanySignalService} bean — cross-references articles,
     * deal signals, and sentiment to score companies for the directory ranking.
     */
    @Bean
    public CompanySignalService companySignalService(ArticleIngestionPort articleIngestionPort,
                                                      DealSignalPort dealSignalPort,
                                                      CompanySentimentPort companySentimentPort) {
        log.debug("companySignalService() | ports wired");
        CompanySignalService result = new CompanySignalService(
                articleIngestionPort, dealSignalPort, companySentimentPort);
        log.debug("companySignalService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link BrowseCompaniesService} bean — powers the public-facing
     * company directory at {@code /companies}.
     *
     * @param companyPort   Company persistence adapter (auto-detected).
     * @param signalService Signal scoring service (auto-detected).
     * @return The wired {@link BrowseCompaniesService} instance.
     */
    @Bean
    public BrowseCompaniesService browseCompaniesService(HealthcareAiCompanyPort companyPort,
                                                          CompanySignalService signalService) {
        log.debug("browseCompaniesService() | companyPort={}", companyPort.getClass().getSimpleName());
        BrowseCompaniesService result = new BrowseCompaniesService(companyPort, signalService);
        log.debug("browseCompaniesService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link CompanySentimentService} bean that implements
     * {@link com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase}.
     *
     * @param companyProfilePort     Adapter implementing company profile persistence.
     * @param articleIngestionPort   Adapter implementing article retrieval.
     * @param sentimentAnalysisPort  Adapter implementing LLM sentiment classification.
     * @param companySentimentPort   Adapter implementing sentiment persistence.
     * @return The wired {@link CompanySentimentService} instance.
     */
    @Bean
    public CompanySentimentService companySentimentService(
            CompanyProfilePort companyProfilePort,
            ArticleIngestionPort articleIngestionPort,
            SentimentAnalysisPort sentimentAnalysisPort,
            CompanySentimentPort companySentimentPort) {
        log.debug("companySentimentService() | companyProfilePort={}, articleIngestionPort={}, sentimentAnalysisPort={}, companySentimentPort={}",
                  companyProfilePort.getClass().getSimpleName(),
                  articleIngestionPort.getClass().getSimpleName(),
                  sentimentAnalysisPort.getClass().getSimpleName(),
                  companySentimentPort.getClass().getSimpleName());
        CompanySentimentService result = new CompanySentimentService(
                companyProfilePort, articleIngestionPort,
                sentimentAnalysisPort, companySentimentPort);
        log.debug("companySentimentService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link FrameworkAnalysisService} — healthcare framework
     * competitive analysis across configured companies.
     *
     * @param frameworkCompanyProperties  YAML-bound company configuration.
     * @param articleIngestionPort        Adapter for fetching articles by topic.
     * @param frameworkLlmPort            Adapter for LLM framework analysis.
     * @param frameworkAnalysisPort       Adapter for persisting framework analyses.
     * @return The wired {@link FrameworkAnalysisService} instance.
     */
    @Bean
    public FrameworkAnalysisService frameworkAnalysisService(
            FrameworkCompanyProperties frameworkCompanyProperties,
            ArticleIngestionPort articleIngestionPort,
            FrameworkLlmPort frameworkLlmPort,
            FrameworkAnalysisPort frameworkAnalysisPort) {
        log.debug("frameworkAnalysisService() | companies={}, articleIngestionPort={}, frameworkLlmPort={}, frameworkAnalysisPort={}",
                  frameworkCompanyProperties.getCompanies().size(),
                  articleIngestionPort.getClass().getSimpleName(),
                  frameworkLlmPort.getClass().getSimpleName(),
                  frameworkAnalysisPort.getClass().getSimpleName());

        List<FrameworkCompany> companies = new ArrayList<>();
        for (FrameworkCompanyProperties.CompanyEntry entry : frameworkCompanyProperties.getCompanies()) {
            companies.add(new FrameworkCompany(
                    entry.getSlug(), entry.getName(), entry.getUrl(), entry.getTopics()));
        }

        FrameworkAnalysisService result = new FrameworkAnalysisService(
                companies, articleIngestionPort, frameworkLlmPort, frameworkAnalysisPort);
        log.debug("frameworkAnalysisService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {
        log.debug("primaryChatModel() | anthropicChatModel={}", anthropicChatModel.getClass().getSimpleName());
        return anthropicChatModel;
    }

    /**
     * Creates the in-memory API rate limiter with configurable requests-per-minute.
     *
     * @param maxPerMinute maximum requests per minute per API key (default 60).
     * @return The configured {@link RateLimiter} instance.
     */
    @Bean
    public RateLimiter apiRateLimiter(
            @Value("${aihealthcare.api.rate-limit-per-minute:60}") int maxPerMinute) {
        log.debug("apiRateLimiter() | maxPerMinute={}", maxPerMinute);
        RateLimiter result = new RateLimiter(maxPerMinute);
        log.debug("apiRateLimiter() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link DealEnrichmentService} — cross-references deal signals
     * with sentiment, framework, regulatory, and company profile data.
     */
    @Bean
    public DealEnrichmentService dealEnrichmentService(
            @Autowired(required = false) CompanySentimentPort companySentimentPort,
            @Autowired(required = false) FrameworkAnalysisPort frameworkAnalysisPort,
            @Autowired(required = false) RegulatoryEventPort regulatoryEventPort,
            @Autowired(required = false) CompanyProfilePort companyProfilePort) {
        log.debug("dealEnrichmentService()");
        DealEnrichmentService result = new DealEnrichmentService(
                companySentimentPort, frameworkAnalysisPort, regulatoryEventPort, companyProfilePort);
        log.debug("dealEnrichmentService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link DealSignalDetectionService} — keyword + LLM deal signal
     * detection in recently harvested articles with cross-ref enrichment.
     */
    @Bean
    public DealSignalDetectionService dealSignalDetectionService(
            ArticleIngestionPort articleIngestionPort,
            DealSignalPort dealSignalPort,
            @Autowired(required = false) DealClassificationPort dealClassificationPort,
            DealEnrichmentService dealEnrichmentService) {
        log.debug("dealSignalDetectionService() | articleIngestionPort={}, dealSignalPort={}, classificationPort={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  dealSignalPort.getClass().getSimpleName(),
                  dealClassificationPort != null ? dealClassificationPort.getClass().getSimpleName() : "null");
        DealSignalDetectionService result = new DealSignalDetectionService(
                articleIngestionPort, dealSignalPort, dealClassificationPort, dealEnrichmentService);
        log.debug("dealSignalDetectionService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link TeamManagementService} — multi-tenant team account management.
     *
     * @param teamPort Adapter for persisting teams and members.
     * @return The wired {@link TeamManagementService} instance.
     */
    @Bean
    public TeamManagementService teamManagementService(TeamPort teamPort) {
        log.debug("teamManagementService() | teamPort={}", teamPort.getClass().getSimpleName());
        TeamManagementService result = new TeamManagementService(teamPort);
        log.debug("teamManagementService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link CompanyRelationshipService} — keyword-based inter-company
     * relationship extraction from recently harvested articles.
     *
     * @param articleIngestionPort     Adapter for fetching recent articles.
     * @param companyRelationshipPort  Adapter for persisting relationships.
     * @return The wired {@link CompanyRelationshipService} instance.
     */
    @Bean
    public CompanyRelationshipService companyRelationshipService(
            ArticleIngestionPort articleIngestionPort,
            CompanyRelationshipPort companyRelationshipPort) {
        log.debug("companyRelationshipService() | articleIngestionPort={}, companyRelationshipPort={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  companyRelationshipPort.getClass().getSimpleName());
        CompanyRelationshipService result = new CompanyRelationshipService(articleIngestionPort, companyRelationshipPort);
        log.debug("companyRelationshipService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link DataExportService} — CSV/JSON/PDF data export with optional
     * white-label branding for enterprise customers.
     *
     * @param articleIngestionPort      Adapter for fetching recent articles.
     * @param dealSignalPort            Adapter for fetching deal signals.
     * @param companyRelationshipPort   Adapter for fetching company relationships.
     * @return The wired {@link DataExportService} instance.
     */
    @Bean
    public DataExportService dataExportService(
            ArticleIngestionPort articleIngestionPort,
            DealSignalPort dealSignalPort,
            CompanyRelationshipPort companyRelationshipPort) {
        log.debug("dataExportService() | articleIngestionPort={}, dealSignalPort={}, companyRelationshipPort={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  dealSignalPort.getClass().getSimpleName(),
                  companyRelationshipPort.getClass().getSimpleName());
        DataExportService result = new DataExportService(articleIngestionPort, dealSignalPort, companyRelationshipPort);
        log.debug("dataExportService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Wires the {@link com.wgblackmon.aihealthcare.domain.service.StateLawService}
     * domain service with its three outbound ports for the legislation registry.
     *
     * @param stateLawPort     law persistence port
     * @param changeEventPort  change event persistence port
     * @param candidatePort    new bill candidate persistence port
     * @return the wired service
     */
    @Bean
    public com.wgblackmon.aihealthcare.domain.service.StateLawService stateLawService(
            com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort stateLawPort,
            com.wgblackmon.aihealthcare.domain.port.outbound.LawChangeEventPort changeEventPort,
            com.wgblackmon.aihealthcare.domain.port.outbound.NewBillCandidatePort candidatePort) {
        log.debug("stateLawService() | stateLawPort={}, changeEventPort={}, candidatePort={}",
                  stateLawPort.getClass().getSimpleName(),
                  changeEventPort.getClass().getSimpleName(),
                  candidatePort.getClass().getSimpleName());
        com.wgblackmon.aihealthcare.domain.service.StateLawService result =
                new com.wgblackmon.aihealthcare.domain.service.StateLawService(
                        stateLawPort, changeEventPort, candidatePort);
        log.debug("stateLawService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Registers the {@link ApiRateLimitFilter} as a servlet filter on {@code /api/*}
     * paths. Registered via {@link FilterRegistrationBean} to avoid modifying
     * {@link SecurityConfig}'s constructor signature.
     *
     * @param rateLimiter the rate limiter bean.
     * @return The filter registration bean.
     */
    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(
            RateLimiter rateLimiter) {
        log.debug("apiRateLimitFilterRegistration() | rateLimiter={}", rateLimiter.getClass().getSimpleName());
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiRateLimitFilter(rateLimiter));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        log.debug("apiRateLimitFilterRegistration() | return=FilterRegistrationBean");
        return registration;
    }
}
