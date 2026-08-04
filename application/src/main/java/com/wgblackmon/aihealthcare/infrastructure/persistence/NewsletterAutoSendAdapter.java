package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterAutoSendPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Persistence adapter for the newsletter auto-send override setting.
 *
 * <p>Uses a singleton row in the {@code newsletter_send_settings} table.
 * If no row exists yet, queries return the default (not overridden) and
 * the first {@link #setOverride} call creates the row.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class NewsletterAutoSendAdapter implements NewsletterAutoSendPort {

    private static final Long SINGLETON_ID = 1L;

    private final NewsletterSendSettingsRepository repository;

    public NewsletterAutoSendAdapter(NewsletterSendSettingsRepository repository) {
        log.debug("NewsletterAutoSendAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public boolean isOverriddenForDate(LocalDate date) {
        log.debug("isOverriddenForDate() | date={}", date);
        boolean result = repository.findById(SINGLETON_ID)
                .map(s -> s.isAutoSendOverride() && date.equals(s.getOverrideDate()))
                .orElse(false);
        log.debug("isOverriddenForDate() | return={}", result);
        return result;
    }

    @Override
    public void setOverride(LocalDate date, boolean override) {
        log.debug("setOverride() | date={}, override={}", date, override);
        NewsletterSendSettingsEntity entity = repository.findById(SINGLETON_ID)
                .orElseGet(() -> {
                    NewsletterSendSettingsEntity newEntity = new NewsletterSendSettingsEntity();
                    newEntity.setId(SINGLETON_ID);
                    return newEntity;
                });
        entity.setAutoSendOverride(override);
        entity.setOverrideDate(date);
        repository.save(entity);
        log.debug("setOverride() | return=void");
    }
}
