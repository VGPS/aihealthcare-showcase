package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of the patient harm category alleged in a liability incident.
 *
 * <p>MISDIAGNOSIS covers incorrect or missed diagnoses attributed to AI tools.
 * MEDICATION_ERROR covers wrong dosage, wrong drug, or premature discontinuation.
 * AUTONOMOUS_DEVICE covers harm caused by AI systems that acted without human
 * intervention (robotic surgery, autonomous infusion pumps). DOCUMENTATION_TRAP
 * covers cases where AI-generated clinical notes misled subsequent care.
 * DELAYED_CARE covers harm from AI reassurance that caused a patient to defer care.
 * OTHER is a catch-all for incidents that do not fit the above categories.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public enum HarmType {
    MISDIAGNOSIS,
    MEDICATION_ERROR,
    AUTONOMOUS_DEVICE,
    DOCUMENTATION_TRAP,
    DELAYED_CARE,
    OTHER
}
