package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @updated 2026-08-19
 */
@Slf4j
@Configuration
public class MarketAnalysisConfig {

    /**
     * Wires the daily digest orchestration service with all four adapter ports.
     *
     * @param newsResearch     Perplexity adapter — fetches recent AI-healthcare news
     * @param marketData       Alpaca adapter — stock quote + price history lookup
     * @param impactClassifier Claude adapter — 5-dimension impact scoring
     * @param repository       JPA adapter — persist and query digests
     * @param notifier         SES notifier — null until Slice 1.8 is merged
     * @return configured {@link MarketDigestService} bean
     */
    @Bean
    public MarketDigestService marketDigestService(
            MarketNewsResearchPort newsResearch,
            MarketDataPort marketData,
            ImpactClassifierPort impactClassifier,
            MarketDigestRepository repository,
            @Autowired(required = false) MarketDigestNotifier notifier) {
        log.debug("marketDigestService() | notifierPresent={}", notifier != null);
        MarketDigestService result = new MarketDigestService(
                newsResearch, marketData, impactClassifier, repository, notifier);
        log.debug("marketDigestService() | return={}", result);
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
