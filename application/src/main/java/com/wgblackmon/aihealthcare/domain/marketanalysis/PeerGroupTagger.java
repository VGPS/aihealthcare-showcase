package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Domain utility that assigns {@link PeerGroup} values to {@link AffectedCompany}
 * instances based on keyword matching against company names and ticker symbols.
 *
 * <p>The keyword lists are hardcoded for the six AI-healthcare peer groups defined
 * in {@link PeerGroup}. Matching is case-insensitive and uses substring search.
 * First matching group wins; if no keyword matches, {@link PeerGroup#OTHER} is
 * returned. Companies already tagged with a non-OTHER peer group are passed through
 * unchanged.
 *
 * <p>Pure Java — no Spring or Lombok. Instantiated as a static field inside
 * {@link MarketDigestService} so no Spring bean wiring is required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public class PeerGroupTagger {

    private final Map<PeerGroup, List<String>> keywordMap;

    public PeerGroupTagger() {
        keywordMap = new HashMap<>();

        List<String> scribe = new ArrayList<>();
        scribe.add("scribe");
        scribe.add("ambient documentation");
        scribe.add("clinical documentation");
        scribe.add("doximity");
        scribe.add("nuance");
        scribe.add("abridge");
        scribe.add("nabla");
        scribe.add("suki");
        scribe.add("chartnote");
        scribe.add("deepscribe");
        scribe.add("augmedix");
        scribe.add("chartswap");
        keywordMap.put(PeerGroup.AI_SCRIBE_DOCUMENTATION, scribe);

        List<String> vbc = new ArrayList<>();
        vbc.add("value-based care");
        vbc.add("value based care");
        vbc.add("population health");
        vbc.add("risk adjustment");
        vbc.add("care management");
        vbc.add("privia");
        vbc.add("evolent");
        vbc.add("healthspring");
        vbc.add("bright health");
        vbc.add("clarify health");
        vbc.add("arcadia");
        keywordMap.put(PeerGroup.VALUE_BASED_CARE_PLATFORM, vbc);

        List<String> imaging = new ArrayList<>();
        imaging.add("imaging");
        imaging.add("radiology");
        imaging.add("pathology");
        imaging.add("diagnostic ai");
        imaging.add("aidoc");
        imaging.add("viz.ai");
        imaging.add("rad ai");
        imaging.add("icad");
        imaging.add("nanox");
        imaging.add("behold.ai");
        imaging.add("arterys");
        imaging.add("enlitic");
        imaging.add("intelerad");
        keywordMap.put(PeerGroup.DIAGNOSTIC_IMAGING_AI, imaging);

        List<String> drug = new ArrayList<>();
        drug.add("drug discovery");
        drug.add("molecular design");
        drug.add("protein folding");
        drug.add("genomics");
        drug.add("exscientia");
        drug.add("insilico");
        drug.add("recursion");
        drug.add("relay therapeutics");
        drug.add("schrodinger");
        drug.add("benevolentai");
        drug.add("atomwise");
        keywordMap.put(PeerGroup.DRUG_DISCOVERY_AI, drug);

        List<String> dTx = new ArrayList<>();
        dTx.add("digital therapeutic");
        dTx.add("dtx");
        dTx.add("mental health app");
        dTx.add("sleep therapy");
        dTx.add("akili");
        dTx.add("biofourmis");
        dTx.add("pear therapeutics");
        dTx.add("happify");
        dTx.add("headspace health");
        dTx.add("calm health");
        dTx.add("omada");
        dTx.add("noom");
        keywordMap.put(PeerGroup.DIGITAL_THERAPEUTICS, dTx);
    }

    /**
     * Returns the best-matching {@link PeerGroup} for the given company name and ticker.
     *
     * <p>If neither the name nor the ticker matches any known keyword, returns
     * {@link PeerGroup#OTHER}.
     */
    public PeerGroup tag(String companyName, String tickerSymbol) {
        String normalized = buildNormalized(companyName, tickerSymbol);
        for (PeerGroup group : PeerGroup.values()) {
            if (group == PeerGroup.OTHER) {
                continue;
            }
            List<String> keywords = keywordMap.get(group);
            if (keywords == null) {
                continue;
            }
            for (String keyword : keywords) {
                if (normalized.contains(keyword)) {
                    return group;
                }
            }
        }
        return PeerGroup.OTHER;
    }

    /**
     * Returns a new {@link AffectedCompany} with the peer group assigned by keyword
     * matching. If the company already has a non-OTHER peer group, it is returned as-is.
     */
    public AffectedCompany tagCompany(AffectedCompany company) {
        if (company.peerGroup() != null && company.peerGroup() != PeerGroup.OTHER) {
            return company;
        }
        PeerGroup tagged = tag(company.name(), company.tickerSymbol());
        if (tagged == company.peerGroup()) {
            return company;
        }
        return new AffectedCompany(company.name(), company.tickerSymbol(), company.role(), tagged);
    }

    /**
     * Returns a new {@link MarketDigestEntry} with all affected companies re-tagged.
     */
    public MarketDigestEntry tagEntry(MarketDigestEntry entry) {
        List<AffectedCompany> tagged = new ArrayList<>();
        for (AffectedCompany company : entry.affectedCompanies()) {
            tagged.add(tagCompany(company));
        }
        return new MarketDigestEntry(
                entry.newsItem(),
                entry.impactAssessments(),
                entry.factClassification(),
                entry.rank(),
                tagged
        );
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private String buildNormalized(String name, String ticker) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name.toLowerCase(Locale.ROOT));
        }
        if (ticker != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(ticker.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
