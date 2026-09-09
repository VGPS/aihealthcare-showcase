package com.wgblackmon.aihealthcare.infrastructure.enterprise.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataJobLog;
import com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RemoteConnectionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Enterprise data source adapter for customer-owned HTTPS/JSON endpoints.
 *
 * <p>Resolves a {@link RemoteConnection} by connection ID from the request,
 * delegates the actual HTTP call to {@link RemoteEndpointGuard} (which handles
 * all SSRF defences and IP pinning), then flattens the JSON response into a
 * tabular {@link DataSet}.
 *
 * <p>The guard controls every aspect of the network call: TLS pinning,
 * redirect rejection, IP blocklist, size cap. This adapter only parses JSON.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class HttpJsonRemoteDataSourceAdapter implements EnterpriseDataSourcePort {

    static final String FEED_ID = "customer-remote";

    private final RemoteConnectionPort connectionPort;
    private final RemoteEndpointGuard guard;
    private final ObjectMapper objectMapper;

    public HttpJsonRemoteDataSourceAdapter(RemoteConnectionPort connectionPort,
                                           RemoteEndpointGuard guard,
                                           ObjectMapper objectMapper) {
        this.connectionPort = connectionPort;
        this.guard = guard;
        this.objectMapper = objectMapper;
        log.debug("HttpJsonRemoteDataSourceAdapter()");
    }

    @Override
    public String feedId() { return FEED_ID; }

    @Override
    public DataSourceKind kind() { return DataSourceKind.CUSTOMER_REMOTE; }

    @Override
    public DataFeed describe() {
        log.debug("describe()");
        DataFeed result = new DataFeed(
                FEED_ID, "Customer Remote (HTTPS/JSON)",
                "Fetch JSON data from a customer-owned HTTPS endpoint.",
                DataSourceKind.CUSTOMER_REMOTE,
                List.of(ExportFormat.CSV, ExportFormat.JSON),
                List.of(
                        new DataParameter("connectionId", "Connection ID", "STRING", true, null, List.of()),
                        new DataParameter("path", "URL path suffix", "STRING", false, null, List.of())
                ),
                100, 10000, "NONE", true
        );
        log.debug("describe() | return={}", result.feedId());
        return result;
    }

    @Override
    public boolean supports(DataRequest request) {
        log.debug("supports() | feedId={}", request.feedId());
        boolean result = FEED_ID.equals(request.feedId());
        log.debug("supports() | return={}", result);
        return result;
    }

    @Override
    public DataSet fetch(DataRequest request, DataJobLog jobLog) {
        log.debug("fetch() | jobId={}, ownerEmail=[REDACTED], connectionId={}",
                request.jobId(), request.connectionId());

        String rawConnId = request.connectionId();
        if (rawConnId == null || rawConnId.isBlank()) {
            rawConnId = request.parameters().getOrDefault("connectionId", "");
        }
        if (rawConnId.isBlank()) {
            throw new IllegalArgumentException("connectionId is required for customer-remote feed");
        }
        final String connId = rawConnId;

        jobLog.phase("FETCH_START", "feed=customer-remote connectionId=" + connId);

        RemoteConnection conn = connectionPort
                .findByConnectionIdAndOwnerEmail(connId, request.ownerEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connection not found or not owned: " + connId));

        if (!conn.active()) {
            throw new IllegalStateException("Connection is inactive: " + connId);
        }

        String path = request.parameters().getOrDefault("path", "");

        byte[] responseBytes = guard.fetch(
                conn.baseUrl(), path.isBlank() ? null : path,
                conn.authType(), conn.headerName(), conn.secretRef());

        DataSet result = parseJsonToDataSet(responseBytes, request.rowLimit());

        jobLog.phase("FETCH_END", "rows=" + result.rows().size()
                + " truncated=" + (result.truncatedAtRows() > 0));

        log.debug("fetch() | return={} rows", result.rows().size());
        return result;
    }

    DataSet parseJsonToDataSet(byte[] json, int rowLimit) {
        try {
            JsonNode root = objectMapper.readTree(json);

            List<JsonNode> items;
            if (root.isArray()) {
                items = new ArrayList<>();
                root.forEach(items::add);
            } else if (root.isObject()) {
                JsonNode dataNode = findArrayNode(root);
                if (dataNode != null) {
                    items = new ArrayList<>();
                    dataNode.forEach(items::add);
                } else {
                    items = List.of(root);
                }
            } else {
                return new DataSet(List.of(), List.of(), null, List.of(), List.of(), 0);
            }

            if (items.isEmpty()) {
                return new DataSet(List.of(), List.of(), null, List.of(), List.of(), 0);
            }

            List<String> columnNames = extractColumnNames(items.get(0));
            List<DataColumn> columns = columnNames.stream()
                    .map(name -> new DataColumn(name, name, "STRING"))
                    .toList();

            int originalCount = items.size();
            boolean truncated = items.size() > rowLimit;
            if (truncated) {
                items = items.subList(0, rowLimit);
            }

            List<List<String>> rows = new ArrayList<>();
            for (JsonNode item : items) {
                List<String> row = new ArrayList<>();
                for (String col : columnNames) {
                    JsonNode val = item.get(col);
                    row.add(val != null && !val.isNull() ? val.asText() : "");
                }
                rows.add(List.copyOf(row));
            }

            return new DataSet(
                    columns, rows, null, List.of(), List.of(),
                    truncated ? originalCount : 0
            );
        } catch (Exception e) {
            log.warn("parseJsonToDataSet() | Failed to parse response: {}", e.getMessage());
            return new DataSet(List.of(), List.of(), null, List.of(),
                    List.of("Failed to parse JSON response: " + e.getMessage()), 0);
        }
    }

    private JsonNode findArrayNode(JsonNode obj) {
        Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isArray() && !entry.getValue().isEmpty()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> extractColumnNames(JsonNode firstItem) {
        List<String> names = new ArrayList<>();
        if (firstItem.isObject()) {
            firstItem.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }
}
