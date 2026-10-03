package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageOutreachUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link OutreachController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@Import(SecurityConfig.class)
@WithMockUser(username = "admin@test.com", roles = "ADMIN")
@WebMvcTest(OutreachController.class)
class OutreachControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ManageOutreachUseCase outreachUseCase;

    @MockitoBean
    private HealthcareAiCompanyPort companyPort;

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private static CompanyOutreach outreachRow() {
        return new CompanyOutreach(1L, "microsoft", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, "notes", NOW, NOW);
    }

    private static CompanyContact contact() {
        return new CompanyContact(1L, "microsoft", "Jane Doe", "CTO",
                "jane@ms.com", null, ContactSource.LINKEDIN, ContactStatus.IDENTIFIED, null, NOW, NOW);
    }

    private static HealthcareAiCompany company() {
        return new HealthcareAiCompany(
                "microsoft", "Microsoft Health", "microsoft-health",
                "microsoft.com", "Microsoft healthcare AI", null, null,
                null, null, null, null, null, null,
                List.of(), false, List.of(), NOW, NOW);
    }

    @Test
    void listOutreach_returns200WithModel() throws Exception {
        when(outreachUseCase.listAllOutreach()).thenReturn(List.of(outreachRow()));
        when(companyPort.findAllByOrderByName()).thenReturn(List.of(company()));

        mockMvc.perform(get("/admin/outreach"))
                .andExpect(status().isOk())
                .andExpect(view().name("outreach"))
                .andExpect(model().attributeExists("allOutreach"))
                .andExpect(model().attributeExists("companies"))
                .andExpect(model().attributeExists("companyNameToId"));
    }

    @Test
    void contactsJson_returnsEmptyListForUnknownSlug() throws Exception {
        when(outreachUseCase.listContacts("unknown-co")).thenReturn(List.of());

        mockMvc.perform(get("/admin/outreach/contacts-json").param("slug", "unknown-co"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string("[]"));
    }

    @Test
    void contactsJson_returnsContactsForKnownSlug() throws Exception {
        when(outreachUseCase.listContacts("microsoft")).thenReturn(List.of(contact()));

        mockMvc.perform(get("/admin/outreach/contacts-json").param("slug", "microsoft"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString("Jane Doe")));
    }

    @Test
    void addOutreach_redirectsToList() throws Exception {
        when(outreachUseCase.addOutreach(anyString(), any(), anyString())).thenReturn(outreachRow());

        mockMvc.perform(post("/admin/outreach/add").with(csrf())
                        .param("slug", "microsoft")
                        .param("purpose", "EMPLOYMENT")
                        .param("notes", "test notes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outreach"));
    }

    @Test
    void companyDetail_returns200WithSlugAndContacts() throws Exception {
        when(outreachUseCase.listOutreachBySlug("microsoft")).thenReturn(List.of(outreachRow()));
        when(outreachUseCase.listContacts("microsoft")).thenReturn(List.of(contact()));

        mockMvc.perform(get("/admin/outreach/microsoft"))
                .andExpect(status().isOk())
                .andExpect(view().name("outreach-company"))
                .andExpect(model().attribute("slug", "microsoft"))
                .andExpect(model().attributeExists("contacts"));
    }

    @Test
    void updateStatus_redirectsToCompanyPage() throws Exception {
        when(outreachUseCase.updateOutreachStatus(eq(1L), any())).thenReturn(outreachRow());

        mockMvc.perform(post("/admin/outreach/1/status").with(csrf())
                        .param("status", "IN_PROGRESS")
                        .param("slug", "microsoft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outreach/microsoft"));

        verify(outreachUseCase).updateOutreachStatus(1L, OutreachStatus.IN_PROGRESS);
    }

    @Test
    void deleteOutreach_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/outreach/1/delete").with(csrf())
                        .param("slug", "microsoft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outreach"));

        verify(outreachUseCase).deleteOutreach(1L);
    }

    @Test
    void addContact_redirectsToCompanyPage() throws Exception {
        when(outreachUseCase.addContact(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(contact());

        mockMvc.perform(post("/admin/outreach/microsoft/contacts/add").with(csrf())
                        .param("fullName", "Jane Doe")
                        .param("source", "LINKEDIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outreach/microsoft"));
    }

    @Test
    void deleteContact_redirectsToCompanyPage() throws Exception {
        mockMvc.perform(post("/admin/outreach/contacts/5/delete").with(csrf())
                        .param("slug", "microsoft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outreach/microsoft"));

        verify(outreachUseCase).deleteContact(5L);
    }
}
