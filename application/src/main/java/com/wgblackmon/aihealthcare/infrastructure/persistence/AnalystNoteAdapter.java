package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link AnalystNotePort}.
 *
 * <p>Converts between the immutable {@link AnalystNote} domain record
 * and the mutable {@link AnalystNoteEntity} JPA entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class AnalystNoteAdapter implements AnalystNotePort {

    private final AnalystNoteRepository repository;

    public AnalystNoteAdapter(AnalystNoteRepository repository) {
        log.debug("AnalystNoteAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(AnalystNote note) {
        log.debug("save() | noteId={}, userEmail={}, targetType={}", note.noteId(), note.userEmail(), note.targetType());
        AnalystNoteEntity entity = toEntity(note);
        repository.save(entity);
        log.debug("save() | return=void");
    }

    @Override
    @Transactional
    public void delete(String noteId, String userEmail) {
        log.debug("delete() | noteId={}, userEmail={}", noteId, userEmail);
        repository.deleteByNoteIdAndUserEmail(noteId, userEmail);
        log.debug("delete() | return=void");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalystNote> findByUser(String email) {
        log.debug("findByUser() | email={}", email);
        List<AnalystNoteEntity> entities = repository.findByUserEmailOrderByUpdatedAtDesc(email);
        List<AnalystNote> result = new ArrayList<>();
        for (AnalystNoteEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByUser() | return={} notes", result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalystNote> findByUserAndTarget(String email, NoteTargetType targetType, String targetId) {
        log.debug("findByUserAndTarget() | email={}, targetType={}, targetId={}", email, targetType, targetId);
        List<AnalystNoteEntity> entities = repository.findByUserEmailAndTargetTypeAndTargetIdOrderByUpdatedAtDesc(
                email, targetType.name(), targetId);
        List<AnalystNote> result = new ArrayList<>();
        for (AnalystNoteEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByUserAndTarget() | return={} notes", result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalystNote> findById(String noteId) {
        log.debug("findById() | noteId={}", noteId);
        Optional<AnalystNote> result = repository.findById(noteId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    private AnalystNoteEntity toEntity(AnalystNote note) {
        AnalystNoteEntity entity = new AnalystNoteEntity();
        entity.setNoteId(note.noteId());
        entity.setUserEmail(note.userEmail());
        entity.setTargetType(note.targetType().name());
        entity.setTargetId(note.targetId());
        entity.setTargetLabel(note.targetLabel());
        entity.setContent(note.content());
        entity.setCreatedAt(note.createdAt());
        entity.setUpdatedAt(note.updatedAt());
        return entity;
    }

    private AnalystNote toDomain(AnalystNoteEntity entity) {
        return new AnalystNote(
                entity.getNoteId(),
                entity.getUserEmail(),
                NoteTargetType.valueOf(entity.getTargetType()),
                entity.getTargetId(),
                entity.getTargetLabel(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
