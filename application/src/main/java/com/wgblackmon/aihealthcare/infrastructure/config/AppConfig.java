package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class that wires the application-layer service as a bean.
 *
 * <p><b>Why is NewsletterService not annotated with @Service?</b>
 * {@link NewsletterService} lives in the {@code application} layer, which is intentionally
 * free of Spring annotations. This keeps the application layer testable without a Spring
 * context — unit tests construct it directly with mock ports, no framework involved.
 *
 * <p>This {@code @Configuration} class acts as the wiring bridge: it lives in
 * {@code infrastructure/config} (where Spring is allowed), pulls in the two outbound-port
 * adapters that Spring has auto-detected via {@code @Component}, and manually constructs
 * the {@link NewsletterService}. Spring then registers that instance as a bean.
 *
 * <p>Because {@link NewsletterService} implements both
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase} and
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase},
 * Spring will automatically satisfy any injection point typed to either interface using
 * the single bean defined here.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
@Slf4j
@Configuration
public class AppConfig {

    /**
     * Creates the single {@link NewsletterService} instance shared across the application.
     *
     * <p>Spring injects the two {@code @Component}-annotated adapters
     * ({@code AiSummarizationAdapter} and {@code ArticleIngestionAdapter}) automatically.
     * Keeping the service as a manually-constructed bean rather than {@code @Service} means
     * it can be tested without any Spring context — just {@code new NewsletterService(mockPort, mockPort)}.
     *
     * @param ingestionPort      The adapter implementing article fetching (auto-detected).
     * @param summarizationPort  The adapter implementing AI summarization (auto-detected).
     * @return The wired {@link NewsletterService} instance.
     */
    @Bean
    public NewsletterService newsletterService(ArticleIngestionPort ingestionPort,
                                               AiSummarizationPort summarizationPort) {
        log.debug("newsletterService() | ingestionPort={}, summarizationPort={}",
                  ingestionPort.getClass().getSimpleName(),
                  summarizationPort.getClass().getSimpleName());
        NewsletterService result = new NewsletterService(ingestionPort, summarizationPort);
        log.debug("newsletterService() | return={}", result.getClass().getSimpleName());
        return result;
    }
}
