package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
 * <p>A {@link SimpleVectorStore} bean is manually registered here because Spring AI
 * 1.0.0 does not auto-configure {@code SimpleVectorStore}.  The store is backed by
 * the auto-configured {@link EmbeddingModel} from the OpenAI starter.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-13
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(FeedSourceProperties.class)
public class AppConfig {

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
     * @return The wired {@link NewsletterService} instance.
     */
    /**
     * In-memory vector store backed by the auto-configured OpenAI {@link EmbeddingModel}.
     *
     * <p>Spring AI 1.0.0 does not provide auto-configuration for {@link SimpleVectorStore},
     * so the bean is registered here.  Swap the implementation (e.g., to PgVector or
     * Chroma) by replacing this bean — no other code changes are required.
     *
     * @param embeddingModel The embedding model auto-configured by the OpenAI starter.
     * @return A new {@link SimpleVectorStore} instance.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.debug("vectorStore() | embeddingModel={}", embeddingModel.getClass().getSimpleName());
        VectorStore result = SimpleVectorStore.builder(embeddingModel).build();
        log.debug("vectorStore() | return={}", result.getClass().getSimpleName());
        return result;
    }

    /**
     * Creates the {@link DeliveryService} instance that implements subscriber management.
     *
     * @param subscriberPort Adapter implementing subscriber persistence (auto-detected).
     * @return The wired {@link DeliveryService} instance.
     */
    @Bean
    public DeliveryService deliveryService(SubscriberPort subscriberPort,
                                           NewsletterRunPort newsletterRunPort,
                                           NewsletterDeliveryPort newsletterDeliveryPort) {
        log.debug("deliveryService() | subscriberPort={}, newsletterRunPort={}, newsletterDeliveryPort={}",
                  subscriberPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  newsletterDeliveryPort.getClass().getSimpleName());
        DeliveryService result = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort);
        log.debug("deliveryService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public NewsletterService newsletterService(ArticleIngestionPort ingestionPort,
                                               AiSummarizationPort summarizationPort,
                                               NewsletterRunPort newsletterRunPort) {
        log.debug("newsletterService() | ingestionPort={}, summarizationPort={}, newsletterRunPort={}",
                  ingestionPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName());
        NewsletterRenderer renderer = new NewsletterRenderer();
        NewsletterService result = new NewsletterService(
                ingestionPort, summarizationPort, renderer, newsletterRunPort);
        log.debug("newsletterService() | return={}", result.getClass().getSimpleName());
        return result;
    }
}
