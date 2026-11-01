package com.wgblackmon.aihealthcare.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Handlers for the six AIHealthcare MCP server tools.
 *
 * Each method takes a {@link McpSchema.CallToolRequest} and returns a
 * {@link McpSchema.CallToolResult} whose text content is a JSON string.
 * Exceptions are caught and returned as error results so a single bad
 * parameter never takes down the MCP session.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-01
 * @updated 2026-11-01
 */
@Slf4j
@Component
public class AiHealthcareMcpToolHandlers {

    private final ManageStateLawsUseCase stateLawsUseCase;
    private final BrowseCompaniesUseCase companiesUseCase;
    private final WikiQueryPort wikiQueryPort;
    private final ProduceMarketDigestUseCase marketDigestUseCase;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final DetectTrendsUseCase trendsUseCase;
    private final ObjectMapper objectMapper;

    public AiHealthcareMcpToolHandlers(
            ManageStateLawsUseCase stateLawsUseCase,
            BrowseCompaniesUseCase companiesUseCase,
            WikiQueryPort wikiQueryPort,
            ProduceMarketDigestUseCase marketDigestUseCase,
            MonitorRegulatoryEventsUseCase regulatoryUseCase,
            DetectTrendsUseCase trendsUseCase,
            ObjectMapper objectMapper) {
        this.stateLawsUseCase  = stateLawsUseCase;
        this.companiesUseCase  = companiesUseCase;
        this.wikiQueryPort     = wikiQueryPort;
        this.marketDigestUseCase = marketDigestUseCase;
        this.regulatoryUseCase = regulatoryUseCase;
        this.trendsUseCase     = trendsUseCase;
        this.objectMapper      = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Tool: search_legislation
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult searchLegislation(McpSchema.CallToolRequest req) {
        log.debug("searchLegislation() | args={}", req.arguments());
        try {
            Map<String, Object> args = req.arguments();
            String query      = str(args, "query");
            String stateCode  = str(args, "stateCode");
            String category   = str(args, "category");

            var laws = (query != null)    ? stateLawsUseCase.search(query)
                     : (stateCode != null) ? stateLawsUseCase.getByState(StateCode.valueOf(stateCode.toUpperCase()))
                     : (category != null)  ? stateLawsUseCase.getByCategory(LawCategory.valueOf(category.toUpperCase()))
                     : stateLawsUseCase.getAll();

            ArrayNode arr = objectMapper.createArrayNode();
            for (var l : laws) {
                ObjectNode n = arr.addObject();
                n.put("id",              l.id());
                n.put("stateCode",       String.valueOf(l.stateCode()));
                n.put("stateName",       l.stateName());
                n.put("billNumber",      l.billNumber());
                n.put("title",           l.title());
                n.put("yearEnacted",     l.yearEnacted());
                n.put("status",          String.valueOf(l.status()));
                n.put("keyRequirements", nvl(l.keyRequirements()));
                if (l.categories() != null) {
                    var cats = n.putArray("categories");
                    l.categories().forEach(c -> cats.add(String.valueOf(c)));
                }
            }
            String result = objectMapper.writeValueAsString(arr);
            log.debug("searchLegislation() | return={} laws", laws.size());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("searchLegislation() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool: search_companies
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult searchCompanies(McpSchema.CallToolRequest req) {
        log.debug("searchCompanies() | args={}", req.arguments());
        try {
            String nameQuery = str(req.arguments(), "nameQuery");
            var companies = companiesUseCase.listCompanies();
            if (nameQuery != null) {
                String lc = nameQuery.toLowerCase();
                companies = companies.stream()
                        .filter(c -> c.name().toLowerCase().contains(lc))
                        .toList();
            }

            ArrayNode arr = objectMapper.createArrayNode();
            for (var c : companies.stream().limit(50).toList()) {
                ObjectNode n = arr.addObject();
                n.put("name",        c.name());
                n.put("domain",      nvl(c.domain()));
                n.put("sector",      nvl(c.sector()));
                n.put("subSector",   nvl(c.subSector()));
                n.put("category",    nvl(c.category()));
                n.put("description", nvl(c.description()));
                n.put("fundingStage",nvl(c.fundingStage()));
            }
            String result = objectMapper.writeValueAsString(arr);
            log.debug("searchCompanies() | return={} companies", companies.size());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("searchCompanies() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool: search_wiki
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult searchWiki(McpSchema.CallToolRequest req) {
        log.debug("searchWiki() | args={}", req.arguments());
        try {
            String query     = nvl(str(req.arguments(), "query"));
            int    maxResults = num(req.arguments(), "maxResults", 5);

            var pages = wikiQueryPort.findRelevantPages(query, maxResults);

            ArrayNode arr = objectMapper.createArrayNode();
            for (var p : pages) {
                ObjectNode n = arr.addObject();
                n.put("slug",     p.slug());
                n.put("title",    p.title());
                n.put("type",     String.valueOf(p.pageType()));
                String content = p.contentMarkdown();
                n.put("excerpt",  content != null
                        ? content.substring(0, Math.min(500, content.length()))
                        : "");
                if (p.tags() != null) {
                    var tags = n.putArray("tags");
                    p.tags().forEach(tags::add);
                }
            }
            String result = objectMapper.writeValueAsString(arr);
            log.debug("searchWiki() | return={} pages", pages.size());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("searchWiki() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool: get_market_digest
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult getMarketDigest(McpSchema.CallToolRequest req) {
        log.debug("getMarketDigest() | args={}", req.arguments());
        try {
            String dateStr = str(req.arguments(), "date");
            var digest = (dateStr != null)
                    ? marketDigestUseCase.findByDate(LocalDate.parse(dateStr))
                    : marketDigestUseCase.findLatest();

            String result = digest.isPresent()
                    ? objectMapper.writeValueAsString(digest.get())
                    : "{\"message\":\"No market digest available\"}";
            log.debug("getMarketDigest() | return present={}", digest.isPresent());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("getMarketDigest() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool: get_regulatory_events
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult getRegulatoryEvents(McpSchema.CallToolRequest req) {
        log.debug("getRegulatoryEvents() | args={}", req.arguments());
        try {
            String type  = str(req.arguments(), "eventType");
            String body  = str(req.arguments(), "regulatoryBody");
            int    limit = num(req.arguments(), "limit", 20);

            var events = (type != null)
                    ? regulatoryUseCase.getEventsByType(RegulatoryEventType.valueOf(type.toUpperCase()), limit)
                    : (body != null)
                    ? regulatoryUseCase.getEventsByBody(RegulatoryBody.valueOf(body.toUpperCase()), limit)
                    : regulatoryUseCase.getRecentEvents(limit);

            ArrayNode arr = objectMapper.createArrayNode();
            for (var e : events) {
                ObjectNode n = arr.addObject();
                n.put("eventId",         e.eventId());
                n.put("eventType",       String.valueOf(e.eventType()));
                n.put("regulatoryBody",  String.valueOf(e.regulatoryBody()));
                n.put("title",           e.title());
                n.put("referenceNumber", nvl(e.referenceNumber()));
                n.put("applicantName",   nvl(e.applicantName()));
                n.put("deviceName",      nvl(e.deviceName()));
                n.put("discoveredAt",    e.discoveredAt().toString());
            }
            String result = objectMapper.writeValueAsString(arr);
            log.debug("getRegulatoryEvents() | return={} events", events.size());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("getRegulatoryEvents() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool: get_ai_trends
    // -------------------------------------------------------------------------

    public McpSchema.CallToolResult getTrends(McpSchema.CallToolRequest req) {
        log.debug("getTrends() | (no args)");
        try {
            var snapshot = trendsUseCase.getLatestSnapshot();
            if (snapshot.isEmpty()) {
                String result = "{\"message\":\"No trend snapshot available\"}";
                log.debug("getTrends() | return=empty");
                return McpSchema.CallToolResult.builder().addTextContent(result).build();
            }
            var s = snapshot.get();
            ObjectNode root = objectMapper.createObjectNode();
            root.put("generatedAt",    s.generatedAt().toString());
            root.put("totalKeywords",  s.totalKeywords());

            var rising = root.putArray("risingTopics");
            for (var sig : s.risingTopics()) {
                ObjectNode n = rising.addObject();
                n.put("keyword",   sig.keyword());
                n.put("momentum",  sig.momentum());
                n.put("current30d", sig.current30d());
            }
            var fading = root.putArray("fadingTopics");
            for (var sig : s.fadingTopics()) {
                ObjectNode n = fading.addObject();
                n.put("keyword",   sig.keyword());
                n.put("momentum",  sig.momentum());
            }
            var newTopics = root.putArray("newTopics");
            for (var sig : s.newTopics()) {
                ObjectNode n = newTopics.addObject();
                n.put("keyword",   sig.keyword());
                n.put("current30d", sig.current30d());
            }

            String result = objectMapper.writeValueAsString(root);
            log.debug("getTrends() | return={} rising, {} fading, {} new",
                    s.risingTopics().size(), s.fadingTopics().size(), s.newTopics().size());
            return McpSchema.CallToolResult.builder().addTextContent(result).build();
        } catch (Exception e) {
            log.error("getTrends() | error: {}", e.getMessage());
            return errorResult(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        String s = v.toString().strip();
        return s.isEmpty() ? null : s;
    }

    private static int num(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v == null) return defaultValue;
        return ((Number) v).intValue();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    static McpSchema.CallToolResult errorResult(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + msg)
                .isError(true)
                .build();
    }
}
