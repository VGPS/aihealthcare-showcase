package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter that implements {@link AiSummarizationPort} using a large-language
 * model (LLM) via Spring AI's {@link ChatClient}.
 *
 * <h2>What Spring AI's ChatClient does</h2>
 * <p>{@link ChatClient} is Spring AI's unified, provider-agnostic HTTP client for talking
 * to LLMs. You build a prompt (a structured text request), send it with {@code .call()},
 * and receive the model's text response. The same code works whether the underlying model
 * is Anthropic Claude, OpenAI GPT, or any other provider — you switch models purely through
 * {@code application.properties}, with zero Java code changes.
 *
 * <p>Spring Boot auto-configures a {@link ChatClient.Builder} bean based on whichever
 * provider starter is on the classpath (e.g. {@code spring-ai-starter-model-anthropic}).
 * This adapter receives that builder via constructor injection and calls {@code .build()}
 * once to produce the reusable {@link ChatClient} instance.
 *
 * <h2>How the adapter bridges domain and AI</h2>
 * <p>The domain layer knows nothing about AI — it defines only the {@link AiSummarizationPort}
 * interface with plain Java types ({@link NewsArticle}, {@link NewsletterSection}, etc.).
 * This adapter sits in the infrastructure layer and is the only class in the project that
 * imports Spring AI. It:
 * <ol>
 *   <li>Converts domain objects into a plain-text prompt string.</li>
 *   <li>Sends the prompt to the LLM via {@code ChatClient}.</li>
 *   <li>Parses the model's plain-text response back into domain objects.</li>
 * </ol>
 *
 * <h2>Prompt templates</h2>
 * <p>Prompt text is loaded from {@code /prompts/summarize-articles.txt} and
 * {@code /prompts/generate-introduction.txt} on the classpath. Keeping prompts in
 * resource files (rather than hard-coded strings) makes them easy to tune without
 * recompiling. Placeholders like {@code {topic}} are replaced at runtime by this class.
 *
 * <h2>Response parsing</h2>
 * <p>The summarize prompt instructs the model to reply in a strict two-line format:
 * <pre>
 * HEADLINE: &lt;text&gt;
 * SUMMARY: &lt;text&gt;
 * </pre>
 * This class parses that format with a simple loop. A more robust approach (structured
 * output via JSON schema) can be introduced in a later slice without changing the port
 * interface.
 *
 * <h2>Unit testing without real AI calls</h2>
 * <p>Because this adapter implements a port interface, unit tests never instantiate it.
 * Instead, they inject a Mockito mock of {@link AiSummarizationPort}. Real calls to the
 * LLM happen only in smoke tests annotated with {@code @ActiveProfiles("ai-integration")}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
@Slf4j
@Component
public class AiSummarizationAdapter implements AiSummarizationPort {

    /**
     * Spring AI's fluent HTTP client for LLM communication.
     * Built once from the auto-configured {@link ChatClient.Builder} and reused for
     * every request — {@link ChatClient} instances are thread-safe.
     */
    private final ChatClient chatClient;

    /**
     * Constructs the adapter and builds the shared {@link ChatClient}.
     *
     * <p>Spring Boot injects {@link ChatClient.Builder} automatically based on the
     * provider starter present on the classpath. No API key wiring is needed here —
     * credentials are read from {@code application.properties} (or environment variables)
     * by the auto-configuration.
     *
     * @param chatClientBuilder Auto-configured builder provided by Spring AI.
     */
    public AiSummarizationAdapter(ChatClient.Builder chatClientBuilder) {
        log.debug("AiSummarizationAdapter() | chatClientBuilder={}", chatClientBuilder);
        this.chatClient = chatClientBuilder.build();
        log.debug("AiSummarizationAdapter() | return=void");
    }

    // -------------------------------------------------------------------------
    // AiSummarizationPort
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p><b>Implementation detail:</b> assembles a prompt that includes each article's
     * title and body text, instructs the model on tone, then parses the two-line
     * {@code HEADLINE / SUMMARY} response format into a {@link NewsletterSection}.
     */
    @Override
    public NewsletterSection summarize(List<NewsArticle> articles,
                                       String topic,
                                       NewsletterTone tone,
                                       String sectionId) {
        log.debug("summarize() | topic={}, tone={}, sectionId={}, articleCount={}",
                  topic, tone, sectionId, articles.size());

        String prompt = buildSummarizePrompt(articles, topic, tone);
        log.debug("summarize() | sending prompt to LLM, length={} chars", prompt.length());

        // .prompt(text)  — sets the user message
        // .call()        — sends the HTTP request to the LLM provider (blocking)
        // .content()     — extracts the model's reply as a plain String
        String response = chatClient.prompt(prompt).call().content();
        log.debug("summarize() | received LLM response, length={} chars", response.length());

        NewsletterSection result = parseSection(response, topic, sectionId, articles);
        log.info("summarize() | Section produced: sectionId={}, topic={}", sectionId, topic);
        log.debug("summarize() | return={}", result);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Implementation detail:</b> builds a prompt from the section headlines already
     * generated, then returns the model's reply directly as the introduction string.
     */
    @Override
    public String generateIntroduction(List<NewsletterSection> sections, NewsletterTone tone) {
        log.debug("generateIntroduction() | sectionCount={}, tone={}", sections.size(), tone);

        String prompt = buildIntroductionPrompt(sections, tone);
        log.debug("generateIntroduction() | sending prompt to LLM, length={} chars", prompt.length());

        String result = chatClient.prompt(prompt).call().content();
        log.info("generateIntroduction() | Introduction generated, length={} chars", result.length());
        log.debug("generateIntroduction() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Prompt builders
    // -------------------------------------------------------------------------

    /**
     * Constructs the summarization prompt by filling the template placeholders
     * with live article data and the selected tone instruction.
     *
     * <p>Each article is rendered as a numbered block:
     * <pre>
     * [1] Title: &lt;title&gt;
     *     Body:  &lt;bodyText&gt;
     * </pre>
     */
    private String buildSummarizePrompt(List<NewsArticle> articles,
                                         String topic,
                                         NewsletterTone tone) {
        log.debug("buildSummarizePrompt() | topic={}, tone={}, articleCount={}", topic, tone, articles.size());

        StringBuilder articlesBlock = new StringBuilder();
        int index = 1;
        for (NewsArticle article : articles) {
            articlesBlock.append("[").append(index).append("] Title: ").append(article.title()).append("\n");
            if (article.author() != null && !article.author().isBlank()) {
                articlesBlock.append("    By:    ").append(article.author()).append("\n");
            }
            articlesBlock.append("    Body:  ").append(article.bodyText()).append("\n\n");
            index++;
        }

        String template = loadTemplate("summarize-articles.txt");
        String result = template
                .replace("{toneInstruction}", toneInstruction(tone))
                .replace("{topic}", topic)
                .replace("{articleCount}", String.valueOf(articles.size()))
                .replace("{articles}", articlesBlock.toString().trim());

        log.debug("buildSummarizePrompt() | return=prompt[{} chars]", result.length());
        return result;
    }

    /**
     * Constructs the introduction prompt by listing the headline of each section
     * so the model can write a cohesive overview paragraph.
     */
    private String buildIntroductionPrompt(List<NewsletterSection> sections, NewsletterTone tone) {
        log.debug("buildIntroductionPrompt() | sectionCount={}, tone={}", sections.size(), tone);

        StringBuilder headlines = new StringBuilder();
        for (NewsletterSection section : sections) {
            headlines.append("- ").append(section.headline()).append("\n");
        }

        String template = loadTemplate("generate-introduction.txt");
        String result = template
                .replace("{toneInstruction}", toneInstruction(tone))
                .replace("{sectionCount}", String.valueOf(sections.size()))
                .replace("{headlines}", headlines.toString().trim());

        log.debug("buildIntroductionPrompt() | return=prompt[{} chars]", result.length());
        return result;
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the model's three-line {@code HEADLINE / SUMMARY / SECTION_TYPE} response
     * into a {@link NewsletterSection}.
     *
     * <p>The prompt instructs the model to reply in this exact format:
     * <pre>
     * HEADLINE: &lt;text&gt;
     * SUMMARY:  &lt;text&gt;
     * SECTION_TYPE: &lt;enum value&gt;
     * </pre>
     * LLMs occasionally deviate from strict formatting instructions. This method
     * therefore uses fallback values for missing fields rather than throwing, so the
     * pipeline degrades gracefully:
     * <ul>
     *   <li>Missing/blank {@code HEADLINE} or {@code SUMMARY} → placeholder text + {@code log.warn}</li>
     *   <li>Missing or unrecognised {@code SECTION_TYPE} → defaults to {@link SectionType#WHAT_SHIPPED}
     *       + {@code log.warn}</li>
     * </ul>
     *
     * @param response  Raw text returned by the LLM.
     * @param topic     Topic label to embed in the section.
     * @param sectionId Identifier to assign to the returned section.
     * @param articles  Source articles; their IDs are recorded for attribution.
     */
    private NewsletterSection parseSection(String response,
                                            String topic,
                                            String sectionId,
                                            List<NewsArticle> articles) {
        log.debug("parseSection() | sectionId={}, responseLength={}", sectionId, response.length());

        String headline        = "[Headline unavailable]";
        String summary         = "[Summary unavailable]";
        String sectionTypeRaw  = null;

        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("HEADLINE:")) {
                headline = trimmed.substring("HEADLINE:".length()).trim();
            } else if (trimmed.startsWith("SUMMARY:")) {
                summary = trimmed.substring("SUMMARY:".length()).trim();
            } else if (trimmed.startsWith("SECTION_TYPE:")) {
                sectionTypeRaw = trimmed.substring("SECTION_TYPE:".length()).trim();
            }
        }

        if (headline.equals("[Headline unavailable]") || summary.equals("[Summary unavailable]")) {
            log.warn("parseSection() | LLM response missing HEADLINE or SUMMARY for sectionId={}; "
                     + "using fallback values. Raw response: {}", sectionId, response);
        }

        SectionType sectionType = resolveSectionType(sectionTypeRaw, sectionId);

        List<String> articleIds = new ArrayList<>();
        for (NewsArticle article : articles) {
            articleIds.add(article.articleId());
        }

        NewsletterSection result = new NewsletterSection(sectionId, sectionType, topic, headline, summary, articleIds);
        log.debug("parseSection() | return={}", result);
        return result;
    }

    /**
     * Converts the raw string from the LLM response to a {@link SectionType}, falling
     * back to {@link SectionType#WHAT_SHIPPED} when the value is missing or unrecognised.
     *
     * @param raw       The raw string parsed from the {@code SECTION_TYPE:} line, may be null.
     * @param sectionId Section being parsed — used only for the warning log.
     */
    private SectionType resolveSectionType(String raw, String sectionId) {
        log.debug("resolveSectionType() | raw={}, sectionId={}", raw, sectionId);
        if (raw == null || raw.isBlank()) {
            log.warn("resolveSectionType() | SECTION_TYPE missing from LLM response for sectionId={}; "
                     + "defaulting to WHAT_SHIPPED", sectionId);
            log.debug("resolveSectionType() | return={}", SectionType.WHAT_SHIPPED);
            return SectionType.WHAT_SHIPPED;
        }
        try {
            SectionType result = SectionType.valueOf(raw.toUpperCase());
            log.debug("resolveSectionType() | return={}", result);
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("resolveSectionType() | Unrecognised SECTION_TYPE '{}' for sectionId={}; "
                     + "defaulting to WHAT_SHIPPED", raw, sectionId);
            log.debug("resolveSectionType() | return={}", SectionType.WHAT_SHIPPED);
            return SectionType.WHAT_SHIPPED;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a tone-specific instruction sentence appended to every prompt so the
     * model adopts the correct register for the target audience.
     */
    private String toneInstruction(NewsletterTone tone) {
        log.debug("toneInstruction() | tone={}", tone);
        String result;
        switch (tone) {
            case PROFESSIONAL -> result =
                    "Write in a formal, clinical tone suited for healthcare executives and medical professionals.";
            case ACCESSIBLE   -> result =
                    "Write in plain, jargon-free language suited for a general healthcare audience.";
            case TECHNICAL    -> result =
                    "Write in a precise, detail-oriented tone suited for engineers and data scientists.";
            default           -> result =
                    "Write clearly and concisely.";
        }
        log.debug("toneInstruction() | return={}", result);
        return result;
    }

    /**
     * Loads a prompt template from {@code /prompts/} on the classpath.
     *
     * <p>Templates live in {@code src/main/resources/prompts/} and are packaged into
     * the application JAR, making them easy to edit without recompiling Java classes.
     *
     * @param filename The filename within the {@code /prompts/} directory.
     * @return The template content as a plain String.
     * @throws IllegalStateException if the template file cannot be found or read.
     */
    private String loadTemplate(String filename) {
        log.debug("loadTemplate() | filename={}", filename);
        try (var stream = getClass().getResourceAsStream("/prompts/" + filename)) {
            if (stream == null) {
                throw new IllegalStateException("Prompt template not found on classpath: /prompts/" + filename);
            }
            String result = new String(stream.readAllBytes());
            log.debug("loadTemplate() | return=template[{} chars]", result.length());
            return result;
        } catch (java.io.IOException e) {
            log.error("loadTemplate() | Failed to read prompt template: filename={}", filename, e);
            throw new IllegalStateException("Failed to read prompt template: /prompts/" + filename, e);
        }
    }
}
