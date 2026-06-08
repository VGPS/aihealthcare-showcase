package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java domain service that deduplicates a list of companies by
 * normalized name and (optionally) company-site domain.
 *
 * <p>When two companies share the same normalized name or domain, the
 * record with the longer description is kept. If both descriptions are
 * equal length, the record with a non-null {@code companySite} wins.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public class CompanyDeduplicator {

    /**
     * Deduplicates companies by normalized name. When a company-site URL
     * is available, its domain is also used as a secondary dedup key.
     *
     * @param companies the raw list (may contain duplicates)
     * @return a new list with duplicates removed, insertion order preserved
     */
    public List<Company> dedupe(List<Company> companies) {
        Map<String, Company> seen = new LinkedHashMap<>();

        for (Company company : companies) {
            String nameKey = normalize(company.name());
            String domainKey = extractDomain(company.companySite());

            String matchKey = findExistingKey(seen, nameKey, domainKey);

            if (matchKey == null) {
                // New company — register under name key
                seen.put(nameKey, company);
            } else {
                // Duplicate — keep the better record
                Company existing = seen.get(matchKey);
                if (isBetter(company, existing)) {
                    seen.put(matchKey, company);
                }
            }
        }

        return new ArrayList<>(seen.values());
    }

    private String findExistingKey(Map<String, Company> seen, String nameKey, String domainKey) {
        if (seen.containsKey(nameKey)) {
            return nameKey;
        }
        if (domainKey != null) {
            for (Map.Entry<String, Company> entry : seen.entrySet()) {
                String existingDomain = extractDomain(entry.getValue().companySite());
                if (domainKey.equals(existingDomain)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean isBetter(Company candidate, Company existing) {
        int candidateLen = candidate.description() != null ? candidate.description().length() : 0;
        int existingLen = existing.description() != null ? existing.description().length() : 0;

        if (candidateLen != existingLen) {
            return candidateLen > existingLen;
        }
        // Prefer entry with companySite
        boolean candidateHasSite = candidate.companySite() != null && !candidate.companySite().isBlank();
        boolean existingHasSite = existing.companySite() != null && !existing.companySite().isBlank();
        return candidateHasSite && !existingHasSite;
    }

    private String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String extractDomain(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            // Strip leading "www."
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
