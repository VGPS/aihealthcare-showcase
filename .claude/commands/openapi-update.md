# Update OpenAPI Spec

Update `api/src/main/resources/openapi.yaml` for a new or modified endpoint.

## Process

1. Review the existing spec at `api/src/main/resources/openapi.yaml`.
2. Add or modify the path, operation, request body, and response schemas described below.
3. Ensure all new schemas follow the existing naming conventions (PascalCase schema names, camelCase properties).
4. After updating the YAML, remind the user to run `mvn generate-sources -pl api` to regenerate stubs.
5. Do NOT edit any files under `target/` — those are generated.

## Change requested
$ARGUMENTS
