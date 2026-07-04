package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ApiKeyController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Import(SecurityConfig.class)
@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @DisplayName("POST /api/v1/keys creates key and returns 201 with raw key")
    @WithMockUser(username = "user@test.com")
    void createKey_returns201WithRawKey() throws Exception {
        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Test Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Test Key"))
                .andExpect(jsonPath("$.rawKey").value(startsWith("aih_")))
                .andExpect(jsonPath("$.keyPrefix").value(startsWith("aih_")))
                .andExpect(jsonPath("$.active").value(true));

        verify(apiKeyPort).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("POST /api/v1/keys with blank name returns 400")
    @WithMockUser(username = "user@test.com")
    void createKey_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/keys returns user's keys without raw key")
    @WithMockUser(username = "user@test.com")
    void listKeys_returnsUserKeys() throws Exception {
        ApiKey key = new ApiKey("k1", "user@test.com", "Key One", "aih_1234",
                               "hash123", true, Instant.parse("2026-07-03T10:00:00Z"));
        when(apiKeyPort.findAllByOwnerEmail("user@test.com")).thenReturn(List.of(key));

        mockMvc.perform(get("/api/v1/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Key One"))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} returns 204")
    @WithMockUser(username = "user@test.com")
    void deleteKey_returns204() throws Exception {
        when(apiKeyPort.existsById("k1")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/keys/k1"))
                .andExpect(status().isNoContent());

        verify(apiKeyPort).deleteById("k1");
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} with unknown id returns 404")
    @WithMockUser(username = "user@test.com")
    void deleteKey_unknownId_returns404() throws Exception {
        when(apiKeyPort.existsById("unknown")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/keys/unknown"))
                .andExpect(status().isNotFound());
    }
}
