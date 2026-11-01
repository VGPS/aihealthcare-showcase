package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.web.mcp.AiHealthcareMcpToolHandlers;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Wires the AIHealthcare MCP server and its HTTP/SSE transport.
 *
 * <p>Six read-only intelligence tools are exposed via MCP:
 * <ul>
 *   <li>{@code search_legislation}    — state + federal health AI laws</li>
 *   <li>{@code search_companies}      — AI healthcare company directory</li>
 *   <li>{@code search_wiki}           — knowledge-base wiki pages</li>
 *   <li>{@code get_market_digest}     — daily AI healthcare market digest</li>
 *   <li>{@code get_regulatory_events} — FDA / CMS regulatory events</li>
 *   <li>{@code get_ai_trends}         — keyword trend analysis</li>
 * </ul>
 *
 * <p>The SSE endpoint is {@code GET /mcp/sse}; messages arrive at
 * {@code POST /mcp/messages}.  Both are permitted without authentication
 * by SecurityConfig.  The MCP servlet runs outside the DispatcherServlet —
 * it is registered via {@link ServletRegistrationBean} at {@code /mcp/*}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-01
 * @updated 2026-11-01
 */
@Slf4j
@Configuration
public class McpServerConfig {

    private McpSyncServer mcpSyncServer;

    @Bean
    public HttpServletSseServerTransportProvider mcpTransportProvider() {
        log.debug("mcpTransportProvider() | creating HttpServletSseServerTransportProvider");
        var result = HttpServletSseServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .sseEndpoint("/mcp/sse")
                .messageEndpoint("/mcp/messages")
                .build();
        log.debug("mcpTransportProvider() | return={}", result);
        return result;
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServletRegistration(
            HttpServletSseServerTransportProvider transport) {
        log.debug("mcpServletRegistration() | registering MCP servlet at /mcp/*");
        var reg = new ServletRegistrationBean<>(transport, "/mcp/*");
        reg.setAsyncSupported(true);
        reg.setName("mcpSseServlet");
        log.debug("mcpServletRegistration() | return={}", reg);
        return reg;
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletSseServerTransportProvider transport,
                                   AiHealthcareMcpToolHandlers handlers) {
        log.debug("mcpServer() | creating MCP server with 6 intelligence tools");
        mcpSyncServer = McpServer.sync(transport)
                .serverInfo("aihealthcare-intelligence", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .toolCall(searchLegislationTool(),   (e, r) -> handlers.searchLegislation(r))
                .toolCall(searchCompaniesTool(),     (e, r) -> handlers.searchCompanies(r))
                .toolCall(searchWikiTool(),          (e, r) -> handlers.searchWiki(r))
                .toolCall(getMarketDigestTool(),     (e, r) -> handlers.getMarketDigest(r))
                .toolCall(getRegulatoryEventsTool(), (e, r) -> handlers.getRegulatoryEvents(r))
                .toolCall(getAiTrendsTool(),         (e, r) -> handlers.getTrends(r))
                .build();
        log.debug("mcpServer() | return={}", mcpSyncServer);
        return mcpSyncServer;
    }

    @PreDestroy
    public void shutdownMcpServer() {
        log.debug("shutdownMcpServer() | closing MCP server gracefully");
        if (mcpSyncServer != null) {
            mcpSyncServer.closeGracefully();
        }
        log.debug("shutdownMcpServer() | return=void");
    }

    // -------------------------------------------------------------------------
    // Tool definitions (JSON Schema)
    // -------------------------------------------------------------------------

    private static McpSchema.Tool searchLegislationTool() {
        return McpSchema.Tool.builder("search_legislation", Map.of(
                "type", "object",
                "properties", Map.of(
                        "query",     Map.of("type", "string",
                                "description", "Full-text search across title, requirements, and enforcement text"),
                        "stateCode", Map.of("type", "string",
                                "description", "Two-letter US state code, e.g. CA, TX, NY"),
                        "category",  Map.of("type", "string",
                                "description", "PAYER_UTILIZATION_REVIEW, CONSUMER_CHATBOTS, " +
                                        "MENTAL_HEALTH_PSYCHOTHERAPY, PROVIDER_CLINICAL_USE, " +
                                        "PROVIDER_DISCLOSURE_CONSENT, COMPREHENSIVE_AI_ACT, CLAIMS_DOWNCODING")
                ),
                "required", List.of()
        )).description(
                "Search enacted U.S. state and federal laws governing AI in healthcare. " +
                "Returns law title, state, categories, key requirements, and effective date. " +
                "Supply query OR stateCode OR category to filter; omit all to return all enacted laws."
        ).build();
    }

    private static McpSchema.Tool searchCompaniesTool() {
        return McpSchema.Tool.builder("search_companies", Map.of(
                "type", "object",
                "properties", Map.of(
                        "nameQuery", Map.of("type", "string",
                                "description", "Company name substring filter (case-insensitive). " +
                                        "Omit to return all companies (capped at 50).")
                ),
                "required", List.of()
        )).description(
                "Search the AIHealthcare company directory for AI healthcare companies. " +
                "Returns name, domain, sector, category, description, and funding stage."
        ).build();
    }

    private static McpSchema.Tool searchWikiTool() {
        return McpSchema.Tool.builder("search_wiki", Map.of(
                "type", "object",
                "properties", Map.of(
                        "query",      Map.of("type", "string",
                                "description", "Search query — uses hybrid keyword + semantic search"),
                        "maxResults", Map.of("type", "integer",
                                "description", "Maximum pages to return (default 5)")
                ),
                "required", List.of("query")
        )).description(
                "Search the AIHealthcare LLM-compiled knowledge base. " +
                "Returns wiki page slug, title, type, tags, and a 500-char excerpt."
        ).build();
    }

    private static McpSchema.Tool getMarketDigestTool() {
        return McpSchema.Tool.builder("get_market_digest", Map.of(
                "type", "object",
                "properties", Map.of(
                        "date", Map.of("type", "string",
                                "description", "ISO date YYYY-MM-DD. Omit to get the most recent digest.")
                ),
                "required", List.of()
        )).description(
                "Retrieve the AI healthcare daily market digest — ranked news entries with " +
                "fact/speculation classification, impact assessments, and affected companies."
        ).build();
    }

    private static McpSchema.Tool getRegulatoryEventsTool() {
        return McpSchema.Tool.builder("get_regulatory_events", Map.of(
                "type", "object",
                "properties", Map.of(
                        "eventType",      Map.of("type", "string",
                                "description", "FDA_510K_CLEARANCE, DE_NOVO, PMA, CMS_FINAL_RULE, etc."),
                        "regulatoryBody", Map.of("type", "string",
                                "description", "FDA, CMS, ONC, OTHER"),
                        "limit",          Map.of("type", "integer",
                                "description", "Maximum events to return (default 20)")
                ),
                "required", List.of()
        )).description(
                "Retrieve FDA and CMS regulatory events relevant to AI in healthcare. " +
                "Returns event type, title, applicant, device name, reference number, and discovery date."
        ).build();
    }

    private static McpSchema.Tool getAiTrendsTool() {
        return McpSchema.Tool.builder("get_ai_trends", Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        )).description(
                "Retrieve the latest AI healthcare keyword trend analysis. " +
                "Returns rising, fading, and new keywords with momentum scores from a 30/90/180-day window."
        ).build();
    }
}
