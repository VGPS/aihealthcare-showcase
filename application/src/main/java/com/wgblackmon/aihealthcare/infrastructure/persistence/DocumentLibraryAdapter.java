package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link DocumentLibraryPort}.
 *
 * <p>Converts between the immutable {@link DocumentRecord} domain record
 * and the mutable {@link DocumentLibraryEntity} JPA entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@Slf4j
@Component
public class DocumentLibraryAdapter implements DocumentLibraryPort {

    private final DocumentLibraryRepository repository;

    public DocumentLibraryAdapter(DocumentLibraryRepository repository) {
        log.debug("DocumentLibraryAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(DocumentRecord record) {
        log.debug("save() | docId={}, filename={}, status={}", record.docId(), record.filename(), record.status());
        repository.save(toEntity(record));
        log.debug("save() | return=void");
    }

    @Override
    public List<DocumentRecord> findAll() {
        log.debug("findAll()");
        List<DocumentLibraryEntity> entities = repository.findAllByOrderByUploadedAtDesc();
        List<DocumentRecord> result = new ArrayList<>();
        for (DocumentLibraryEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} records", result.size());
        return result;
    }

    @Override
    public Optional<DocumentRecord> findById(String docId) {
        log.debug("findById() | docId={}", docId);
        Optional<DocumentRecord> result = repository.findById(docId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public void updateStatus(String docId, DocumentStatus status,
                             int chunkCount, String wikiPageSlug, String errorMessage) {
        log.debug("updateStatus() | docId={}, status={}, chunkCount={}, wikiPageSlug={}",
                docId, status, chunkCount, wikiPageSlug);
        repository.findById(docId).ifPresent(entity -> {
            entity.setStatus(status.name());
            entity.setChunkCount(chunkCount);
            entity.setWikiPageSlug(wikiPageSlug);
            entity.setErrorMessage(errorMessage);
            repository.save(entity);
        });
        log.debug("updateStatus() | return=void");
    }

    private DocumentLibraryEntity toEntity(DocumentRecord record) {
        DocumentLibraryEntity entity = new DocumentLibraryEntity();
        entity.setDocId(record.docId());
        entity.setFilename(record.filename());
        entity.setSourceLabel(record.sourceLabel());
        entity.setUploadedAt(record.uploadedAt());
        entity.setChunkCount(record.chunkCount());
        entity.setWikiPageSlug(record.wikiPageSlug());
        entity.setStatus(record.status().name());
        entity.setErrorMessage(record.errorMessage());
        return entity;
    }

    private DocumentRecord toDomain(DocumentLibraryEntity entity) {
        return new DocumentRecord(
                entity.getDocId(),
                entity.getFilename(),
                entity.getSourceLabel(),
                entity.getUploadedAt(),
                entity.getChunkCount(),
                entity.getWikiPageSlug(),
                DocumentStatus.valueOf(entity.getStatus()),
                entity.getErrorMessage()
        );
    }
}
