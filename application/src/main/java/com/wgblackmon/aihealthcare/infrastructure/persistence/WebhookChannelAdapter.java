package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-backed implementation of {@link WebhookChannelPort}.
 *
 * <p>Converts between the immutable {@link WebhookChannel} domain record
 * and the mutable {@link WebhookChannelEntity} JPA entity. The
 * {@code subscribedEvents} field is stored as a pipe-delimited string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class WebhookChannelAdapter implements WebhookChannelPort {

    private final WebhookChannelRepository repository;

    public WebhookChannelAdapter(WebhookChannelRepository repository) {
        log.debug("WebhookChannelAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(WebhookChannel channel) {
        log.debug("save() | id={}, ownerEmail={}, channelType={}", channel.id(), channel.ownerEmail(), channel.channelType());
        repository.save(toEntity(channel));
        log.debug("save() | return=void");
    }

    @Override
    public void deleteById(String id) {
        log.debug("deleteById() | id={}", id);
        repository.deleteById(id);
        log.debug("deleteById() | return=void");
    }

    @Override
    public Optional<WebhookChannel> findById(String id) {
        log.debug("findById() | id={}", id);
        Optional<WebhookChannel> result = repository.findById(id).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<WebhookChannel> findByOwnerEmail(String ownerEmail) {
        log.debug("findByOwnerEmail() | ownerEmail={}", ownerEmail);
        List<WebhookChannelEntity> entities = repository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
        List<WebhookChannel> result = new ArrayList<>();
        for (WebhookChannelEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findByOwnerEmail() | return={} channels", result.size());
        return result;
    }

    @Override
    public List<WebhookChannel> findActiveByEventType(WebhookEventType eventType) {
        log.debug("findActiveByEventType() | eventType={}", eventType);
        List<WebhookChannelEntity> activeEntities = repository.findByActiveTrue();
        String eventName = eventType.name();
        List<WebhookChannel> result = new ArrayList<>();
        for (WebhookChannelEntity entity : activeEntities) {
            if (entity.getSubscribedEvents().contains(eventName)) {
                result.add(toDomain(entity));
            }
        }
        log.debug("findActiveByEventType() | return={} channels", result.size());
        return result;
    }

    private WebhookChannelEntity toEntity(WebhookChannel channel) {
        WebhookChannelEntity entity = new WebhookChannelEntity();
        entity.setId(channel.id());
        entity.setOwnerEmail(channel.ownerEmail());
        entity.setName(channel.name());
        entity.setWebhookUrl(channel.webhookUrl());
        entity.setChannelType(channel.channelType().name());
        entity.setActive(channel.active());
        entity.setCreatedAt(channel.createdAt());

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (WebhookEventType event : channel.subscribedEvents()) {
            if (!first) {
                sb.append("|");
            }
            sb.append(event.name());
            first = false;
        }
        entity.setSubscribedEvents(sb.toString());
        return entity;
    }

    private WebhookChannel toDomain(WebhookChannelEntity entity) {
        Set<WebhookEventType> events = new LinkedHashSet<>();
        String[] parts = entity.getSubscribedEvents().split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                events.add(WebhookEventType.valueOf(trimmed));
            }
        }

        return new WebhookChannel(
                entity.getId(),
                entity.getOwnerEmail(),
                entity.getName(),
                entity.getWebhookUrl(),
                WebhookChannelType.valueOf(entity.getChannelType()),
                entity.isActive(),
                events,
                entity.getCreatedAt()
        );
    }
}
