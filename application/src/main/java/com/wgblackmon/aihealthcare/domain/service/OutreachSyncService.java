package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyOutreachPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.wgblackmon.aihealthcare.domain.model.OutreachPurpose.EMPLOYMENT;
import static com.wgblackmon.aihealthcare.domain.model.OutreachPurpose.SUBSCRIPTION;

/**
 * Domain service that ensures every company in the healthcare AI directory has
 * EMPLOYMENT and SUBSCRIPTION outreach rows in the CRM.
 *
 * <p>On each invocation, fetches all companies and all existing outreach rows,
 * computes the difference, and creates NOT_STARTED rows for any missing
 * (slug, purpose) combinations. Idempotent — running it twice produces no
 * additional rows.
 *
 * <p>This service is pure domain — no Spring, Lombok, or infrastructure imports.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-17
 * @updated 2026-10-18
 */
public class OutreachSyncService {

    /** Purposes auto-synced for every directory company. ACQUISITION is manual-only. */
    private static final List<OutreachPurpose> SYNCED_PURPOSES = List.of(EMPLOYMENT, SUBSCRIPTION);

    private final CompanyOutreachPort outreachPort;
    private final HealthcareAiCompanyPort companyPort;

    public OutreachSyncService(CompanyOutreachPort outreachPort,
                               HealthcareAiCompanyPort companyPort) {
        this.outreachPort = outreachPort;
        this.companyPort = companyPort;
    }

    /**
     * Creates missing EMPLOYMENT and SUBSCRIPTION outreach rows for every company
     * in the healthcare AI directory that does not yet have them.
     *
     * @return count of new outreach rows created
     */
    public int syncOutreachRows() {
        List<CompanyOutreach> existing = outreachPort.findAll();
        Set<String> existingKeys = new HashSet<>();
        for (CompanyOutreach o : existing) {
            existingKeys.add(o.slug() + ":" + o.purpose().name());
        }

        List<HealthcareAiCompany> companies = companyPort.findAll();
        int created = 0;
        Instant now = Instant.now();

        for (HealthcareAiCompany company : companies) {
            String slug = company.companyId();
            if (slug == null || slug.isBlank()) {
                continue;
            }
            for (OutreachPurpose purpose : SYNCED_PURPOSES) {
                String key = slug + ":" + purpose.name();
                if (!existingKeys.contains(key)) {
                    outreachPort.save(new CompanyOutreach(
                            null, slug, purpose, OutreachStatus.NOT_STARTED,
                            null, null, null, null, now, now));
                    existingKeys.add(key);
                    created++;
                }
            }
        }

        return created;
    }
}
