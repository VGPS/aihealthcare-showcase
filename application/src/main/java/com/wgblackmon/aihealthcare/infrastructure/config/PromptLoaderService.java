package com.wgblackmon.aihealthcare.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem-first, classpath-fallback loader for prompt template files.
 *
 * <p>When {@code aihealthcare.prompts.directory} is set to a non-blank path,
 * this service checks that directory for the requested template file before
 * falling back to the packaged {@code /prompts/} directory on the classpath.
 * This allows prompt text to be edited with any text editor at runtime —
 * without recompiling or redeploying the application.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If {@code aihealthcare.prompts.directory} is set and the file exists
 *       there, return its contents.</li>
 *   <li>Otherwise load {@code /prompts/{templateName}} from the classpath.</li>
 * </ol>
 *
 * <p>Typical YAML configuration:
 * <pre>{@code
 * aihealthcare:
 *   prompts:
 *     directory: prompts/   # relative or absolute path; empty = classpath only
 * }</pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Slf4j
@Service
public class PromptLoaderService {

    private final String promptsDirectory;

    /**
     * Constructs the service with the configured external prompts directory.
     *
     * @param promptsDirectory Path to an external directory containing prompt
     *                         template files.  An empty string disables
     *                         filesystem resolution and falls back to classpath.
     */
    public PromptLoaderService(
            @Value("${aihealthcare.prompts.directory:}") String promptsDirectory) {
        log.debug("PromptLoaderService() | promptsDirectory='{}'", promptsDirectory);
        this.promptsDirectory = promptsDirectory;
        if (promptsDirectory != null && !promptsDirectory.isBlank()) {
            log.info("PromptLoaderService() | External prompts directory configured: {}", promptsDirectory);
        } else {
            log.info("PromptLoaderService() | No external prompts directory — classpath only");
        }
        log.debug("PromptLoaderService() | return=void");
    }

    /**
     * Loads a prompt template by filename, checking the external directory first
     * and falling back to the classpath.
     *
     * @param templateName Filename within the prompts directory (e.g.
     *                     {@code "summarize-articles.txt"}).  Must not be blank.
     * @return The template content as a UTF-8 string.
     * @throws IllegalStateException if the template cannot be found or read from
     *                               either location.
     */
    public String load(String templateName) {
        log.debug("load() | templateName={}", templateName);

        // Step 1 — try external filesystem directory
        if (promptsDirectory != null && !promptsDirectory.isBlank()) {
            Path externalPath = Paths.get(promptsDirectory, templateName);
            if (Files.exists(externalPath)) {
                try {
                    String result = Files.readString(externalPath, StandardCharsets.UTF_8);
                    log.info("load() | Loaded from filesystem: {}", externalPath.toAbsolutePath());
                    log.debug("load() | return=template[{} chars]", result.length());
                    return result;
                } catch (IOException e) {
                    log.warn("load() | Failed to read external template '{}' — falling back to classpath",
                             externalPath.toAbsolutePath(), e);
                }
            } else {
                log.debug("load() | External template not found at '{}' — falling back to classpath",
                          externalPath.toAbsolutePath());
            }
        }

        // Step 2 — fall back to classpath /prompts/
        String classpathPath = "/prompts/" + templateName;
        try (InputStream stream = getClass().getResourceAsStream(classpathPath)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Prompt template not found on classpath: " + classpathPath);
            }
            String result = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("load() | Loaded from classpath: {}", classpathPath);
            log.debug("load() | return=template[{} chars]", result.length());
            return result;
        } catch (IOException e) {
            log.error("load() | Failed to read classpath template: {}", classpathPath, e);
            throw new IllegalStateException(
                    "Failed to read prompt template: " + classpathPath, e);
        }
    }
}
