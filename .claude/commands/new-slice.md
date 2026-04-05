# New Vertical Slice

Scaffold a new vertical slice for the AIHealthcare application following our Spec-Driven Development process.

## Steps to follow

1. **Clarify the slice** — confirm the feature name, HTTP verb/path, and domain type(s) involved.
2. **OpenAPI first** — add or update the endpoint definition in `api/src/main/resources/openapi.yaml`.
3. **Domain layer** — add or update records/interfaces in the `domain` module (zero Spring dependencies).
4. **Application layer** — add the use-case method to the application service in `application` module.
5. **Infrastructure stub** — add a minimal port implementation in the appropriate `infrastructure/` sub-module.
6. **Web layer** — add the controller method in `web` module, delegating to the application service.
7. **Unit test** — write a unit test for the application service method that mocks all ports.
8. **Verify build** — run `mvn clean install -pl domain,application,web -P '!ai-smoke'` and confirm it passes.

## Feature being scaffolded
$ARGUMENTS
