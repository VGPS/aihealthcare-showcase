package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link ApiKeyPort}.
 *
 * <p>Converts between the domain {@link ApiKey} record and the
 * {@link ApiKeyEntity} JPA entity for persistence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class ApiKeyAdapter implements ApiKeyPort {

    private final ApiKeyRepository repository;

    public ApiKeyAdapter(ApiKeyRepository repository) {
        log.debug("ApiKeyAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(ApiKey apiKey) {
        log.debug("save() | id={}, ownerEmail={}", apiKey.id(), apiKey.ownerEmail());
        ApiKeyEntity entity = toEntity(apiKey);
        repository.save(entity);
        log.debug("save() | return=void");
    }

    @Override
    public Optional<ApiKey> findByKeyHash(String keyHash) {
        log.debug("findByKeyHash() | keyHash=[REDACTED]");
        Optional<ApiKeyEntity> entity = repository.findByKeyHash(keyHash);
        Optional<ApiKey> result = Optional.empty();
        if (entity.isPresent()) {
            result = Optional.of(toDomain(entity.get()));
        }
        log.debug("findByKeyHash() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public List<ApiKey> findAllByOwnerEmail(String ownerEmail) {
        log.debug("findAllByOwnerEmail() | ownerEmail={}", ownerEmail);
        List<ApiKeyEntity> entities = repository.findAllByOwnerEmail(ownerEmail);
        List<ApiKey> result = new ArrayList<>();
        for (ApiKeyEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAllByOwnerEmail() | return={} keys", result.size());
        return result;
    }

    @Override
    public void deleteById(String id) {
        log.debug("deleteById() | id={}", id);
        repository.deleteById(id);
        log.debug("deleteById() | return=void");
    }

    @Override
    public boolean existsById(String id) {
        log.debug("existsById() | id={}", id);
        boolean result = repository.existsById(id);
        log.debug("existsById() | return={}", result);
        return result;
    }

    @Override
    public Optional<ApiKey> findById(String id) {
        log.debug("findById() | id={}", id);
        Optional<ApiKeyEntity> entity = repository.findById(id);
        Optional<ApiKey> result = Optional.empty();
        if (entity.isPresent()) {
            result = Optional.of(toDomain(entity.get()));
        }
        log.debug("findById() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public int countByOwnerEmail(String ownerEmail) {
        log.debug("countByOwnerEmail() | ownerEmail={}", ownerEmail);
        int result = repository.countByOwnerEmail(ownerEmail);
        log.debug("countByOwnerEmail() | return={}", result);
        return result;
    }

    private ApiKeyEntity toEntity(ApiKey apiKey) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(apiKey.id());
        entity.setOwnerEmail(apiKey.ownerEmail());
        entity.setName(apiKey.name());
        entity.setKeyPrefix(apiKey.keyPrefix());
        entity.setKeyHash(apiKey.keyHash());
        entity.setActive(apiKey.active());
        entity.setCreatedAt(apiKey.createdAt());
        return entity;
    }

    private ApiKey toDomain(ApiKeyEntity entity) {
        return new ApiKey(
                entity.getId(),
                entity.getOwnerEmail(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getKeyHash(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
