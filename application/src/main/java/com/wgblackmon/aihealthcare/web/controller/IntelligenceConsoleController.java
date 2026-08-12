package com.wgblackmon.aihealthcare.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

/**
 * Admin test console for the Claude Healthcare Intelligence Service.
 *
 * <p>Provides a Thymeleaf UI at {@code /admin/intelligence} with all 13 tabs
 * covering the full Claude Intelligence Service endpoint surface. Generic REST
 * proxy at {@code /admin/intelligence/api/**} forwards AJAX requests to the
 * Claude service, enabling the full console behind Spring Security admin auth.
 *
 * <p>Admin-only — protected by SecurityConfig's {@code /admin/**} rule.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-08-08
 * @updated 2026-08-11
 */
@Slf4j
@Controller
@RequestMapping("/admin/intelligence")
public class IntelligenceConsoleController {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public IntelligenceConsoleController(
            @Value("${claude.intelligence.base-url:http://localhost:8081}") String baseUrl,
            @Value("${intelligence.api-key:}") String apiKey) {
        log.debug("IntelligenceConsoleController() | baseUrl={}", baseUrl);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        this.restClient = builder.build();
    }

    @GetMapping
    public String console(Model model) {
        log.debug("console() | rendering intelligence console");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("activeTab", "chat");
        log.debug("console() | return=intelligence-console");
        return "intelligence-console";
    }

    @PostMapping("/coding")
    public String runCoding(
            @RequestParam String clinicalDescription,
            @RequestParam(required = false) String context,
            Model model) {
        log.debug("runCoding() | clinicalDescription={}, context={}", clinicalDescription, context);

        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("activeTab", "coding");
        model.addAttribute("codingInput", clinicalDescription);
        model.addAttribute("codingContext", context);

        try {
            String requestBody = buildCodingJson(clinicalDescription, context);

            String result = restClient.post()
                    .uri("/api/v1/intelligence/coding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            model.addAttribute("codingResult", result);
            log.info("runCoding() | success, result length={}", result != null ? result.length() : 0);
        } catch (Exception e) {
            log.error("runCoding() | failed: {}", e.getMessage());
            model.addAttribute("codingError", "Claude Intelligence Service error: " + e.getMessage());
        }

        log.debug("runCoding() | return=intelligence-console");
        return "intelligence-console";
    }

    @PostMapping("/coverage")
    public String runCoverage(
            @RequestParam String procedureDescription,
            @RequestParam(required = false) String patientContext,
            @RequestParam(required = false, defaultValue = "MEDICARE_B") String payerType,
            Model model) {
        log.debug("runCoverage() | procedure={}, payerType={}", procedureDescription, payerType);

        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("activeTab", "coverage");
        model.addAttribute("coverageInput", procedureDescription);
        model.addAttribute("coverageContext", patientContext);
        model.addAttribute("coveragePayerType", payerType);

        try {
            String requestBody = buildCoverageJson(procedureDescription, patientContext, payerType);

            String result = restClient.post()
                    .uri("/api/v1/intelligence/coverage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            model.addAttribute("coverageResult", result);
            log.info("runCoverage() | success, result length={}", result != null ? result.length() : 0);
        } catch (Exception e) {
            log.error("runCoverage() | failed: {}", e.getMessage());
            model.addAttribute("coverageError", "Claude Intelligence Service error: " + e.getMessage());
        }

        log.debug("runCoverage() | return=intelligence-console");
        return "intelligence-console";
    }

    @PostMapping("/api/**")
    @ResponseBody
    public ResponseEntity<String> proxyPost(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        String forwardPath = request.getRequestURI().substring("/admin/intelligence".length());
        log.debug("proxyPost() | path={}", forwardPath);
        try {
            String result = restClient.post()
                    .uri(forwardPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body != null ? body : "")
                    .retrieve()
                    .body(String.class);
            log.debug("proxyPost() | return=200, length={}", result != null ? result.length() : 0);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception e) {
            log.error("proxyPost() | path={}, error={}", forwardPath, e.getMessage());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Claude Intelligence Service error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @GetMapping("/api/**")
    @ResponseBody
    public ResponseEntity<String> proxyGet(HttpServletRequest request) {
        String forwardPath = request.getRequestURI().substring("/admin/intelligence".length());
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? forwardPath + "?" + queryString : forwardPath;
        log.debug("proxyGet() | path={}", fullPath);
        boolean isHtml = forwardPath.contains("/history/files/") && !forwardPath.endsWith("/files");
        try {
            String result = restClient.get()
                    .uri(fullPath)
                    .retrieve()
                    .body(String.class);
            log.debug("proxyGet() | return=200, length={}", result != null ? result.length() : 0);
            MediaType contentType = isHtml ? MediaType.TEXT_HTML : MediaType.APPLICATION_JSON;
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(result);
        } catch (Exception e) {
            log.error("proxyGet() | path={}, error={}", fullPath, e.getMessage());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Claude Intelligence Service error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String buildCodingJson(String clinicalDescription, String context) {
        log.debug("buildCodingJson() | descLength={}", clinicalDescription.length());
        StringBuilder sb = new StringBuilder();
        sb.append("{\"clinicalDescription\":\"").append(escapeJson(clinicalDescription)).append("\"");
        if (context != null && !context.isBlank()) {
            sb.append(",\"context\":\"").append(escapeJson(context)).append("\"");
        }
        sb.append("}");
        String result = sb.toString();
        log.debug("buildCodingJson() | return={} chars", result.length());
        return result;
    }

    private String buildCoverageJson(String procedureDescription, String patientContext, String payerType) {
        log.debug("buildCoverageJson() | procedure={}", procedureDescription);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"procedureDescription\":\"").append(escapeJson(procedureDescription)).append("\"");
        if (patientContext != null && !patientContext.isBlank()) {
            sb.append(",\"patientContext\":\"").append(escapeJson(patientContext)).append("\"");
        }
        sb.append(",\"payerType\":\"").append(escapeJson(payerType)).append("\"");
        sb.append("}");
        String result = sb.toString();
        log.debug("buildCoverageJson() | return={} chars", result.length());
        return result;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
