package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageEditorialCalendarUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the healthcare AI editorial calendar.
 *
 * <p>Endpoints at {@code /api/v1/editorial/**} require authentication.
 * Status-advance mutation requires {@code ADMIN} role.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/editorial")
public class EditorialCalendarRestController {

    private final ManageEditorialCalendarUseCase editorialUseCase;

    public EditorialCalendarRestController(ManageEditorialCalendarUseCase editorialUseCase) {
        log.debug("EditorialCalendarRestController() | editorialUseCase={}",
                editorialUseCase.getClass().getSimpleName());
        this.editorialUseCase = editorialUseCase;
    }

    /**
     * Returns all editorial items, optionally filtered by status.
     *
     * @param status optional {@link EditorialStatus} name filter
     */
    @GetMapping
    public ResponseEntity<List<EditorialItem>> listItems(
            @RequestParam(required = false) String status) {
        log.debug("listItems() | status={}", status);
        List<EditorialItem> result;
        if (status != null && !status.isBlank()) {
            try {
                result = editorialUseCase.getByStatus(EditorialStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("listItems() | invalid status param: {}", status);
                result = editorialUseCase.getAll();
            }
        } else {
            result = editorialUseCase.getAll();
        }
        log.debug("listItems() | return={}", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the next recommended editorial item: the highest-priority PLANNED
     * item ordered by priority tier then preferred date.
     * Returns 204 No Content when the queue is empty.
     */
    @GetMapping("/next")
    public ResponseEntity<EditorialItem> getNext() {
        log.debug("getNext()");
        ResponseEntity<EditorialItem> result = editorialUseCase.getNext()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
        log.debug("getNext() | return={}", result.getStatusCode());
        return result;
    }

    /**
     * Returns a single editorial item by slug id.
     * Returns 404 when the item does not exist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EditorialItem> getById(@PathVariable String id) {
        log.debug("getById() | id={}", id);
        ResponseEntity<EditorialItem> result = editorialUseCase.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        log.debug("getById() | return={}", result.getStatusCode());
        return result;
    }

    /**
     * Advances an editorial item's lifecycle status by one step.
     * Requires {@code ADMIN} role.
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> advanceStatus(@PathVariable String id) {
        log.debug("advanceStatus() | id={}", id);
        try {
            EditorialItem updated = editorialUseCase.advanceStatus(id);
            Map<String, Object> result = Map.of(
                    "id", updated.id(),
                    "status", updated.status().name(),
                    "title", updated.title());
            log.debug("advanceStatus() | return={}", updated.status());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("advanceStatus() | not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
