package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyOutreachPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing {@link CompanyOutreachPort}.
 *
 * <p>Enum fields are stored as their {@code name()} strings and parsed back
 * on read — the same pattern used throughout this project.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@Slf4j
@Component
public class CompanyOutreachAdapter implements CompanyOutreachPort {

    private final CompanyOutreachRepository repository;

    public CompanyOutreachAdapter(CompanyOutreachRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompanyOutreach save(CompanyOutreach outreach) {
        log.debug("save() | slug={}, purpose={}, status={}", outreach.slug(), outreach.purpose(), outreach.status());
        CompanyOutreachEntity entity = toEntity(outreach);
        CompanyOutreachEntity saved = repository.save(entity);
        CompanyOutreach result = toDomain(saved);
        log.debug("save() | return={}", result.id());
        return result;
    }

    @Override
    public Optional<CompanyOutreach> findById(Long id) {
        log.debug("findById() | id={}", id);
        Optional<CompanyOutreach> result = repository.findById(id).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent());
        return result;
    }

    @Override
    public Optional<CompanyOutreach> findBySlugAndPurpose(String slug, OutreachPurpose purpose) {
        log.debug("findBySlugAndPurpose() | slug={}, purpose={}", slug, purpose);
        Optional<CompanyOutreach> result = repository.findBySlugAndPurpose(slug, purpose.name()).map(this::toDomain);
        log.debug("findBySlugAndPurpose() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<CompanyOutreach> findAll() {
        log.debug("findAll()");
        List<CompanyOutreach> result = repository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findAll() | return=size:{}", result.size());
        return result;
    }

    @Override
    public List<CompanyOutreach> findBySlug(String slug) {
        log.debug("findBySlug() | slug={}", slug);
        List<CompanyOutreach> result = repository.findBySlug(slug)
                .stream().map(this::toDomain).collect(Collectors.toList());
        log.debug("findBySlug() | return=size:{}", result.size());
        return result;
    }

    @Override
    public void deleteById(Long id) {
        log.debug("deleteById() | id={}", id);
        repository.deleteById(id);
        log.debug("deleteById() | return=void");
    }

    // --- Mapping helpers ---

    private CompanyOutreachEntity toEntity(CompanyOutreach o) {
        CompanyOutreachEntity e = new CompanyOutreachEntity();
        e.setId(o.id());
        e.setSlug(o.slug());
        e.setPurpose(o.purpose().name());
        e.setStatus(o.status().name());
        e.setContactedAt(o.contactedAt());
        e.setNotes(o.notes());
        e.setCreatedAt(o.createdAt());
        e.setUpdatedAt(o.updatedAt());
        return e;
    }

    private CompanyOutreach toDomain(CompanyOutreachEntity e) {
        return new CompanyOutreach(
                e.getId(),
                e.getSlug(),
                OutreachPurpose.valueOf(e.getPurpose()),
                OutreachStatus.valueOf(e.getStatus()),
                e.getContactedAt(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
