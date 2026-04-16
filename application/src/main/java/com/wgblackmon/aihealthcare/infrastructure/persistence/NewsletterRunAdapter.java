package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link NewsletterRunPort}.
 *
 * <p>Persists and retrieves {@link NewsletterRun} domain records via
 * {@link NewsletterRunRepository}.  All mapping between the immutable domain
 * record and the mutable {@link NewsletterRunEntity} is performed inside this
 * adapter — entities never escape to the application or domain layers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@Slf4j
@Component
public class NewsletterRunAdapter implements NewsletterRunPort {

    private final NewsletterRunRepository repository;

    public NewsletterRunAdapter(NewsletterRunRepository repository) {
        log.debug("NewsletterRunAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(NewsletterRun run) {
        log.debug("save() | runId={}", run.runId());
        repository.save(toEntity(run));
        log.debug("save() | return=void");
    }

    @Override
    public NewsletterRun findByRunId(String runId) {
        log.debug("findByRunId() | runId={}", runId);
        NewsletterRunEntity entity = repository.findById(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
        NewsletterRun result = toDomain(entity);
        log.debug("findByRunId() | return={}", result.runId());
        return result;
    }

    @Override
    public List<NewsletterRun> findAll() {
        log.debug("findAll() |");
        List<NewsletterRunEntity> entities = repository.findAll();
        List<NewsletterRun> result = new ArrayList<>();
        for (NewsletterRunEntity entity : entities) {
            result.add(toDomain(entity));
        }
        List<NewsletterRun> unmodifiable = List.copyOf(result);
        log.debug("findAll() | return={} runs", unmodifiable.size());
        return unmodifiable;
    }

    private NewsletterRunEntity toEntity(NewsletterRun run) {
        log.debug("toEntity() | runId={}", run.runId());
        NewsletterRunEntity entity = new NewsletterRunEntity();
        entity.setRunId(run.runId());
        entity.setTitle(run.title());
        entity.setWeekOf(run.weekOf());
        entity.setHtmlContent(run.htmlContent());
        entity.setPlainTextContent(run.plainTextContent());
        entity.setStatus(run.status());
        entity.setGeneratedAt(run.generatedAt());
        log.debug("toEntity() | return={}", entity.getRunId());
        return entity;
    }

    private NewsletterRun toDomain(NewsletterRunEntity entity) {
        log.debug("toDomain() | runId={}", entity.getRunId());
        NewsletterRun result = new NewsletterRun(
                entity.getRunId(),
                entity.getTitle(),
                entity.getWeekOf(),
                entity.getHtmlContent(),
                entity.getPlainTextContent(),
                entity.getStatus(),
                entity.getGeneratedAt()
        );
        log.debug("toDomain() | return={}", result.runId());
        return result;
    }
}
