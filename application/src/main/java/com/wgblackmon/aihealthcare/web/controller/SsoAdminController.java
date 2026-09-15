package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSsoProvidersUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for managing SSO Identity Provider configurations.
 *
 * <p>All endpoints require the ADMIN role (enforced by SecurityConfig
 * via {@code /admin/**} pattern). Provides a list view, create/edit
 * form, and delete action.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Slf4j
@Controller
@RequestMapping("/admin/sso")
public class SsoAdminController {

    private final ManageSsoProvidersUseCase ssoUseCase;

    public SsoAdminController(ManageSsoProvidersUseCase ssoUseCase) {
        log.debug("SsoAdminController() | ssoUseCase={}", ssoUseCase.getClass().getSimpleName());
        this.ssoUseCase = ssoUseCase;
    }

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("America/Chicago"));

    @GetMapping
    public String listProviders(Model model) {
        log.debug("listProviders() | (no args)");
        List<SsoIdentityProvider> providers = ssoUseCase.getAll();
        model.addAttribute("providers", providers);

        Map<String, String> updatedDates = new LinkedHashMap<>();
        for (SsoIdentityProvider idp : providers) {
            updatedDates.put(idp.registrationId(), DISPLAY_FMT.format(idp.updatedAt()));
        }
        model.addAttribute("updatedDates", updatedDates);
        log.debug("listProviders() | return=sso-providers");
        return "sso-providers";
    }

    @GetMapping("/new")
    public String newProviderForm(Model model) {
        log.debug("newProviderForm() | (no args)");
        model.addAttribute("editMode", false);
        log.debug("newProviderForm() | return=sso-provider-form");
        return "sso-provider-form";
    }

    @GetMapping("/{id}/edit")
    public String editProviderForm(@PathVariable String id, Model model) {
        log.debug("editProviderForm() | id={}", id);

        SsoIdentityProvider provider = ssoUseCase.getById(id).orElse(null);
        if (provider == null) {
            log.debug("editProviderForm() | return=redirect (not found)");
            return "redirect:/admin/sso";
        }

        model.addAttribute("provider", provider);
        model.addAttribute("editMode", true);
        log.debug("editProviderForm() | return=sso-provider-form");
        return "sso-provider-form";
    }

    @PostMapping
    public String createProvider(@RequestParam String registrationId,
                                 @RequestParam String label,
                                 @RequestParam String entityId,
                                 @RequestParam String ssoUrl,
                                 @RequestParam String certificate,
                                 @RequestParam(required = false) String metadataUrl,
                                 @RequestParam(defaultValue = "email") String emailAttribute,
                                 @RequestParam(defaultValue = "displayName") String displayNameAttribute,
                                 @RequestParam(defaultValue = "true") boolean active,
                                 RedirectAttributes redirectAttributes) {
        log.debug("createProvider() | registrationId={}", registrationId);

        try {
            Instant now = Instant.now();
            SsoIdentityProvider provider = new SsoIdentityProvider(
                    registrationId, label, entityId, ssoUrl, certificate,
                    metadataUrl != null && !metadataUrl.isBlank() ? metadataUrl : null,
                    emailAttribute, displayNameAttribute,
                    SubscriptionTier.ENTERPRISE, active, now, now);
            ssoUseCase.create(provider);
            redirectAttributes.addFlashAttribute("success", "SSO provider created: " + label);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        log.debug("createProvider() | return=redirect:/admin/sso");
        return "redirect:/admin/sso";
    }

    @PostMapping("/{id}")
    public String updateProvider(@PathVariable String id,
                                 @RequestParam String label,
                                 @RequestParam String entityId,
                                 @RequestParam String ssoUrl,
                                 @RequestParam String certificate,
                                 @RequestParam(required = false) String metadataUrl,
                                 @RequestParam(defaultValue = "email") String emailAttribute,
                                 @RequestParam(defaultValue = "displayName") String displayNameAttribute,
                                 @RequestParam(defaultValue = "true") boolean active,
                                 RedirectAttributes redirectAttributes) {
        log.debug("updateProvider() | id={}", id);

        try {
            SsoIdentityProvider existing = ssoUseCase.getById(id).orElseThrow(
                    () -> new IllegalArgumentException("Provider not found: " + id));

            SsoIdentityProvider updated = new SsoIdentityProvider(
                    id, label, entityId, ssoUrl, certificate,
                    metadataUrl != null && !metadataUrl.isBlank() ? metadataUrl : null,
                    emailAttribute, displayNameAttribute,
                    SubscriptionTier.ENTERPRISE, active,
                    existing.createdAt(), Instant.now());
            ssoUseCase.update(updated);
            redirectAttributes.addFlashAttribute("success", "SSO provider updated: " + label);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        log.debug("updateProvider() | return=redirect:/admin/sso");
        return "redirect:/admin/sso";
    }

    @PostMapping("/{id}/delete")
    public String deleteProvider(@PathVariable String id,
                                 RedirectAttributes redirectAttributes) {
        log.debug("deleteProvider() | id={}", id);

        try {
            ssoUseCase.delete(id);
            redirectAttributes.addFlashAttribute("success", "SSO provider deleted");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        log.debug("deleteProvider() | return=redirect:/admin/sso");
        return "redirect:/admin/sso";
    }
}
