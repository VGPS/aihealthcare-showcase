package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for managing webhook channel configurations.
 *
 * <p>Provides CRUD operations for webhook channels and a test endpoint
 * to send a test notification. Only SUBSCRIBER, ENTERPRISE, and ADMIN
 * users can create webhook channels.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-09-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookChannelPort webhookChannelPort;
    private final WebhookNotificationPort webhookNotificationPort;
    private final TierResolver tierResolver;

    public WebhookController(WebhookChannelPort webhookChannelPort,
                              WebhookNotificationPort webhookNotificationPort,
                              TierResolver tierResolver) {
        log.debug("WebhookController() | webhookChannelPort={}, webhookNotificationPort={}, tierResolver={}",
                  webhookChannelPort.getClass().getSimpleName(),
                  webhookNotificationPort.getClass().getSimpleName(),
                  tierResolver.getClass().getSimpleName());
        this.webhookChannelPort = webhookChannelPort;
        this.webhookNotificationPort = webhookNotificationPort;
        this.tierResolver = tierResolver;
    }

    @PostMapping
    public ResponseEntity<?> createChannel(@RequestBody Map<String, Object> body, Principal principal) {
        log.debug("createChannel() | body={}, principal={}", body, principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("createChannel() | return=401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = principal.getName();
        SubscriptionTier tier = tierResolver.resolveTier(principal);
        if (tier != SubscriptionTier.SUBSCRIBER && tier != SubscriptionTier.ENTERPRISE && !tierResolver.isAdmin(principal)) {
            log.debug("createChannel() | return=403 (tier={})", tier);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Webhook channels require SUBSCRIBER or ENTERPRISE tier"));
        }

        String name = (String) body.get("name");
        String webhookUrl = (String) body.get("webhookUrl");
        String channelTypeStr = (String) body.get("channelType");

        if (name == null || name.isBlank() || webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("createChannel() | return=400 (missing fields)");
            return ResponseEntity.badRequest().body(Map.of("error", "name and webhookUrl are required"));
        }

        String urlRejection = validateWebhookUrl(webhookUrl);
        if (urlRejection != null) {
            log.warn("createChannel() | return=400 ({})", urlRejection);
            return ResponseEntity.badRequest().body(Map.of("error", urlRejection));
        }

        WebhookChannelType channelType;
        try {
            channelType = WebhookChannelType.valueOf(channelTypeStr != null ? channelTypeStr : "CUSTOM");
        } catch (IllegalArgumentException e) {
            channelType = WebhookChannelType.CUSTOM;
        }

        Set<WebhookEventType> events = new LinkedHashSet<>();
        Object eventsObj = body.get("events");
        if (eventsObj instanceof List<?>) {
            for (Object ev : (List<?>) eventsObj) {
                try {
                    events.add(WebhookEventType.valueOf(ev.toString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (events.isEmpty()) {
            for (WebhookEventType evt : WebhookEventType.values()) {
                events.add(evt);
            }
        }

        WebhookChannel channel = new WebhookChannel(
                UUID.randomUUID().toString(), email, name, webhookUrl,
                channelType, true, events, Instant.now()
        );
        webhookChannelPort.save(channel);

        log.debug("createChannel() | return=201 id={}", channel.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", channel.id(),
                "name", channel.name(),
                "channelType", channel.channelType().name(),
                "active", channel.active()
        ));
    }

    @GetMapping
    public ResponseEntity<?> listChannels(Principal principal) {
        log.debug("listChannels() | principal={}", principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("listChannels() | return=401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<WebhookChannel> channels = webhookChannelPort.findByOwnerEmail(principal.getName());
        List<Map<String, Object>> result = new ArrayList<>();
        for (WebhookChannel ch : channels) {
            List<String> eventNames = new ArrayList<>();
            for (WebhookEventType evt : ch.subscribedEvents()) {
                eventNames.add(evt.name());
            }
            result.add(Map.of(
                    "id", ch.id(),
                    "name", ch.name(),
                    "webhookUrl", ch.webhookUrl(),
                    "channelType", ch.channelType().name(),
                    "active", ch.active(),
                    "events", eventNames,
                    "createdAt", ch.createdAt().toString()
            ));
        }

        log.debug("listChannels() | return={} channels", result.size());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteChannel(@PathVariable String id, Principal principal) {
        log.debug("deleteChannel() | id={}, principal={}", id, principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("deleteChannel() | return=401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<WebhookChannel> found = webhookChannelPort.findById(id);
        if (found.isEmpty()) {
            log.debug("deleteChannel() | return=404");
            return ResponseEntity.notFound().build();
        }

        WebhookChannel channel = found.get();
        if (!channel.ownerEmail().equals(principal.getName()) && !tierResolver.isAdmin(principal)) {
            log.debug("deleteChannel() | return=403 (ownership mismatch)");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        webhookChannelPort.deleteById(id);
        log.debug("deleteChannel() | return=204");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testChannel(@PathVariable String id, Principal principal) {
        log.debug("testChannel() | id={}, principal={}", id, principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("testChannel() | return=401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<WebhookChannel> found = webhookChannelPort.findById(id);
        if (found.isEmpty()) {
            log.debug("testChannel() | return=404");
            return ResponseEntity.notFound().build();
        }

        WebhookChannel channel = found.get();
        if (!channel.ownerEmail().equals(principal.getName()) && !tierResolver.isAdmin(principal)) {
            log.debug("testChannel() | return=403");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WebhookPayload testPayload = new WebhookPayload(
                WebhookEventType.PIPELINE_COMPLETE,
                "Test Notification",
                "This is a test notification from AIHealthcare. If you see this, your webhook is configured correctly!",
                null,
                Instant.now()
        );

        boolean success = webhookNotificationPort.send(channel, testPayload);
        if (success) {
            log.debug("testChannel() | return=200 (success)");
            return ResponseEntity.ok(Map.of("status", "delivered"));
        } else {
            log.debug("testChannel() | return=502 (delivery failed)");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("status", "failed", "error", "Webhook delivery failed"));
        }
    }

    String validateWebhookUrl(String url) {
        log.debug("validateWebhookUrl() | url={}", url);
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equals("https")) {
                return "Webhook URL must use HTTPS";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "Webhook URL has no host";
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    return "Webhook URL must not resolve to a private or loopback address";
                }
            }
        } catch (Exception e) {
            return "Invalid webhook URL: " + e.getMessage();
        }
        log.debug("validateWebhookUrl() | return=null (valid)");
        return null;
    }

}
