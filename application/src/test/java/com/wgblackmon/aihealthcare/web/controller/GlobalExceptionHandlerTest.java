package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.ApiKeyCreationException;
import com.wgblackmon.aihealthcare.domain.exception.ApiKeyNotFoundException;
import com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException;
import com.wgblackmon.aihealthcare.domain.exception.DuplicateUserException;
import com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException;
import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException;
import com.wgblackmon.aihealthcare.web.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} verifying each exception
 * maps to the correct HTTP status code and error response body.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void runNotFound_returns404() {
        RunNotFoundException ex = new RunNotFoundException("run-42");

        ResponseEntity<ErrorResponse> response = handler.handleRunNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).contains("run-42");
    }

    @Test
    void noArticlesFound_returns422() {
        NoArticlesFoundException ex = new NoArticlesFoundException("run-99");

        ResponseEntity<ErrorResponse> response = handler.handleNoArticles(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NO_ARTICLES");
        assertThat(response.getBody().message()).contains("run-99");
    }

    @Test
    void duplicateSubscriber_returns409() {
        DuplicateSubscriberException ex = new DuplicateSubscriberException("test@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateSubscriber(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void subscriberNotFound_returns404() {
        SubscriberNotFoundException ex = new SubscriberNotFoundException("unknown@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleSubscriberNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void variantNotFound_returns404() {
        PromptVariantNotFoundException ex = new PromptVariantNotFoundException("v-123");

        ResponseEntity<ErrorResponse> response = handler.handleVariantNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void evaluationNotFound_returns404() {
        EvaluationNotFoundException ex = new EvaluationNotFoundException("eval-1");

        ResponseEntity<ErrorResponse> response = handler.handleEvaluationNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void apiKeyNotFound_returns404() {
        ApiKeyNotFoundException ex = new ApiKeyNotFoundException("key-abc");

        ResponseEntity<ErrorResponse> response = handler.handleApiKeyNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void apiKeyCreation_returns403() {
        ApiKeyCreationException ex = new ApiKeyCreationException("Tier does not allow API keys");

        ResponseEntity<ErrorResponse> response = handler.handleApiKeyCreation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Tier does not allow API keys");
    }

    @Test
    void duplicateUser_returns409() {
        DuplicateUserException ex = new DuplicateUserException("dup@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateUser(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void illegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("title must not be blank");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("title must not be blank");
    }
}
