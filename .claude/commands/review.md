# Code Review

Review the specified file or module for the AIHealthcare project.

## Review checklist

- **Architecture compliance**: Does this class live in the correct module? No Spring annotations in `domain`?
- **Hexagonal boundaries**: Are ports defined as interfaces in `domain`? Are adapters in `infrastructure`?
- **Record usage**: Are immutable value types implemented as Java records?
- **Null safety**: Are `Optional<T>` returns used where null is a valid absence?
- **Exception handling**: Are domain exceptions used? No silent swallowing?
- **Test coverage**: Is there a corresponding unit test? Is the AI port mocked?
- **Lombok usage**: Is `@RequiredArgsConstructor` preferred over `@AllArgsConstructor`?
- **Overengineering check**: Is there unnecessary abstraction or premature generalization?
- **Code style**: PascalCase classes, camelCase methods, `com.aihealthcare` root package?

## Target
$ARGUMENTS
