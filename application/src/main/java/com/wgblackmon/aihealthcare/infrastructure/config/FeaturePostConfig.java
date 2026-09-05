package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.port.inbound.RotateFeaturePostsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.FeaturePostPort;
import com.wgblackmon.aihealthcare.domain.service.FeaturePostRotationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/**
 * Spring wiring for the LinkedIn feature-post rotation.
 *
 * The rotation service lives in the domain package and carries no Spring
 * annotations, so its bean definition belongs here — the same separation used
 * elsewhere in this application to keep the domain free of framework imports.
 *
 * The anchor date fixes where week 1 of the cycle begins. It is configurable
 * so the cycle can be re-based without editing the library, and defaults to
 * the Monday the rotation was first published.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@Slf4j
@Configuration
public class FeaturePostConfig {

    /**
     * Creates the rotation service bean.
     *
     * @param featurePostPort supplies the post library
     * @param anchorDate      ISO date on which week 1 Monday falls
     * @return the rotation use case
     */
    @Bean
    public RotateFeaturePostsUseCase rotateFeaturePostsUseCase(
            FeaturePostPort featurePostPort,
            @Value("${aihealthcare.linkedin.rotation-anchor:2026-09-07}") String anchorDate) {
        log.debug("rotateFeaturePostsUseCase() | anchorDate={}", anchorDate);
        return new FeaturePostRotationService(featurePostPort, LocalDate.parse(anchorDate));
    }
}
