package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java domain service that applies keyword-based heuristics to classify
 * companies into AI-healthcare subcategories and set coarse AI/health flags.
 *
 * <p>Classification works by scanning the concatenation of company name and
 * description (lowercased) for predefined keyword patterns. Each tag
 * (scribe, agent, imaging, rcm, infra) is set independently — a company
 * can match multiple categories.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public class CompanyClassifier {

    // ---- AI Scribes / Ambient Documentation ----
    private static final String[] SCRIBE_KEYWORDS = {
            "ai scribe", "medical scribe", "ambient scribe", "ambient documentation",
            "clinical documentation", "clinical notes", "charting", "soap notes",
            "dictation", "voice-to-note", "voice to note", "note generation"
    };

    // ---- AI Agents & Virtual Care ----
    private static final String[] AGENT_KEYWORDS = {
            "ai agent", "agentic", "copilot", "co-pilot", "ai assistant",
            "virtual care", "telehealth", "telemedicine",
            "care navigation", "triage", "patient engagement",
            "conversational ai", "chatbot"
    };

    // ---- Imaging / Diagnostics / Pathology ----
    private static final String[] IMAGING_KEYWORDS = {
            "radiology", "imaging", "ct scan", "ct-scan", "mri", "ultrasound",
            "x-ray", "xray", "pathology", "histopathology", "slides",
            "diagnostic imaging", "image analysis", "computer vision"
    };

    // ---- RCM / Coding / Billing ----
    private static final String[] RCM_KEYWORDS = {
            "revenue cycle", "rcm", "billing", "claims", "denials",
            "medical coding", "coding automation", "auto-coding",
            "prior authorization", "prior auth",
            "charge capture", "midcycle", "claims processing",
            "payment integrity", "financial infrastructure"
    };

    // ---- Infrastructure / Data / Platforms ----
    private static final String[] INFRA_KEYWORDS = {
            "data platform", "data infrastructure", "analytics platform",
            "clinical data platform", "data lake", "data warehouse",
            "healthcare ai platform", "ai operating system", "operating system for healthcare",
            "api", "developer platform", "sdk", "fhir", "ehr"
    };

    // ---- Coarse AI flag ----
    private static final String[] AI_KEYWORDS = {
            " ai ", "ai-", "artificial intelligence", "machine learning",
            " ml ", "llm", "copilot", "agent"
    };

    // ---- Coarse Health flag ----
    private static final String[] HEALTH_KEYWORDS = {
            "health", "healthcare", "clinical", "patient", "hospital",
            "care", "medic", "provider", "clinic"
    };

    /**
     * Classifies a single company by applying tag and flag heuristics.
     *
     * @param company the company to classify (tags/flags may be unset)
     * @return a new {@link Company} with tags and flags populated
     */
    public Company classify(Company company) {
        String text = " " + (company.name() + " " + company.description()).toLowerCase() + " ";

        boolean scribe = matchesAny(text, SCRIBE_KEYWORDS);
        boolean agent = matchesAny(text, AGENT_KEYWORDS);
        boolean imaging = matchesAny(text, IMAGING_KEYWORDS);
        boolean rcm = matchesAny(text, RCM_KEYWORDS);
        boolean infra = matchesAny(text, INFRA_KEYWORDS);
        boolean isAI = matchesAny(text, AI_KEYWORDS);
        boolean isHealth = matchesAny(text, HEALTH_KEYWORDS);

        CompanyTags tags = new CompanyTags(scribe, agent, imaging, rcm, infra);

        return new Company(
                company.name(),
                company.source(),
                company.url(),
                company.companySite(),
                company.description(),
                tags,
                isAI,
                isHealth
        );
    }

    /**
     * Classifies all companies in the list.
     *
     * @param companies the raw companies
     * @return a new list with tags and flags populated
     */
    public List<Company> classifyAll(List<Company> companies) {
        List<Company> result = new ArrayList<>();
        for (Company company : companies) {
            result.add(classify(company));
        }
        return result;
    }

    private boolean matchesAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
