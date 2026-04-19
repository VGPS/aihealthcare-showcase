package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import com.wgblackmon.aihealthcare.domain.port.inbound.EvaluatePromptsUseCase;
import com.wgblackmon.aihealthcare.web.dto.VariantRequest;
import com.wgblackmon.aihealthcare.web.dto.VariantResponse;
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

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for managing prompt variants (CRUD).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/v1/variants}           — create a new variant</li>
 *   <li>{@code GET    /api/v1/variants}           — list all variants</li>
 *   <li>{@code GET    /api/v1/variants/{id}}      — get variant by ID</li>
 *   <li>{@code DELETE /api/v1/variants/{id}}      — delete variant by ID</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/variants")
public class PromptVariantController {

    private final EvaluatePromptsUseCase evaluatePromptsUseCase;

    public PromptVariantController(EvaluatePromptsUseCase evaluatePromptsUseCase) {
        log.debug("PromptVariantController() | evaluatePromptsUseCase={}",
                  evaluatePromptsUseCase.getClass().getSimpleName());
        this.evaluatePromptsUseCase = evaluatePromptsUseCase;
    }

    @PostMapping
    public ResponseEntity<VariantResponse> create(@RequestBody VariantRequest request) {
        log.debug("create() | request={}", request);

        PromptVariant variant = evaluatePromptsUseCase.createVariant(
                request.variantId(), request.name(),
                request.templateText(), request.description());

        VariantResponse result = toResponse(variant);
        log.info("create() | Variant created: variantId={}", variant.variantId());
        log.debug("create() | return={}", result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<VariantResponse>> list() {
        log.debug("list() | (no args)");

        List<PromptVariant> variants = evaluatePromptsUseCase.listVariants();
        List<VariantResponse> result = new ArrayList<>();
        for (PromptVariant variant : variants) {
            result.add(toResponse(variant));
        }

        log.debug("list() | return={} variants", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{variantId}")
    public ResponseEntity<VariantResponse> get(@PathVariable String variantId) {
        log.debug("get() | variantId={}", variantId);

        PromptVariant variant = evaluatePromptsUseCase.getVariant(variantId);

        VariantResponse result = toResponse(variant);
        log.debug("get() | return={}", result);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> delete(@PathVariable String variantId) {
        log.debug("delete() | variantId={}", variantId);

        evaluatePromptsUseCase.deleteVariant(variantId);

        log.info("delete() | Variant deleted: variantId={}", variantId);
        log.debug("delete() | return=void");
        return ResponseEntity.noContent().build();
    }

    private VariantResponse toResponse(PromptVariant variant) {
        log.debug("toResponse() | variantId={}", variant.variantId());
        VariantResponse result = new VariantResponse(
                variant.variantId(),
                variant.name(),
                variant.templateText(),
                variant.description(),
                variant.createdAt());
        log.debug("toResponse() | return={}", result);
        return result;
    }
}
