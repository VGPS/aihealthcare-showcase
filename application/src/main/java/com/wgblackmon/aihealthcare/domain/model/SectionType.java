package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classifies the editorial role of a {@link NewsletterSection} within an
 * AI-in-Healthcare newsletter issue.
 *
 * <p>Every section produced by the AI summarization step is assigned exactly one
 * type. The type drives how the newsletter renderer styles and positions the section
 * and allows readers to filter for the content categories they care about most.
 *
 * <p>The AI model is responsible for selecting the most appropriate type based on
 * the source articles. If the model's response cannot be mapped to a known value,
 * the infrastructure adapter falls back to {@link #WHAT_SHIPPED} and logs a warning.
 *
 * <p>This enum is part of the domain layer and has no framework dependencies.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-07-05
 */
public enum SectionType {

    /**
     * A new product, model, feature, or clinical capability has been released or
     * announced. Typical subjects: FDA clearances, model launches, platform updates.
     */
    WHAT_SHIPPED,

    /**
     * A design pattern, system-design insight, or technical decision relevant to
     * building AI-powered health systems. Typical subjects: MLOps practices, data
     * pipeline design, interoperability approaches (FHIR, HL7).
     */
    ARCHITECTURE_NOTE,

    /**
     * A post-mortem, identified risk, cautionary finding, or known failure pattern
     * in an AI health application. Typical subjects: bias studies, recall events,
     * algorithm audits, adverse-outcome analyses.
     */
    FAILURE_MODE,

    /**
     * A regulatory, compliance, reimbursement, or governance development that
     * affects how AI can be deployed or monetised in healthcare.
     * Typical subjects: FDA guidance, CMS rule-making, EU AI Act updates, IRB policy.
     */
    POLICY_WATCH,

    /**
     * A notable tool, open-source library, dataset, or benchmark worth knowing about.
     * Typical subjects: new model weights, public datasets, evaluation frameworks,
     * developer SDKs.
     */
    TOOL_PICK,

    /**
     * A regulatory U-turn, walked-back vendor claim, or failed replication
     * detected by the wiki's contradiction tracking system.  Typical subjects:
     * reversed FDA guidance, retracted efficacy claims, contradicted study results.
     */
    REVERSAL_WATCH,

    /**
     * A digest of recent legal, regulatory, and policy developments affecting
     * AI in healthcare.  Typical subjects: litigation filings, FDA enforcement,
     * CMS rule-making, HIPAA compliance actions, government policy changes.
     */
    LEGAL_BRIEF
}
