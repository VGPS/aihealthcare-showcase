package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSubscribersUseCase;
import com.wgblackmon.aihealthcare.web.dto.SubscriberRequest;
import com.wgblackmon.aihealthcare.web.dto.SubscriberResponse;
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
 * REST controller for newsletter subscriber management.
 *
 * <p>Exposes three endpoints:
 * <ul>
 *   <li><b>POST /api/v1/subscribers</b> — register a new subscriber (201 Created);
 *       returns 409 Conflict if the email is already registered.</li>
 *   <li><b>GET  /api/v1/subscribers</b> — list all subscribers (200 OK).</li>
 *   <li><b>DELETE /api/v1/subscribers/{email}</b> — remove a subscriber (204 No
 *       Content); returns 404 Not Found if the email is unknown.</li>
 * </ul>
 *
 * <p>This controller calls only the {@link ManageSubscribersUseCase} inbound port —
 * never {@code DeliveryService} or any infrastructure class directly.
 * Domain exceptions are translated to HTTP status codes by
 * {@link GlobalExceptionHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscribers")
public class SubscriberController {

    private final ManageSubscribersUseCase manageSubscribersUseCase;

    public SubscriberController(ManageSubscribersUseCase manageSubscribersUseCase) {
        log.debug("SubscriberController() | manageSubscribersUseCase={}",
                  manageSubscribersUseCase.getClass().getSimpleName());
        this.manageSubscribersUseCase = manageSubscribersUseCase;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/subscribers
    // -------------------------------------------------------------------------

    /**
     * Registers a new subscriber.
     *
     * @param request Body containing {@code email} and {@code name}.
     * @return 201 Created with the new {@link SubscriberResponse}; 400 if fields
     *         are blank; 409 if the email is already registered.
     */
    @PostMapping
    public ResponseEntity<SubscriberResponse> addSubscriber(@RequestBody SubscriberRequest request) {
        log.debug("addSubscriber() | request={}", request);

        Subscriber subscriber = manageSubscribersUseCase.addSubscriber(
                request.email(), request.name());

        SubscriberResponse response = toResponse(subscriber);
        log.info("addSubscriber() | Subscriber registered: email={}", subscriber.email());
        log.debug("addSubscriber() | return={}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/subscribers
    // -------------------------------------------------------------------------

    /**
     * Lists all subscribers, both active and inactive.
     *
     * @return 200 OK with the full subscriber list.
     */
    @GetMapping
    public ResponseEntity<List<SubscriberResponse>> listSubscribers() {
        log.debug("listSubscribers() | (no args)");

        List<Subscriber> subscribers = manageSubscribersUseCase.listSubscribers();
        List<SubscriberResponse> response = new ArrayList<>();
        for (Subscriber s : subscribers) {
            response.add(toResponse(s));
        }

        log.debug("listSubscribers() | return={} subscribers", response.size());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/subscribers/{email}
    // -------------------------------------------------------------------------

    /**
     * Removes an existing subscriber.
     *
     * @param email Path variable identifying the subscriber to remove.
     * @return 204 No Content on success; 404 if the email is not found (via
     *         {@link GlobalExceptionHandler}).
     */
    @DeleteMapping("/{email}")
    public ResponseEntity<Void> removeSubscriber(@PathVariable String email) {
        log.debug("removeSubscriber() | email={}", email);

        manageSubscribersUseCase.removeSubscriber(email);

        log.info("removeSubscriber() | Subscriber removed: email={}", email);
        log.debug("removeSubscriber() | return=204");
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Mapping helper
    // -------------------------------------------------------------------------

    private SubscriberResponse toResponse(Subscriber subscriber) {
        log.debug("toResponse() | email={}", subscriber.email());

        SubscriberResponse result = new SubscriberResponse(
                subscriber.email(),
                subscriber.name(),
                subscriber.active(),
                subscriber.subscribedAt()
        );

        log.debug("toResponse() | return={}", result);
        return result;
    }
}
