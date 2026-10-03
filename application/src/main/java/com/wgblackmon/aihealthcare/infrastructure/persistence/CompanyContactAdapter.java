package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyContactPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing {@link CompanyContactPort}.
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
public class CompanyContactAdapter implements CompanyContactPort {

    private final CompanyContactRepository repository;

    public CompanyContactAdapter(CompanyContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompanyContact save(CompanyContact contact) {
        log.debug("save() | slug={}, fullName={}, status={}", contact.slug(), contact.fullName(), contact.status());
        CompanyContactEntity entity = toEntity(contact);
        CompanyContactEntity saved = repository.save(entity);
        CompanyContact result = toDomain(saved);
        log.debug("save() | return={}", result.id());
        return result;
    }

    @Override
    public Optional<CompanyContact> findById(Long id) {
        log.debug("findById() | id={}", id);
        Optional<CompanyContact> result = repository.findById(id).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<CompanyContact> findBySlug(String slug) {
        log.debug("findBySlug() | slug={}", slug);
        List<CompanyContact> result = repository.findBySlugOrderByCreatedAtDesc(slug)
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

    private CompanyContactEntity toEntity(CompanyContact c) {
        CompanyContactEntity e = new CompanyContactEntity();
        e.setId(c.id());
        e.setSlug(c.slug());
        e.setFullName(c.fullName());
        e.setJobTitle(c.jobTitle());
        e.setEmail(c.email());
        e.setLinkedinUrl(c.linkedinUrl());
        e.setSource(c.source().name());
        e.setStatus(c.status().name());
        e.setNotes(c.notes());
        e.setCreatedAt(c.createdAt());
        e.setUpdatedAt(c.updatedAt());
        return e;
    }

    private CompanyContact toDomain(CompanyContactEntity e) {
        return new CompanyContact(
                e.getId(),
                e.getSlug(),
                e.getFullName(),
                e.getJobTitle(),
                e.getEmail(),
                e.getLinkedinUrl(),
                ContactSource.valueOf(e.getSource()),
                ContactStatus.valueOf(e.getStatus()),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
