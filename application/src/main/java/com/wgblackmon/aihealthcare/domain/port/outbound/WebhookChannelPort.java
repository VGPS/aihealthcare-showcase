package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link WebhookChannel} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface WebhookChannelPort {

    void save(WebhookChannel channel);

    void deleteById(String id);

    Optional<WebhookChannel> findById(String id);

    List<WebhookChannel> findByOwnerEmail(String ownerEmail);

    List<WebhookChannel> findActiveByEventType(WebhookEventType eventType);
}
