package com.wgblackmon.aihealthcare.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException;
import com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSubscribersUseCase;
import com.wgblackmon.aihealthcare.web.dto.SubscriberRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link SubscriberController}.
 *
 * <p>{@code @WebMvcTest} loads only the MVC layer (controller + exception handler)
 * and mocks all dependencies via {@code @MockitoBean}.  No Spring context, no
 * database, no real service calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@WebMvcTest(controllers = {SubscriberController.class, GlobalExceptionHandler.class})
class SubscriberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ManageSubscribersUseCase manageSubscribersUseCase;

    private static final String EMAIL = "jane.doe@example.com";
    private static final String NAME  = "Jane Doe";

    private static final Subscriber SUBSCRIBER = new Subscriber(
            EMAIL, NAME, true, Instant.parse("2026-04-13T10:00:00Z"), null);

    // -------------------------------------------------------------------------
    // POST /api/v1/subscribers
    // -------------------------------------------------------------------------

    @Test
    void addSubscriber_returns201WithBody() throws Exception {
        when(manageSubscribersUseCase.addSubscriber(EMAIL, NAME)).thenReturn(SUBSCRIBER);

        mockMvc.perform(post("/api/v1/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubscriberRequest(EMAIL, NAME))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value(NAME))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void addSubscriber_duplicateEmail_returns409() throws Exception {
        when(manageSubscribersUseCase.addSubscriber(anyString(), anyString()))
                .thenThrow(new DuplicateSubscriberException(EMAIL));

        mockMvc.perform(post("/api/v1/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubscriberRequest(EMAIL, NAME))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void addSubscriber_blankEmail_returns400() throws Exception {
        when(manageSubscribersUseCase.addSubscriber(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("email must not be blank"));

        mockMvc.perform(post("/api/v1/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubscriberRequest("", NAME))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/subscribers
    // -------------------------------------------------------------------------

    @Test
    void listSubscribers_returns200WithList() throws Exception {
        when(manageSubscribersUseCase.listSubscribers()).thenReturn(List.of(SUBSCRIBER));

        mockMvc.perform(get("/api/v1/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(EMAIL))
                .andExpect(jsonPath("$[0].name").value(NAME))
                .andExpect(jsonPath("$[0].tier").value("FREE"));
    }

    @Test
    void listSubscribers_emptyList_returns200WithEmptyArray() throws Exception {
        when(manageSubscribersUseCase.listSubscribers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/subscribers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/subscribers/{email}
    // -------------------------------------------------------------------------

    @Test
    void removeSubscriber_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/subscribers/{email}", EMAIL))
                .andExpect(status().isNoContent());

        verify(manageSubscribersUseCase).removeSubscriber(EMAIL);
    }

    @Test
    void removeSubscriber_unknownEmail_returns404() throws Exception {
        doThrow(new SubscriberNotFoundException(EMAIL))
                .when(manageSubscribersUseCase).removeSubscriber(EMAIL);

        mockMvc.perform(delete("/api/v1/subscribers/{email}", EMAIL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
