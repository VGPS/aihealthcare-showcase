package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.web.dto.DeliverRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the manual newsletter delivery trigger.
 *
 * <p>A single endpoint is provided:
 * <ul>
 *   <li><b>POST /api/v1/newsletter/deliver</b> — looks up the specified run,
 *       sends it to all active subscribers, and updates the run status to SENT.
 *       Returns 204 No Content on success; 404 if the run ID is unknown.</li>
 * </ul>
 *
 * <p>This endpoint exists for manual and operational use (e.g., re-sending a
 * run, testing the delivery pipeline with MailHog).  The weekly automated
 * delivery is driven by {@code NewsletterGenerationScheduler} in Slice 3c and
 * does not go through this controller.
 *
 * <p>Domain exceptions are mapped to HTTP status codes by
 * {@link GlobalExceptionHandler} — no try/catch blocks here.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/newsletter")
public class NewsletterDeliveryController {

    private final DeliverNewsletterUseCase deliverNewsletterUseCase;

    public NewsletterDeliveryController(DeliverNewsletterUseCase deliverNewsletterUseCase) {
        log.debug("NewsletterDeliveryController() | deliverNewsletterUseCase={}",
                  deliverNewsletterUseCase.getClass().getSimpleName());
        this.deliverNewsletterUseCase = deliverNewsletterUseCase;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/newsletter/deliver
    // -------------------------------------------------------------------------

    /**
     * Manually triggers delivery of a newsletter run to all active subscribers.
     *
     * @param request Body containing the {@code runId} to deliver.
     * @return 204 No Content on success; 404 if the run is not found (via
     *         {@link GlobalExceptionHandler}).
     */
    @PostMapping("/deliver")
    public ResponseEntity<Void> deliver(@RequestBody DeliverRequest request) {
        log.debug("deliver() | request={}", request);

        deliverNewsletterUseCase.deliver(request.runId());

        log.info("deliver() | Delivery triggered: runId={}", request.runId());
        log.debug("deliver() | return=204");
        return ResponseEntity.noContent().build();
    }
}
