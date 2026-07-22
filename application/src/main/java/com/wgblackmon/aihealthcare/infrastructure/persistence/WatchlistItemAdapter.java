package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link WatchlistPort}.
 *
 * <p>Converts between the immutable {@link WatchlistItem} domain record
 * and the mutable {@link WatchlistItemEntity} JPA entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class WatchlistItemAdapter implements WatchlistPort {

    private final WatchlistItemRepository repository;

    public WatchlistItemAdapter(WatchlistItemRepository repository) {
        log.debug("WatchlistItemAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(WatchlistItem item) {
        log.debug("save() | itemId={}, userEmail={}, itemType={}", item.itemId(), item.userEmail(), item.itemType());
        WatchlistItemEntity entity = toEntity(item);
        repository.save(entity);
        log.debug("save() | return=void");
    }

    @Override
    @Transactional
    public void delete(String itemId, String userEmail) {
        log.debug("delete() | itemId={}, userEmail={}", itemId, userEmail);
        repository.deleteByItemIdAndUserEmail(itemId, userEmail);
        log.debug("delete() | return=void");
    }

    @Override
    public List<WatchlistItem> findByUser(String email) {
        log.debug("findByUser() | email={}", email);
        List<WatchlistItemEntity> entities = repository.findByUserEmailOrderByCreatedAtDesc(email);
        List<WatchlistItem> result = new ArrayList<>();
        for (WatchlistItemEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByUser() | return={} items", result.size());
        return result;
    }

    @Override
    public List<WatchlistItem> findAll() {
        log.debug("findAll()");
        List<WatchlistItemEntity> entities = repository.findAll();
        List<WatchlistItem> result = new ArrayList<>();
        for (WatchlistItemEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} items", result.size());
        return result;
    }

    @Override
    public Optional<WatchlistItem> findById(String itemId) {
        log.debug("findById() | itemId={}", itemId);
        Optional<WatchlistItem> result = repository.findById(itemId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    private WatchlistItemEntity toEntity(WatchlistItem item) {
        WatchlistItemEntity entity = new WatchlistItemEntity();
        entity.setItemId(item.itemId());
        entity.setUserEmail(item.userEmail());
        entity.setItemType(item.itemType().name());
        entity.setValue(item.value());
        entity.setLabel(item.label());
        entity.setCreatedAt(item.createdAt());
        return entity;
    }

    private WatchlistItem toDomain(WatchlistItemEntity entity) {
        return new WatchlistItem(
                entity.getItemId(),
                entity.getUserEmail(),
                WatchlistItemType.valueOf(entity.getItemType()),
                entity.getValue(),
                entity.getLabel(),
                entity.getCreatedAt()
        );
    }
}
