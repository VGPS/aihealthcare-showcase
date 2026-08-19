package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.marketanalysis.GuidanceQueryService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.GuidancePort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring configuration for the Market Analysis module.
 *
 * <p>Wires {@link MarketDigestService} — which is a plain domain class,
 * not a {@code @Component} — with its four adapter implementations.
 * {@link MarketDigestNotifier} is injected as optional (required=false)
 * because the SES notifier is not implemented until Slice 1.8.
 *
 * <p>Also defines the {@code marketAnalysisExecutor} thread pool
 * (corePoolSize=2, maxPoolSize=4) for async REST endpoints introduced
 * in Slice 1.8.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19  added guidanceQueryService bean (Slice 2.1)
 */
@Slf4j
@Configuration
public class MarketAnalysisConfig {

    /**
     * Wires the daily digest orchestration service with all adapter ports.
     *
     * @param newsResearch     Perplexity adapter — fetches recent AI-healthcare news
     * @param marketData       Alpaca adapter — stock quote + price history lookup
     * @param impactClassifier Claude adapter — 5-dimension impact scoring
     * @param repository       JPA adapter — persist and query digests
     * @param notifier         SES notifier — email alert delivery
     * @param embeddingPort    embedding adapter for dedup — null when EmbeddingModel absent
     * @param dedupThreshold   cosine similarity threshold above which entries are suppressed
     * @return configured {@link MarketDigestService} bean
     */
    @Bean
    public MarketDigestService marketDigestService(
            MarketNewsResearchPort newsResearch,
            MarketDataPort marketData,
            ImpactClassifierPort impactClassifier,
            MarketDigestRepository repository,
            @Autowired(required = false) MarketDigestNotifier notifier,
            @Autowired(required = false) EntryEmbeddingPort embeddingPort,
            @Value("${aihealthcare.market-analysis.dedup.similarity-threshold:0.93}") double dedupThreshold) {
        log.debug("marketDigestService() | notifierPresent={}, embeddingPortPresent={}, dedupThreshold={}",
                notifier != null, embeddingPort != null, dedupThreshold);
        MarketDigestService result = new MarketDigestService(
                newsResearch, marketData, impactClassifier, repository,
                notifier, embeddingPort, dedupThreshold);
        log.debug("marketDigestService() | return={}", result);
        return result;
    }

    /**
     * Wires the {@link GuidanceQueryService} domain service with the JPA-backed
     * {@link GuidancePort} adapter.
     *
     * @param guidancePort JPA-backed guidance history adapter
     * @return configured {@link GuidanceQueryService} bean
     */
    @Bean
    public GuidanceQueryService guidanceQueryService(GuidancePort guidancePort) {
        log.debug("guidanceQueryService() | guidancePort={}", guidancePort.getClass().getSimpleName());
        GuidanceQueryService result = new GuidanceQueryService(guidancePort);
        log.debug("guidanceQueryService() | return={}", result);
        return result;
    }

    /**
     * Dedicated thread pool for async Market Analysis operations (REST-triggered
     * manual digest generation, introduced in Slice 1.8).
     *
     * <p>Kept distinct from the Trends pipeline's executor so the two pipelines
     * do not compete for threads.
     *
     * @return thread pool executor named {@code marketAnalysisExecutor}
     */
    @Bean(name = "marketAnalysisExecutor")
    public Executor marketAnalysisExecutor() {
        log.debug("marketAnalysisExecutor() | configuring corePoolSize=2, maxPoolSize=4");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("market-analysis-");
        executor.initialize();
        log.debug("marketAnalysisExecutor() | return={}", executor);
        return executor;
    }
}
