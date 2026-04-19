package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import com.wgblackmon.aihealthcare.domain.port.outbound.PromptVariantPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link PromptVariantPort}.
 *
 * <p>Persists and retrieves {@link PromptVariant} domain records via
 * {@link PromptVariantRepository}.  All mapping between the immutable domain
 * record and the mutable {@link PromptVariantEntity} is performed inside this
 * adapter — entities never escape to the application or domain layers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Slf4j
@Component
public class PromptVariantAdapter implements PromptVariantPort {

    private final PromptVariantRepository repository;

    public PromptVariantAdapter(PromptVariantRepository repository) {
        log.debug("PromptVariantAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(PromptVariant variant) {
        log.debug("save() | variantId={}", variant.variantId());
        repository.save(toEntity(variant));
        log.debug("save() | return=void");
    }

    @Override
    public PromptVariant findByVariantId(String variantId) {
        log.debug("findByVariantId() | variantId={}", variantId);
        PromptVariantEntity entity = repository.findById(variantId)
                .orElseThrow(() -> new PromptVariantNotFoundException(variantId));
        PromptVariant result = toDomain(entity);
        log.debug("findByVariantId() | return={}", result.variantId());
        return result;
    }

    @Override
    public List<PromptVariant> findAll() {
        log.debug("findAll() | (no args)");
        List<PromptVariantEntity> entities = repository.findAll();
        List<PromptVariant> result = new ArrayList<>();
        for (PromptVariantEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} variants", result.size());
        return result;
    }

    @Override
    public void delete(String variantId) {
        log.debug("delete() | variantId={}", variantId);
        if (!repository.existsById(variantId)) {
            throw new PromptVariantNotFoundException(variantId);
        }
        repository.deleteById(variantId);
        log.debug("delete() | return=void");
    }

    private PromptVariantEntity toEntity(PromptVariant variant) {
        log.debug("toEntity() | variantId={}", variant.variantId());
        PromptVariantEntity entity = new PromptVariantEntity();
        entity.setVariantId(variant.variantId());
        entity.setName(variant.name());
        entity.setTemplateText(variant.templateText());
        entity.setDescription(variant.description());
        entity.setCreatedAt(variant.createdAt());
        log.debug("toEntity() | return={}", entity.getVariantId());
        return entity;
    }

    private PromptVariant toDomain(PromptVariantEntity entity) {
        log.debug("toDomain() | variantId={}", entity.getVariantId());
        PromptVariant result = new PromptVariant(
                entity.getVariantId(),
                entity.getName(),
                entity.getTemplateText(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
        log.debug("toDomain() | return={}", result.variantId());
        return result;
    }
}
