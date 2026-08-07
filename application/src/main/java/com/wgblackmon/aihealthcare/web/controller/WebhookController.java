package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.model.WebhookPayload;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
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
 * @updated 2026-08-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookChannelPort webhookChannelPort;
    private final WebhookNotificationPort webhookNotificationPort;
    private final AppUserPort appUserPort;

    public WebhookController(WebhookChannelPort webhookChannelPort,
                              WebhookNotificationPort webhookNotificationPort,
                              AppUserPort appUserPort) {
        log.debug("WebhookController() | webhookChannelPort={}, webhookNotificationPort={}, appUserPort={}",
                  webhookChannelPort.getClass().getSimpleName(),
                  webhookNotificationPort.getClass().getSimpleName(),
                  appUserPort.getClass().getSimpleName());
        this.webhookChannelPort = webhookChannelPort;
        this.webhookNotificationPort = webhookNotificationPort;
        this.appUserPort = appUserPort;
    }

    @PostMapping
    public ResponseEntity<?> createChannel(@RequestBody Map<String, Object> body, Principal principal) {
        log.debug("createChannel() | body={}, principal={}", body, principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("createChannel() | return=401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = principal.getName();
        SubscriptionTier tier = resolveTier(email);
        if (tier != SubscriptionTier.SUBSCRIBER && tier != SubscriptionTier.ENTERPRISE && !isAdmin(principal)) {
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
        if (!channel.ownerEmail().equals(principal.getName()) && !isAdmin(principal)) {
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
        if (!channel.ownerEmail().equals(principal.getName()) && !isAdmin(principal)) {
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

    private SubscriptionTier resolveTier(String email) {
        log.debug("resolveTier() | email={}", email);
        Optional<AppUser> user = appUserPort.findByEmail(email);
        SubscriptionTier tier = user.map(AppUser::tier).orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", tier);
        return tier;
    }

    private boolean isAdmin(Principal principal) {
        if (!(principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth)) {
            return false;
        }
        for (var authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
