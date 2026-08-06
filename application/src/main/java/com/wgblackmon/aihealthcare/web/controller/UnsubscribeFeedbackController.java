package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight REST endpoint that captures unsubscribe feedback reasons.
 *
 * <p>Called via a tracking pixel from the unsubscribe page's JavaScript.
 * Logs the feedback reason for analytics review — no persistence needed
 * at this stage.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Slf4j
@RestController
public class UnsubscribeFeedbackController {

    @GetMapping("/api/v1/feedback/unsubscribe")
    public ResponseEntity<?> recordFeedback(@RequestParam(value = "reason", required = false) String reason) {
        log.debug("recordFeedback() | reason={}", reason);

        if (reason != null && !reason.isBlank()) {
            log.info("UNSUBSCRIBE_FEEDBACK | reason={}", reason);
        }

        log.debug("recordFeedback() | return=ok");
        return ResponseEntity.ok(Map.of("recorded", true));
    }
}
