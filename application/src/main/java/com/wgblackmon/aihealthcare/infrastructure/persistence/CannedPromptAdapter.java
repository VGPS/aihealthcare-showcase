package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.CannedPrompt;
import com.wgblackmon.aihealthcare.domain.model.DataParameter;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.CannedPromptPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link CannedPromptPort}.
 *
 * <p>The {@code parameters} field on {@link CannedPrompt} is a
 * {@code List<DataParameter>} serialised to/from JSON in the
 * {@code parameters_json} TEXT column.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class CannedPromptAdapter implements CannedPromptPort {

    private static final TypeReference<List<DataParameter>> PARAM_LIST_TYPE =
            new TypeReference<>() {};

    private final EnterpriseDataPromptRepository repository;
    private final ObjectMapper objectMapper;

    public CannedPromptAdapter(EnterpriseDataPromptRepository repository,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        log.debug("CannedPromptAdapter() | repository={}, objectMapper={}",
                repository.getClass().getSimpleName(), objectMapper.getClass().getSimpleName());
    }

    @Override
    public List<CannedPrompt> findAllActive() {
        log.debug("findAllActive()");
        List<CannedPrompt> result = repository.findByActiveTrue()
                .stream().map(this::toDomain).toList();
        log.debug("findAllActive() | return={} prompts", result.size());
        return result;
    }

    @Override
    public List<CannedPrompt> findByFeedId(String feedId) {
        log.debug("findByFeedId() | feedId={}", feedId);
        List<CannedPrompt> result = repository.findByFeedIdAndActiveTrue(feedId)
                .stream().map(this::toDomain).toList();
        log.debug("findByFeedId() | return={} prompts", result.size());
        return result;
    }

    @Override
    public Optional<CannedPrompt> findById(String promptId) {
        log.debug("findById() | promptId={}", promptId);
        Optional<CannedPrompt> result = repository.findById(promptId)
                .map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent());
        return result;
    }

    @Override
    @Transactional
    public CannedPrompt save(CannedPrompt prompt) {
        log.debug("save() | promptId={}", prompt.promptId());
        EnterpriseDataPromptEntity entity = toEntity(prompt);
        EnterpriseDataPromptEntity saved = repository.save(entity);
        CannedPrompt result = toDomain(saved);
        log.debug("save() | return={}", result.promptId());
        return result;
    }

    @Override
    @Transactional
    public void delete(String promptId) {
        log.debug("delete() | promptId={}", promptId);
        repository.deleteById(promptId);
        log.debug("delete() | return=void");
    }

    // ---- mapping helpers ----

    private EnterpriseDataPromptEntity toEntity(CannedPrompt p) {
        EnterpriseDataPromptEntity e = new EnterpriseDataPromptEntity();
        e.setPromptId(p.promptId());
        e.setLabel(p.label());
        e.setDescription(p.description());
        e.setFeedId(p.feedId());
        e.setTemplateText(p.templateText());
        e.setParametersJson(serialiseParameters(p.parameters()));
        e.setMinTier(p.minTier().name());
        e.setActive(p.active());
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private CannedPrompt toDomain(EnterpriseDataPromptEntity e) {
        return new CannedPrompt(
                e.getPromptId(),
                e.getLabel(),
                e.getDescription(),
                e.getFeedId(),
                e.getTemplateText(),
                deserialiseParameters(e.getParametersJson()),
                SubscriptionTier.valueOf(e.getMinTier()),
                e.isActive()
        );
    }

    private String serialiseParameters(List<DataParameter> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            log.error("serialiseParameters() | failed to serialise DataParameter list", ex);
            return null;
        }
    }

    private List<DataParameter> deserialiseParameters(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, PARAM_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            log.error("deserialiseParameters() | failed to parse DataParameter JSON", ex);
            return List.of();
        }
    }
}
