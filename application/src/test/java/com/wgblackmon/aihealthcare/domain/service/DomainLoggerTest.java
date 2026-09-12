package com.wgblackmon.aihealthcare.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DomainLogger}.
 *
 * <p>Focuses on the {@code toIndexed()} placeholder conversion since the
 * logging delegation itself goes through {@link System.Logger} which is
 * JDK-provided. The conversion must translate SLF4J-style {@code {}}
 * placeholders into {@link java.text.MessageFormat}-style {@code {0}, {1}}
 * indexed placeholders.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-11
 */
class DomainLoggerTest {

    @Test
    void toIndexed_noPlaceholders_returnsUnchanged() {
        assertThat(DomainLogger.toIndexed("no placeholders here"))
                .isEqualTo("no placeholders here");
    }

    @Test
    void toIndexed_singlePlaceholder_replacesWithZero() {
        assertThat(DomainLogger.toIndexed("value={}"))
                .isEqualTo("value={0}");
    }

    @Test
    void toIndexed_multiplePlaceholders_numbersSequentially() {
        assertThat(DomainLogger.toIndexed("a={}, b={}, c={}"))
                .isEqualTo("a={0}, b={1}, c={2}");
    }

    @Test
    void toIndexed_adjacentPlaceholders_eachNumbered() {
        assertThat(DomainLogger.toIndexed("{}{}"))
                .isEqualTo("{0}{1}");
    }

    @Test
    void toIndexed_preservesNonPlaceholderBraces() {
        assertThat(DomainLogger.toIndexed("json {\"key\": {}}"))
                .isEqualTo("json {\"key\": {0}}");
    }

    @Test
    void toIndexed_emptyString_returnsEmpty() {
        assertThat(DomainLogger.toIndexed("")).isEqualTo("");
    }

    @Test
    void toIndexed_typicalLogPattern_convertsCorrectly() {
        String input = "method() | param1={}, param2={}, result={}";
        String expected = "method() | param1={0}, param2={1}, result={2}";
        assertThat(DomainLogger.toIndexed(input)).isEqualTo(expected);
    }

    @Test
    void debug_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        log.debug("test message");
        log.debug("test with arg={}", "value");
        log.debug("multi args={}, {}", "a", "b");
    }

    @Test
    void info_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        log.info("test info");
        log.info("info with arg={}", 42);
    }

    @Test
    void warn_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        log.warn("test warning");
        log.warn("warning with arg={}", "val");
    }

    @Test
    void error_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        log.error("test error");
        log.error("error with arg={}", "val");
    }

    @Test
    void error_withThrowable_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        RuntimeException ex = new RuntimeException("test");
        log.error("failed: {}", "ctx", ex);
    }

    @Test
    void warn_withThrowableOnly_doesNotThrow() {
        DomainLogger log = new DomainLogger(DomainLoggerTest.class);
        RuntimeException ex = new RuntimeException("test");
        log.warn("something broke", ex);
    }
}
