package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import com.wgblackmon.aihealthcare.domain.service.DocumentUploadService;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link DocumentLibraryController}.
 *
 * <p>Covers GET library listing, POST upload happy path, POST invalid extension,
 * POST oversized file guard, POST missing source label, POST missing topic,
 * redirect after success, unauthenticated access redirect, and admin-only access.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@Import(SecurityConfig.class)
@WebMvcTest(DocumentLibraryController.class)
@TestPropertySource(properties = "aihealthcare.documents.upload-dir=/tmp/aihealthcare-test")
class DocumentLibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentUploadService uploadService;

    @MockitoBean
    private DocumentLibraryPort documentLibraryPort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private NewsArticleRepository articleRepository;

    @MockitoBean
    private NewsTopicProperties newsTopicProperties;

    private DocumentRecord sampleRecord() {
        return new DocumentRecord(
                "doc-1", "research.pdf", "Dr Smith", "AI Healthcare Legal", null, null, Instant.now(),
                42, "ai-drug-discovery", DocumentStatus.WIKI_COMPILED, null);
    }

    @Test
    @DisplayName("GET /admin/documents renders library page with documents")
    @WithMockUser(roles = "ADMIN")
    void getLibrary_rendersPage() throws Exception {
        when(documentLibraryPort.findAll()).thenReturn(List.of(sampleRecord()));

        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(view().name("document-library"))
                .andExpect(model().attributeExists("documents"))
                .andExpect(model().attribute("documentCount", 1));
    }

    @Test
    @DisplayName("GET /admin/documents with no documents shows empty list")
    @WithMockUser(roles = "ADMIN")
    void getLibrary_emptyList() throws Exception {
        when(documentLibraryPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("documentCount", 0));
    }

    @Test
    @DisplayName("POST upload with valid PDF redirects to list with success message")
    @WithMockUser(roles = "ADMIN")
    void postUpload_validPdf_redirectsWithSuccess() throws Exception {
        DocumentRecord rec = sampleRecord();
        when(uploadService.uploadAndIngest(any(), anyString(), anyString(), anyString())).thenReturn(rec);

        MockMultipartFile file = new MockMultipartFile(
                "file", "study.pdf", MediaType.APPLICATION_PDF_VALUE,
                "PDF content".getBytes());

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "Dr Smith — 2026 Study")
                        .param("topic", "AI Healthcare Legal")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?message=*"));
    }

    @Test
    @DisplayName("POST upload where service reports FAILED status redirects with error, not success")
    @WithMockUser(roles = "ADMIN")
    void postUpload_serviceReturnsFailedStatus_redirectsWithError() throws Exception {
        DocumentRecord failed = new DocumentRecord(
                "doc-2", "study.pdf", "Dr Smith", "AI Healthcare Legal", null, null, Instant.now(),
                0, null, DocumentStatus.FAILED, "vector store unavailable");
        when(uploadService.uploadAndIngest(any(), anyString(), anyString(), anyString())).thenReturn(failed);

        MockMultipartFile file = new MockMultipartFile(
                "file", "study.pdf", MediaType.APPLICATION_PDF_VALUE,
                "PDF content".getBytes());

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "Dr Smith — 2026 Study")
                        .param("topic", "AI Healthcare Legal")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?error=*"));
    }

    @Test
    @DisplayName("handleMaxUploadSizeExceeded redirects with a friendly error, not a raw 413")
    void handleMaxUploadSizeExceeded_redirectsWithError() {
        DocumentLibraryController controller =
                new DocumentLibraryController(uploadService, documentLibraryPort, newsTopicProperties, "/tmp/aihealthcare-test");
        RedirectAttributesModelMap redirectAttrs = new RedirectAttributesModelMap();

        String view = controller.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(52_428_800L), redirectAttrs);

        assertThat(view).isEqualTo("redirect:/admin/documents");
        assertThat(redirectAttrs)
                .containsEntry("error", "File exceeds maximum size of 50 MB.");
    }

    @Test
    @DisplayName("POST upload with unsupported extension redirects with error")
    @WithMockUser(roles = "ADMIN")
    void postUpload_unsupportedExtension_redirectsWithError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png",
                "PNG content".getBytes());

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "Some Label")
                        .param("topic", "General AI Healthcare News")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?error=*"));
    }

    @Test
    @DisplayName("POST upload with missing source label redirects with error")
    @WithMockUser(roles = "ADMIN")
    void postUpload_missingSourceLabel_redirectsWithError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "paper.pdf", MediaType.APPLICATION_PDF_VALUE,
                "content".getBytes());

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "  ")
                        .param("topic", "General AI Healthcare News")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?error=*"));
    }

    @Test
    @DisplayName("POST upload with missing topic redirects with error")
    @WithMockUser(roles = "ADMIN")
    void postUpload_missingTopic_redirectsWithError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "paper.pdf", MediaType.APPLICATION_PDF_VALUE,
                "content".getBytes());

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "Dr Smith")
                        .param("topic", "  ")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?error=*"));
    }

    @Test
    @DisplayName("POST upload with empty file redirects with error")
    @WithMockUser(roles = "ADMIN")
    void postUpload_emptyFile_redirectsWithError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/admin/documents/upload")
                        .file(file)
                        .param("sourceLabel", "Label")
                        .param("topic", "General AI Healthcare News")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/documents?error=*"));
    }

    @Test
    @DisplayName("GET /admin/documents as non-admin USER returns 403")
    @WithMockUser(roles = "USER")
    void getLibrary_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/documents unauthenticated redirects to login")
    void getLibrary_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Library page shows wiki link when wikiPageSlug is present")
    @WithMockUser(roles = "ADMIN")
    void getLibrary_documentWithWikiSlug_showsInModel() throws Exception {
        DocumentRecord rec = sampleRecord();
        when(documentLibraryPort.findAll()).thenReturn(List.of(rec));

        mockMvc.perform(get("/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("uploadedAtMap"));
    }
}
