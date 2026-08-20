package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.CorporateActionConfirmation;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.CorporateActionPort;
import com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence.CorporateActionConfirmationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Alpaca Corporate Actions API adapter implementing {@link CorporateActionPort}.
 *
 * <p>After the daily digest is saved, this adapter is called with the completed
 * digest and its date. It:
 * <ol>
 *   <li>Collects all unique ticker symbols from affected companies across all entries.</li>
 *   <li>Calls {@code GET /v1/corporate-actions} on the Alpaca Data API for those
 *       tickers in a ±{@value #DATE_WINDOW}-day window around the digest date.</li>
 *   <li>For each action item whose symbol matches a tracked ticker, saves a
 *       {@link CorporateActionConfirmation} linked by digest date + ticker.</li>
 * </ol>
 *
 * <p>Uses the same Alpaca API key pair as {@link AlpacaMarketDataAdapter}.
 * Returns silently (no exception) on any API or parsing failure — corporate action
 * confirmation is enrichment only, never required for the core digest pipeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class AlpacaCorporateActionsAdapter implements CorporateActionPort {

    private static final String BASE_URL    = "https://data.alpaca.markets";
    private static final int    DATE_WINDOW = 3;

    private final String                            apiKey;
    private final String                            secretKey;
    private final RestClient                        restClient;
    private final CorporateActionConfirmationAdapter confirmationAdapter;

    @Autowired
    public AlpacaCorporateActionsAdapter(
            @Value("${aihealthcare.alpaca.api-key:}") String apiKey,
            @Value("${aihealthcare.alpaca.secret-key:}") String secretKey,
            CorporateActionConfirmationAdapter confirmationAdapter) {
        this(apiKey, secretKey, confirmationAdapter,
                RestClient.builder().baseUrl(BASE_URL).build());
    }

    /** Package-private for testing — accepts a pre-built RestClient. */
    AlpacaCorporateActionsAdapter(String apiKey, String secretKey,
                                   CorporateActionConfirmationAdapter confirmationAdapter,
                                   RestClient restClient) {
        log.debug("AlpacaCorporateActionsAdapter() | apiKeyPresent={}, secretKeyPresent={}",
                apiKey != null && !apiKey.isBlank(), secretKey != null && !secretKey.isBlank());
        this.apiKey              = apiKey;
        this.secretKey           = secretKey;
        this.confirmationAdapter = confirmationAdapter;
        this.restClient          = restClient;
        if (!keysPresent()) {
            log.warn("AlpacaCorporateActionsAdapter() | Alpaca keys absent — corporate action confirmation disabled");
        }
        log.debug("AlpacaCorporateActionsAdapter() | return=void");
    }

    @Override
    public void attachConfirmations(MarketDigest digest, LocalDate queryDate) {
        log.debug("attachConfirmations() | date={}, entries={}", queryDate, digest.entries().size());

        if (!keysPresent()) {
            log.debug("attachConfirmations() | return=void (no credentials)");
            return;
        }

        Set<String> tickers = collectTickers(digest);
        if (tickers.isEmpty()) {
            log.debug("attachConfirmations() | return=void (no tickers in affected companies)");
            return;
        }

        String symbolsParam = String.join(",", tickers);
        LocalDate since = queryDate.minusDays(DATE_WINDOW);
        LocalDate until = queryDate.plusDays(DATE_WINDOW);

        AlpacaCorporateActionsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/corporate-actions")
                            .queryParam("symbols", symbolsParam)
                            .queryParam("since", since.toString())
                            .queryParam("until", until.toString())
                            .queryParam("ca_types", "cash_dividends,stock_dividends,cash_mergers,"
                                    + "stock_mergers,stock_and_cash_mergers,spinoffs,forward_splits,reverse_splits")
                            .build())
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .body(AlpacaCorporateActionsResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("attachConfirmations() | HTTP {} from Alpaca Corporate Actions: {}",
                    e.getStatusCode(), e.getMessage());
            log.debug("attachConfirmations() | return=void");
            return;
        } catch (Exception e) {
            log.warn("attachConfirmations() | Alpaca Corporate Actions error (non-fatal): {}", e.getMessage());
            log.debug("attachConfirmations() | return=void");
            return;
        }

        if (response == null || response.corporateActions() == null) {
            log.info("attachConfirmations() | empty response from Alpaca for date={}", queryDate);
            log.debug("attachConfirmations() | return=void");
            return;
        }

        List<ActionEntry> actions = flattenActions(response.corporateActions());
        int saved = 0;
        for (ActionEntry action : actions) {
            if (action.item().symbol() == null || action.item().symbol().isBlank()) continue;
            String symbol = action.item().symbol().toUpperCase();
            if (!tickers.contains(symbol)) continue;

            LocalDate declDate = action.item().declarationDate() != null
                    ? action.item().declarationDate()
                    : queryDate;

            CorporateActionConfirmation confirmation = new CorporateActionConfirmation(
                    UUID.randomUUID().toString(),
                    queryDate,
                    symbol,
                    action.type(),
                    declDate,
                    action.item().exDate(),
                    action.item().recordDate(),
                    action.item().payableDate()
            );
            try {
                confirmationAdapter.save(confirmation);
                saved++;
            } catch (Exception e) {
                log.warn("attachConfirmations() | failed to save confirmation for ticker={}: {}",
                        symbol, e.getMessage());
            }
        }

        log.info("attachConfirmations() | saved {} corporate action confirmations for date={}", saved, queryDate);
        log.debug("attachConfirmations() | return=void");
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private boolean keysPresent() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /** Package-private for testing. */
    Set<String> collectTickers(MarketDigest digest) {
        Set<String> tickers = new HashSet<>();
        for (MarketDigestEntry entry : digest.entries()) {
            for (AffectedCompany company : entry.affectedCompanies()) {
                if (company.tickerSymbol() != null && !company.tickerSymbol().isBlank()) {
                    tickers.add(company.tickerSymbol().toUpperCase());
                }
            }
        }
        return tickers;
    }

    /** Package-private for testing. */
    List<ActionEntry> flattenActions(AlpacaCorporateActionsResponse.ActionsByType byType) {
        List<ActionEntry> result = new ArrayList<>();
        addAll(result, byType.cashDividends(),       "cash_dividend");
        addAll(result, byType.stockDividends(),      "stock_dividend");
        addAll(result, byType.cashMergers(),         "cash_merger");
        addAll(result, byType.stockMergers(),        "stock_merger");
        addAll(result, byType.stockAndCashMergers(), "stock_and_cash_merger");
        addAll(result, byType.spinoffs(),            "spinoff");
        addAll(result, byType.forwardSplits(),       "forward_split");
        addAll(result, byType.reverseSplits(),       "reverse_split");
        return result;
    }

    private void addAll(List<ActionEntry> dest,
                        List<AlpacaCorporateActionsResponse.ActionItem> src,
                        String type) {
        if (src == null) return;
        for (AlpacaCorporateActionsResponse.ActionItem item : src) {
            dest.add(new ActionEntry(type, item));
        }
    }

    /** Internal pairing of a resolved action type string with its parsed item. */
    record ActionEntry(String type, AlpacaCorporateActionsResponse.ActionItem item) {}
}
