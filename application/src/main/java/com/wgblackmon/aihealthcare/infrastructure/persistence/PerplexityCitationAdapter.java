package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyCallType;
import com.wgblackmon.aihealthcare.domain.model.PerplexityCitation;
import com.wgblackmon.aihealthcare.domain.port.outbound.PerplexityCitationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA adapter implementing {@link PerplexityCitationPort} for persisting
 * and querying Perplexity API citation audit trail records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@Slf4j
@Component
public class PerplexityCitationAdapter implements PerplexityCitationPort {

    private static final int URL_MAX_LENGTH = 2048;

    private final PerplexityCitationRepository repository;

    public PerplexityCitationAdapter(PerplexityCitationRepository repository) {
        log.debug("PerplexityCitationAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(PerplexityCitation citation) {
        log.debug("save() | citationId={}, companyId={}, callType={}",
                citation.citationId(), citation.companyId(), citation.callType());
        repository.save(toEntity(citation));
        log.debug("save() | return=void");
    }

    @Override
    public void saveAll(List<PerplexityCitation> citations) {
        log.debug("saveAll() | count={}", citations.size());
        int saved = 0;
        for (PerplexityCitation citation : citations) {
            try {
                repository.save(toEntity(citation));
                saved++;
            } catch (RuntimeException e) {
                log.warn("saveAll() | failed to persist citation {} — skipping. cause={}",
                        citation.citationId(), e.getMessage());
            }
        }
        log.debug("saveAll() | return=void, saved={}/{}", saved, citations.size());
    }

    @Override
    public List<PerplexityCitation> findByCompanyId(String companyId) {
        log.debug("findByCompanyId() | companyId={}", companyId);
        List<PerplexityCitationEntity> entities = repository.findByCompanyId(companyId);
        List<PerplexityCitation> result = new ArrayList<>();
        for (PerplexityCitationEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByCompanyId() | return={} citations", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    PerplexityCitationEntity toEntity(PerplexityCitation citation) {
        PerplexityCitationEntity entity = new PerplexityCitationEntity();
        entity.setCitationId(citation.citationId());
        entity.setCompanyId(citation.companyId());
        entity.setUrl(truncate(citation.citationId(), citation.url(), "url", URL_MAX_LENGTH));
        entity.setContext(citation.context());
        entity.setCallType(citation.callType().name());
        entity.setRetrievedAt(citation.retrievedAt());
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs.
     *
     * @param citationId the owning citation's id, for the warning log
     * @param value      the value to clip; null passes through unchanged
     * @param fieldName  the column name, for the warning log
     * @param maxLength  the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String citationId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | citation {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                citationId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
    }

    PerplexityCitation toDomain(PerplexityCitationEntity entity) {
        return new PerplexityCitation(
                entity.getCitationId(),
                entity.getCompanyId(),
                entity.getUrl(),
                entity.getContext(),
                CompanyCallType.valueOf(entity.getCallType()),
                entity.getRetrievedAt()
        );
    }
}
