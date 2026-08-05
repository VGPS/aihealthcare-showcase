package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DataExportRequest;
import com.wgblackmon.aihealthcare.domain.model.DataExportResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.ExportDataUseCase;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link DataExportController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@WebMvcTest(DataExportController.class)
@Import(SecurityConfig.class)
class DataExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportDataUseCase exportDataUseCase;

    @MockBean
    private com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort apiKeyPort;

    @Test
    void exportCsv_returnsDownloadableFile() throws Exception {
        DataExportResult result = new DataExportResult("id,title\na1,Test", "articles.csv", "text/csv", 1);
        when(exportDataUseCase.export(any(DataExportRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/export")
                        .param("type", "articles")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"articles.csv\""))
                .andExpect(content().string("id,title\na1,Test"));
    }

    @Test
    void exportJson_returnsJsonContent() throws Exception {
        DataExportResult result = new DataExportResult("[{\"id\":\"a1\"}]", "deals.json", "application/json", 1);
        when(exportDataUseCase.export(any(DataExportRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/export")
                        .param("type", "deals")
                        .param("format", "JSON"))
                .andExpect(status().isOk())
                .andExpect(content().string("[{\"id\":\"a1\"}]"));
    }

    @Test
    void invalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/export")
                        .param("type", "articles")
                        .param("format", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_isPermitted() throws Exception {
        DataExportResult result = new DataExportResult("data", "file.csv", "text/csv", 0);
        when(exportDataUseCase.export(any(DataExportRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/export")
                        .param("type", "articles")
                        .param("format", "CSV"))
                .andExpect(status().isOk());
    }

    @Test
    void exportWithBrandName_passesToService() throws Exception {
        DataExportResult result = new DataExportResult("<html>branded</html>", "report.html", "text/html", 5);
        when(exportDataUseCase.export(any(DataExportRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/export")
                        .param("type", "relationships")
                        .param("format", "PDF")
                        .param("brandName", "Enterprise Co"))
                .andExpect(status().isOk())
                .andExpect(content().string("<html>branded</html>"));
    }
}
