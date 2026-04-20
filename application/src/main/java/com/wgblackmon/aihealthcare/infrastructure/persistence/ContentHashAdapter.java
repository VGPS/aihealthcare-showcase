package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.port.outbound.ContentHashPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link ContentHashPort}.
 *
 * <p>Stores and retrieves SHA-256 content hashes for monitored web pages
 * using the {@code page_content_hashes} table.  The {@code saveHash} method
 * performs an upsert — if a row for the URL already exists, its hash and
 * timestamp are updated in place.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@Slf4j
@Component
public class ContentHashAdapter implements ContentHashPort {

    private final PageContentHashRepository repository;

    public ContentHashAdapter(PageContentHashRepository repository) {
        log.debug("ContentHashAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public String getHash(String pageUrl) {
        log.debug("getHash() | pageUrl={}", pageUrl);
        Optional<PageContentHashEntity> entity = repository.findByPageUrl(pageUrl);
        String result = entity.map(PageContentHashEntity::getContentHash).orElse(null);
        log.debug("getHash() | return={}", result);
        return result;
    }

    @Override
    public void saveHash(String pageUrl, String contentHash) {
        log.debug("saveHash() | pageUrl={}, contentHash={}", pageUrl, contentHash);
        Optional<PageContentHashEntity> existing = repository.findByPageUrl(pageUrl);
        PageContentHashEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setContentHash(contentHash);
            entity.setLastCheckedAt(Instant.now());
        } else {
            entity = new PageContentHashEntity(pageUrl, contentHash, Instant.now());
        }
        repository.save(entity);
        log.debug("saveHash() | return=void");
    }
}
