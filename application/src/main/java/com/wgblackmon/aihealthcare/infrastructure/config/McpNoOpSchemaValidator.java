package com.wgblackmon.aihealthcare.infrastructure.config;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;

import java.util.Map;

/**
 * No-op JSON schema validator that always returns valid.
 *
 * <p>Registered as a ServiceLoader provider so that {@code McpServiceLoader.findFirst()}
 * always picks THIS implementation before the incompatible {@code mcp-json-jackson2:0.18.2}
 * one (from Spring AI) or the Jackson-3-dependent one (from {@code mcp-json-jackson3:2.1.0}).
 *
 * <p>Application classes are first on the classpath, guaranteeing this wins. The server
 * controls all tool input schemas in {@code McpServerConfig}, so skipping runtime
 * validation is safe.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-01
 * @updated 2026-11-01
 */
public class McpNoOpSchemaValidator implements JsonSchemaValidator, JsonSchemaValidatorSupplier {

    @Override
    public ValidationResponse validateSchema(Map<String, Object> schema) {
        return ValidationResponse.asValid("{}");
    }

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        return ValidationResponse.asValid("{}");
    }

    @Override
    public JsonSchemaValidator get() {
        return this;
    }
}
