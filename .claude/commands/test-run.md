# Run Tests

Run the appropriate Maven test command for the AIHealthcare project.

## Rules
- Default: exclude AI smoke tests (`-P '!ai-smoke'`)
- Only enable AI smoke profile when explicitly requested AND `ANTHROPIC_API_KEY` is set
- Run only the affected module(s) when possible to keep feedback fast
- Report: number of tests run, failures, and any skipped tests

## Test target / scope
$ARGUMENTS

## Commands to use

```bash
# Unit tests only (default — no real AI calls)
mvn test -pl <module> -P '!ai-smoke'

# Full build all modules
mvn clean install -P '!ai-smoke'

# Single test class
mvn test -pl <module> -Dtest=<ClassName> -P '!ai-smoke'

# AI smoke tests (only when explicitly requested)
mvn test -pl infrastructure -P ai-smoke -Dtest=<SmokeTestClass>
```
