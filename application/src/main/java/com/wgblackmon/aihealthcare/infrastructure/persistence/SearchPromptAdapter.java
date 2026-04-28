package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link SearchPromptPort}.
 *
 * <p>Maps between the immutable {@link SearchPromptConfig} domain record and the
 * mutable {@link SearchPromptEntity} JPA entity. The {@code save} operation is
 * an upsert — if an entity with the given engine key already exists it is updated
 * in place; otherwise a new row is inserted.
 *
 * <p>Domain objects never hold references to JPA entities — the mapping methods
 * {@link #toDomain} and the inline entity construction in {@link #save} ensure the
 * two representations stay isolated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Slf4j
@Component
public class SearchPromptAdapter implements SearchPromptPort {

    private final SearchPromptRepository repository;

    public SearchPromptAdapter(SearchPromptRepository repository) {
        log.debug("SearchPromptAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
        log.debug("SearchPromptAdapter() | return=void");
    }

    @Override
    public Optional<SearchPromptConfig> findByEngine(String engine) {
        log.debug("findByEngine() | engine={}", engine);
        Optional<SearchPromptEntity> entity = repository.findById(engine);
        Optional<SearchPromptConfig> result = entity.map(this::toDomain);
        log.debug("findByEngine() | return={}", result.isPresent() ? result.get().name() : "empty");
        return result;
    }

    @Override
    public List<SearchPromptConfig> findAll() {
        log.debug("findAll()");
        List<SearchPromptEntity> entities = repository.findAll();
        List<SearchPromptConfig> result = new ArrayList<>();
        for (SearchPromptEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} configs", result.size());
        return result;
    }

    @Override
    public void save(SearchPromptConfig config) {
        log.debug("save() | engine={}", config.engine());
        SearchPromptEntity entity = repository.findById(config.engine())
                .orElse(new SearchPromptEntity(
                        config.engine(), config.name(),
                        config.templateText(), config.description(), config.active()));
        entity.setName(config.name());
        entity.setTemplateText(config.templateText());
        entity.setDescription(config.description());
        entity.setActive(config.active());
        repository.save(entity);
        log.debug("save() | return=void");
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private SearchPromptConfig toDomain(SearchPromptEntity entity) {
        log.debug("toDomain() | engine={}", entity.getEngine());
        SearchPromptConfig result = new SearchPromptConfig(
                entity.getEngine(),
                entity.getName(),
                entity.getTemplateText(),
                entity.getDescription() != null ? entity.getDescription() : "",
                entity.isActive()
        );
        log.debug("toDomain() | return={}", result.engine());
        return result;
    }
}
