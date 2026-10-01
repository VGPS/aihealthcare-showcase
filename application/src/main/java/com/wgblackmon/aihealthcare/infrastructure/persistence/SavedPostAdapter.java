package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.outbound.SavedPostPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for {@link SavedPostPort} — persists saved social post drafts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Slf4j
@Component
public class SavedPostAdapter implements SavedPostPort {

    private final SavedPostRepository repository;

    public SavedPostAdapter(SavedPostRepository repository) {
        log.debug("SavedPostAdapter() | repository={}", repository);
        this.repository = repository;
    }

    @Override
    public SavedPost save(SavedPost post) {
        log.debug("save() | postId={}", post.postId());
        SavedPostEntity entity = toEntity(post);
        SavedPostEntity saved = repository.save(entity);
        SavedPost result = toDomain(saved);
        log.debug("save() | return={}", result.postId());
        return result;
    }

    @Override
    public Optional<SavedPost> findById(String postId) {
        log.debug("findById() | postId={}", postId);
        Optional<SavedPost> result = repository.findById(postId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? result.get().postId() : "empty");
        return result;
    }

    @Override
    public List<SavedPost> findAll() {
        log.debug("findAll()");
        List<SavedPost> result = repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
        log.debug("findAll() | return=count:{}", result.size());
        return result;
    }

    @Override
    public List<SavedPost> findByStatus(SavedPostStatus status) {
        log.debug("findByStatus() | status={}", status);
        List<SavedPost> result = repository.findByStatusOrderByCreatedAtDesc(status.name()).stream()
                .map(this::toDomain)
                .toList();
        log.debug("findByStatus() | return=count:{}", result.size());
        return result;
    }

    @Override
    public void delete(String postId) {
        log.debug("delete() | postId={}", postId);
        repository.deleteById(postId);
        log.debug("delete() | return=void");
    }

    private SavedPostEntity toEntity(SavedPost post) {
        return new SavedPostEntity(
                post.postId(),
                post.platform().name(),
                post.digestDate(),
                post.body(),
                post.comment(),
                post.yourTake(),
                post.status().name(),
                post.createdAt(),
                post.updatedAt(),
                post.postedAt()
        );
    }

    private SavedPost toDomain(SavedPostEntity e) {
        return new SavedPost(
                e.getPostId(),
                SocialPlatform.valueOf(e.getPlatform()),
                e.getDigestDate(),
                e.getBody(),
                e.getComment(),
                e.getYourTake(),
                SavedPostStatus.valueOf(e.getStatus()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getPostedAt()
        );
    }
}
