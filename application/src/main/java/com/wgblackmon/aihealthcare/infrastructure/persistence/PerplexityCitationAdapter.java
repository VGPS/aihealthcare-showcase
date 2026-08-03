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
        List<PerplexityCitationEntity> entities = new ArrayList<>();
        for (PerplexityCitation citation : citations) {
            entities.add(toEntity(citation));
        }
        repository.saveAll(entities);
        log.debug("saveAll() | return=void");
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
        entity.setUrl(citation.url());
        entity.setContext(citation.context());
        entity.setCallType(citation.callType().name());
        entity.setRetrievedAt(citation.retrievedAt());
        return entity;
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
