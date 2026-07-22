package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of regulatory events monitored by the system.
 *
 * <p>FDA events include 510(k) clearances, De Novo classifications,
 * PMA approvals, and guidance documents. CMS events include proposed
 * and final rules, plus National Coverage Determinations (NCDs).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public enum RegulatoryEventType {
    FDA_510K_CLEARANCE,
    FDA_DE_NOVO_CLASSIFICATION,
    FDA_PMA_APPROVAL,
    FDA_GUIDANCE_DRAFT,
    FDA_GUIDANCE_FINAL,
    CMS_PROPOSED_RULE,
    CMS_FINAL_RULE,
    CMS_NCD,
    FDA_DIGITAL_HEALTH_ANNOUNCEMENT,
    OTHER
}
