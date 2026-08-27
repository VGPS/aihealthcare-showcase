package com.wgblackmon.aihealthcare.domain.service;

/**
 * Pure-domain classifier that maps an AI healthcare company's name, description,
 * and sub-sector text to one of seven canonical category labels.
 *
 * <p>Classification is keyword-driven and priority-ordered: the first category
 * whose keywords match the combined text wins. All matching is case-insensitive.
 * Companies that match no specific category are assigned "Other Healthcare AI".
 *
 * <p>Categories (priority order):
 * <ol>
 *   <li>Drug Discovery</li>
 *   <li>Medical Imaging &amp; Diagnostics</li>
 *   <li>Clinical Documentation</li>
 *   <li>Revenue Cycle Management</li>
 *   <li>AI Agents &amp; Virtual Care</li>
 *   <li>Data &amp; Infrastructure</li>
 *   <li>Other Healthcare AI (catch-all)</li>
 * </ol>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-27
 * @updated 2026-08-27
 */
public class HealthcareAiCompanyClassifier {

    public static final String DRUG_DISCOVERY        = "Drug Discovery";
    public static final String IMAGING_DIAGNOSTICS   = "Medical Imaging & Diagnostics";
    public static final String CLINICAL_DOCUMENTATION = "Clinical Documentation";
    public static final String REVENUE_CYCLE          = "Revenue Cycle Management";
    public static final String VIRTUAL_CARE           = "AI Agents & Virtual Care";
    public static final String DATA_INFRASTRUCTURE    = "Data & Infrastructure";
    public static final String OTHER                  = "Other Healthcare AI";

    private static final String[] DRUG_KEYWORDS = {
            "drug discovery", "drug development", "drug design",
            "pharma", "pharmaceutical", "molecule", "protein folding",
            "therapeutic", "genomic", "genomics", "biotech", "cryo-em",
            "structural biology", "oncology research", "cancer research",
            "clinical trial", "preclinical", "target identification",
            "autonomous lab", "lab automation"
    };

    private static final String[] IMAGING_KEYWORDS = {
            "radiology", "imaging", "pathology", "mri", "ct scan", "ct-scan",
            "ultrasound", "x-ray", "xray", "histopathology", "slides",
            "diagnostic imaging", "image analysis", "computer vision",
            "medical imaging", "digital pathology", "computational pathology",
            "ecg", "dermatology ai", "ophthalmology ai", "retinal"
    };

    private static final String[] DOCUMENTATION_KEYWORDS = {
            "clinical documentation", "ambient scrib", "medical scribe", "ai scribe",
            "charting", "soap note", "dictation", "voice-to-note", "voice to note",
            "note generation", "ehr documentation", "ambient documentation",
            "clinical notes", "documentation ai", "encounter documentation"
    };

    private static final String[] RCM_KEYWORDS = {
            "revenue cycle", "rcm", "medical billing", "medical coding",
            "coding automation", "auto-coding", "claims", "prior authorization",
            "prior auth", "charge capture", "denials", "claims processing",
            "payment integrity", "financial operations", "reimbursement",
            "patient access", "eligibility verification"
    };

    private static final String[] VIRTUAL_CARE_KEYWORDS = {
            "ai agent", "agentic", "copilot", "co-pilot", "virtual care",
            "telehealth", "telemedicine", "care navigation", "triage",
            "patient engagement", "conversational ai", "chatbot",
            "digital therapeutics", "remote patient monitoring",
            "care coordination", "virtual assistant", "patient communication"
    };

    private static final String[] INFRA_KEYWORDS = {
            "fhir", "ehr integration", "data platform", "analytics platform",
            "clinical data platform", "data lake", "data warehouse",
            "api platform", "interoperability", "health data", "health records",
            "operating system for health", "developer platform",
            "healthcare data", "population health", "real-world data"
    };

    /**
     * Returns the canonical category for a company based on its name,
     * description, and sub-sector text. Null inputs are treated as empty strings.
     *
     * @param name        company display name
     * @param description company description
     * @param subSector   LLM-generated sub-sector label (may be null)
     * @return one of the seven canonical category constants
     */
    public String classify(String name, String description, String subSector) {
        String text = " " + concat(name, description, subSector).toLowerCase() + " ";

        if (matchesAny(text, DRUG_KEYWORDS))          return DRUG_DISCOVERY;
        if (matchesAny(text, IMAGING_KEYWORDS))        return IMAGING_DIAGNOSTICS;
        if (matchesAny(text, DOCUMENTATION_KEYWORDS))  return CLINICAL_DOCUMENTATION;
        if (matchesAny(text, RCM_KEYWORDS))            return REVENUE_CYCLE;
        if (matchesAny(text, VIRTUAL_CARE_KEYWORDS))   return VIRTUAL_CARE;
        if (matchesAny(text, INFRA_KEYWORDS))          return DATA_INFRASTRUCTURE;
        return OTHER;
    }

    private static String concat(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                sb.append(part).append(" ");
            }
        }
        return sb.toString();
    }

    private static boolean matchesAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
