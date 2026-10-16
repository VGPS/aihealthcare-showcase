package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.PipelineErrorType;

/**
 * Classifies pipeline-step failures into a {@link PipelineErrorType} and, where
 * possible, the LLM provider responsible — based on the exception message and
 * cause chain, since Spring AI / RestClient embed HTTP status text there rather
 * than exposing typed status codes.
 *
 * <p>Extracted from {@code StartupPipelineOrchestrator} (originally two private
 * methods there) so {@link ResilientStepExecutor} can apply the same
 * classification rules without duplicating them.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public final class PipelineErrorClassifier {

    private PipelineErrorClassifier() {
    }

    public static PipelineErrorType classify(Throwable t) {
        Throwable cause = t.getCause();
        if (cause instanceof OutOfMemoryError || cause instanceof StackOverflowError) {
            return PipelineErrorType.FATAL;
        }

        String msg = buildFullMessage(t).toLowerCase();
        if (msg.contains("401") || msg.contains("403")
                || msg.contains("unauthorized") || msg.contains("authentication failed")
                || msg.contains("invalid api key") || msg.contains("invalid_api_key")
                || msg.contains("forbidden") || msg.contains("permission denied")) {
            return PipelineErrorType.LLM_AUTH;
        }
        if (msg.contains("402") || msg.contains("429")
                || msg.contains("payment required") || msg.contains("insufficient_quota")
                || msg.contains("rate limit") || msg.contains("quota exceeded")
                || msg.contains("billing") || msg.contains("credit")) {
            return PipelineErrorType.LLM_QUOTA;
        }
        if (msg.contains("504") || msg.contains("503") || msg.contains("502")
                || msg.contains("timeout") || msg.contains("timed out")
                || msg.contains("connection refused") || msg.contains("connection reset")
                || msg.contains("failed to connect") || msg.contains("network error")
                || msg.contains("socketexception") || msg.contains("sockettimeout")) {
            return PipelineErrorType.NETWORK;
        }
        return PipelineErrorType.UNKNOWN;
    }

    /** Returns the LLM provider responsible for the failure, or null if not LLM-related. */
    public static String detectProvider(Throwable t) {
        String msg = buildFullMessage(t).toLowerCase();
        if (msg.contains("anthropic") || msg.contains("claude")) {
            return "Anthropic";
        }
        if (msg.contains("openai") || msg.contains("gpt")) {
            return "OpenAI";
        }
        if (msg.contains("perplexity") || msg.contains("sonar")) {
            return "Perplexity";
        }
        if (msg.contains("gemini") || msg.contains("google.generativeai") || msg.contains("generativelanguage")) {
            return "Gemini";
        }
        if (msg.contains("alpaca")) {
            return "Alpaca";
        }
        return null;
    }

    /** Builds a searchable string from the exception message and its full cause chain. */
    private static String buildFullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            sb.append(current.getClass().getName()).append(' ');
            current = current.getCause();
        }
        return sb.toString();
    }
}
