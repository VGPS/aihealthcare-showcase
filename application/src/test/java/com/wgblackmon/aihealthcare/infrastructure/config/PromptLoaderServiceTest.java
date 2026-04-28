package com.wgblackmon.aihealthcare.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PromptLoaderService}.
 *
 * <p>Covers filesystem-first loading, classpath fallback, missing-file errors,
 * and the empty-directory-string behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
class PromptLoaderServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void load_classpathFallback_returnsTemplate() {
        PromptLoaderService service = new PromptLoaderService("");

        String result = service.load("summarize-articles.txt");

        assertThat(result).isNotBlank();
        assertThat(result).contains("{topic}");
        assertThat(result).contains("{articles}");
    }

    @Test
    void load_externalFileOverridesClasspath() throws IOException {
        String overrideText = "CUSTOM PROMPT for {topic}";
        Path externalFile = tempDir.resolve("summarize-articles.txt");
        Files.writeString(externalFile, overrideText, StandardCharsets.UTF_8);

        PromptLoaderService service = new PromptLoaderService(tempDir.toString());

        String result = service.load("summarize-articles.txt");

        assertThat(result).isEqualTo(overrideText);
    }

    @Test
    void load_externalDirSetButFileAbsent_fallsBackToClasspath() {
        // tempDir exists but has no summarize-articles.txt in it
        PromptLoaderService service = new PromptLoaderService(tempDir.toString());

        String result = service.load("summarize-articles.txt");

        assertThat(result).isNotBlank();
        assertThat(result).contains("{topic}");
    }

    @Test
    void load_classpathTemplateNotFound_throwsIllegalState() {
        PromptLoaderService service = new PromptLoaderService("");

        assertThatThrownBy(() -> service.load("nonexistent-template.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonexistent-template.txt");
    }

    @Test
    void load_allFourClasspathTemplatesExist() {
        PromptLoaderService service = new PromptLoaderService("");

        assertThat(service.load("summarize-articles.txt")).isNotBlank();
        assertThat(service.load("summarize-articles-rag.txt")).isNotBlank();
        assertThat(service.load("evaluate-section.txt")).isNotBlank();
        assertThat(service.load("generate-introduction.txt")).isNotBlank();
    }
}
