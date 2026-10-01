package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSavedPostsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SavedPostPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for saved social post draft management.
 *
 * <p>Handles CRUD lifecycle for drafts: create from the social post generator,
 * edit via TinyMCE, mark as posted once published, or archive/delete.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public class SavedPostService implements ManageSavedPostsUseCase {

    private final SavedPostPort savedPostPort;

    public SavedPostService(SavedPostPort savedPostPort) {
        this.savedPostPort = savedPostPort;
    }

    @Override
    public SavedPost save(SocialPlatform platform, String digestDate, String body,
                          String comment, String yourTake) {
        Instant now = Instant.now();
        SavedPost post = new SavedPost(
                UUID.randomUUID().toString(),
                platform,
                digestDate,
                body,
                comment,
                yourTake == null ? "" : yourTake,
                SavedPostStatus.DRAFT,
                now,
                now,
                null
        );
        return savedPostPort.save(post);
    }

    @Override
    public Optional<SavedPost> findById(String postId) {
        return savedPostPort.findById(postId);
    }

    @Override
    public List<SavedPost> listDrafts() {
        return savedPostPort.findByStatus(SavedPostStatus.DRAFT);
    }

    @Override
    public SavedPost update(String postId, String body, String comment, String yourTake) {
        SavedPost existing = savedPostPort.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        SavedPost updated = new SavedPost(
                existing.postId(),
                existing.platform(),
                existing.digestDate(),
                body != null ? body : existing.body(),
                comment,
                yourTake != null ? yourTake : existing.yourTake(),
                existing.status(),
                existing.createdAt(),
                Instant.now(),
                existing.postedAt()
        );
        return savedPostPort.save(updated);
    }

    @Override
    public SavedPost markPosted(String postId) {
        SavedPost existing = savedPostPort.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        SavedPost posted = new SavedPost(
                existing.postId(),
                existing.platform(),
                existing.digestDate(),
                existing.body(),
                existing.comment(),
                existing.yourTake(),
                SavedPostStatus.POSTED,
                existing.createdAt(),
                Instant.now(),
                Instant.now()
        );
        return savedPostPort.save(posted);
    }

    @Override
    public SavedPost archive(String postId) {
        SavedPost existing = savedPostPort.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        SavedPost archived = new SavedPost(
                existing.postId(),
                existing.platform(),
                existing.digestDate(),
                existing.body(),
                existing.comment(),
                existing.yourTake(),
                SavedPostStatus.ARCHIVED,
                existing.createdAt(),
                Instant.now(),
                existing.postedAt()
        );
        return savedPostPort.save(archived);
    }

    @Override
    public void delete(String postId) {
        savedPostPort.delete(postId);
    }
}
