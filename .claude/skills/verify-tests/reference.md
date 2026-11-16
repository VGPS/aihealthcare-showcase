# Test-Weakening Patterns (JUnit 5 + AssertJ + Mockito)

Reference catalog for the `verify-tests` skill. Each pattern is a way a test can
be edited to pass without the underlying behavior actually being correct. Use
this to judge test-file diffs — not an exhaustive list, but covers the common
cases for this project's stack.

## 1. Exact assertion loosened to a weaker check

```java
// before
assertThat(result.totalCount()).isEqualTo(42);
// after (weakened)
assertThat(result.totalCount()).isNotNull();
```

## 2. Assertion count reduced

A test that checked 5 fields on a result record now only checks 1-2, with no
explanation for why the others are no longer relevant.

## 3. Exception expectation removed or widened

```java
// before
assertThatThrownBy(() -> service.process(badInput))
    .isInstanceOf(InvalidArticleException.class);
// after (weakened)
assertThatThrownBy(() -> service.process(badInput))
    .isInstanceOf(Exception.class);   // or: assertion deleted entirely
```

## 4. Tolerance/offset widened

```java
// before
assertThat(score).isCloseTo(0.85, within(0.01));
// after (weakened)
assertThat(score).isCloseTo(0.85, within(0.5));
```

## 5. Test disabled instead of fixed

```java
@Disabled("flaky")   // added during this change, wasn't there before
@Test
void shouldRejectDuplicateUrls() { ... }
```

## 6. Tautological mock stubbing

```java
// before — exercises the real branching logic
when(aiPort.summarize(argThat(req -> req.topic().equals("oncology"))))
    .thenReturn(expectedSummary);
// after (weakened) — passes regardless of what's actually passed in
when(aiPort.summarize(any())).thenReturn(expectedSummary);
```

## 7. `verify()` relaxed

```java
// before
verify(articleStoragePort, times(1)).save(article);
// after (weakened)
verify(articleStoragePort, atLeastOnce()).save(any());
```

## 8. Parameterized cases reduced

A `@CsvSource` / `@ValueSource` that covered an edge case (empty string, null,
boundary value) has that case silently dropped from the list.

## 9. Tautological expected value

The "expected" value in the assertion is computed by calling the same
production method under test, rather than being a known independent value —
the test can never fail regardless of a regression.

## 10. Catch-and-swallow added

```java
// after (weakened) — suppresses a failure path the test used to require
try {
    service.process(badInput);
} catch (Exception ignored) {
    // was previously: assertThatThrownBy(...).isInstanceOf(...)
}
```

## Not weakening — avoid false positives

- Renaming a test method for clarity with the same assertions.
- Splitting one large test into several smaller ones covering the same ground.
- Adding *additional* assertions alongside the existing ones.
- Loosening a genuinely over-specific test because the production contract
  itself changed on purpose — confirm the behavior change is real and intended,
  then say so explicitly rather than silently treating it as fine.
