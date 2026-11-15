package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of an AI healthcare liability incident by its legal or regulatory form.
 *
 * <p>LAWSUIT covers civil complaints filed in court. FDA_FLAG covers safety notices and
 * adverse-event filings from the FDA. WRONGFUL_DEATH is a sub-classification of a lawsuit
 * where patient death is alleged. REGULATORY_SANCTION covers enforcement actions by CMS,
 * state boards, or other regulatory bodies. SETTLEMENT records resolved cases.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public enum IncidentType {
    LAWSUIT,
    FDA_FLAG,
    WRONGFUL_DEATH,
    REGULATORY_SANCTION,
    SETTLEMENT
}
