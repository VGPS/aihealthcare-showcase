package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence.CorporateActionConfirmationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AlpacaCorporateActionsAdapter}.
 *
 * <p>Tests credentials-guarding and the ticker-collection and action-flattening
 * logic via package-private accessors — avoiding RestClient chain mocks, which
 * are brittle against Spring's fluent API generics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class AlpacaCorporateActionsAdapterTest {

    private static final String KEY    = "PKTEST123";
    private static final String SECRET = "supersecret";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Mock private CorporateActionConfirmationAdapter confirmationAdapter;

    // ─── missing credentials — guard path ────────────────────────────────────

    @Test
    void missingApiKey_returnsWithoutCallingAdapter() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter("", SECRET, confirmationAdapter, mock(RestClient.class));
        adapter.attachConfirmations(emptyDigest(), TODAY);
        verify(confirmationAdapter, never()).save(any());
    }

    @Test
    void missingSecretKey_returnsWithoutCallingAdapter() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, "", confirmationAdapter, mock(RestClient.class));
        adapter.attachConfirmations(emptyDigest(), TODAY);
        verify(confirmationAdapter, never()).save(any());
    }

    @Test
    void digestWithNoAffectedCompanyTickers_skipsApiCall() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        adapter.attachConfirmations(digestWithEntry(null), TODAY);
        verify(confirmationAdapter, never()).save(any());
    }

    // ─── collectTickers ───────────────────────────────────────────────────────

    @Test
    void collectTickers_extractsTickersFromAffectedCompanies() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        MarketDigest digest = digestWithEntry("DOCS");
        Set<String> tickers = adapter.collectTickers(digest);
        assertThat(tickers).containsExactly("DOCS");
    }

    @Test
    void collectTickers_nullTickerIsIgnored() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        Set<String> tickers = adapter.collectTickers(digestWithEntry(null));
        assertThat(tickers).isEmpty();
    }

    @Test
    void collectTickers_upperCasesSymbols() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        MarketDigest digest = digestWithEntry("docs");
        Set<String> tickers = adapter.collectTickers(digest);
        assertThat(tickers).containsExactly("DOCS");
    }

    @Test
    void collectTickers_dedupesIdenticalTickers() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));

        MarketDigest digest = digestWithTwoEntries("DOCS", "DOCS");
        Set<String> tickers = adapter.collectTickers(digest);
        assertThat(tickers).containsExactlyInAnyOrder("DOCS");
    }

    // ─── flattenActions ───────────────────────────────────────────────────────

    @Test
    void flattenActions_nullLists_returnsEmpty() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        AlpacaCorporateActionsResponse.ActionsByType byType =
                new AlpacaCorporateActionsResponse.ActionsByType(
                        null, null, null, null, null, null, null, null);
        List<AlpacaCorporateActionsAdapter.ActionEntry> result = adapter.flattenActions(byType);
        assertThat(result).isEmpty();
    }

    @Test
    void flattenActions_cashDividend_mappedWithCorrectType() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        AlpacaCorporateActionsResponse.ActionItem item =
                new AlpacaCorporateActionsResponse.ActionItem("DOCS", TODAY, TODAY, TODAY, TODAY);
        AlpacaCorporateActionsResponse.ActionsByType byType =
                new AlpacaCorporateActionsResponse.ActionsByType(
                        List.of(item), null, null, null, null, null, null, null);

        List<AlpacaCorporateActionsAdapter.ActionEntry> result = adapter.flattenActions(byType);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("cash_dividend");
        assertThat(result.get(0).item().symbol()).isEqualTo("DOCS");
    }

    @Test
    void flattenActions_cashMerger_mappedWithCorrectType() {
        AlpacaCorporateActionsAdapter adapter =
                new AlpacaCorporateActionsAdapter(KEY, SECRET, confirmationAdapter, mock(RestClient.class));
        AlpacaCorporateActionsResponse.ActionItem item =
                new AlpacaCorporateActionsResponse.ActionItem("EVH", TODAY, null, null, null);
        AlpacaCorporateActionsResponse.ActionsByType byType =
                new AlpacaCorporateActionsResponse.ActionsByType(
                        null, null, List.of(item), null, null, null, null, null);

        List<AlpacaCorporateActionsAdapter.ActionEntry> result = adapter.flattenActions(byType);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("cash_merger");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketDigest emptyDigest() {
        return new MarketDigest(TODAY, List.of(), Instant.now());
    }

    private MarketDigest digestWithEntry(String tickerSymbol) {
        return new MarketDigest(TODAY, List.of(makeEntry(tickerSymbol)), Instant.now());
    }

    private MarketDigest digestWithTwoEntries(String ticker1, String ticker2) {
        return new MarketDigest(TODAY, List.of(makeEntry(ticker1), makeEntry(ticker2)), Instant.now());
    }

    private MarketDigestEntry makeEntry(String tickerSymbol) {
        AffectedCompany company = new AffectedCompany(
                "Doximity", tickerSymbol, "subject", PeerGroup.AI_SCRIBE_DOCUMENTATION);
        MarketNewsItem item = new MarketNewsItem(
                "DOCS Q2 Beat", "Doximity beat earnings.", List.of("https://example.com"),
                Instant.now(), NewsCategory.EARNINGS, null);
        return new MarketDigestEntry(
                item,
                List.of(new ImpactAssessment(ImpactDimension.REVENUE, ImpactDirection.POSITIVE, "Strong")),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of(company));
    }
}
