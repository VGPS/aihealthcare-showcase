package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WebhookChannelPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the webhook configuration page at
 * {@code /settings/webhooks}.
 *
 * <p>Displays the user's configured webhook channels and provides
 * forms for adding new channels, testing, and deleting channels.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Controller
public class WebhookConfigController {

    private final WebhookChannelPort webhookChannelPort;

    public WebhookConfigController(WebhookChannelPort webhookChannelPort) {
        log.debug("WebhookConfigController() | webhookChannelPort={}", webhookChannelPort.getClass().getSimpleName());
        this.webhookChannelPort = webhookChannelPort;
    }

    @GetMapping("/settings/webhooks")
    public String webhooksPage(Model model, Principal principal) {
        log.debug("webhooksPage() | principal={}", principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("webhooksPage() | return=redirect:/login");
            return "redirect:/login";
        }

        String email = principal.getName();
        List<WebhookChannel> channels = webhookChannelPort.findByOwnerEmail(email);

        List<Map<String, Object>> channelList = new ArrayList<>();
        for (WebhookChannel ch : channels) {
            List<String> eventNames = new ArrayList<>();
            for (WebhookEventType evt : ch.subscribedEvents()) {
                eventNames.add(evt.name());
            }
            channelList.add(Map.of(
                    "id", ch.id(),
                    "name", ch.name(),
                    "webhookUrl", ch.webhookUrl(),
                    "channelType", ch.channelType().name(),
                    "active", ch.active(),
                    "events", eventNames,
                    "createdAt", ch.createdAt().toString()
            ));
        }

        model.addAttribute("channels", channelList);
        model.addAttribute("channelTypes", WebhookChannelType.values());
        model.addAttribute("eventTypes", WebhookEventType.values());
        model.addAttribute("activePage", "webhooks");

        log.debug("webhooksPage() | return=webhooks ({} channels)", channelList.size());
        return "webhooks";
    }
}
