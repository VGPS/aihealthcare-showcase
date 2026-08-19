package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.marketdata;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceBar;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceHistory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.Quote;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alpaca Markets adapter implementing {@link MarketDataPort}.
 *
 * <p>Uses two Alpaca Data API v2 endpoints:
 * <ul>
 *   <li>{@code GET /v2/stocks/snapshots?symbols=TICKER} — latest quote + daily bar</li>
 *   <li>{@code GET /v2/stocks/{symbol}/bars?timeframe=1Day&start=...&end=...} — historical OHLCV bars</li>
 * </ul>
 *
 * <p>The Alpaca free tier ("paper trading") API key is sufficient for both endpoints.
 * When {@code ALPACA_API_KEY} or {@code ALPACA_SECRET_KEY} is absent, both methods
 * return {@code Optional.empty()} rather than throwing — callers treat missing market
 * data as non-fatal.
 *
 * <p>Market cap is not available via the Alpaca Data API; the {@code marketCap} field
 * of every returned {@link Quote} is {@code null}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class AlpacaMarketDataAdapter implements MarketDataPort {

    private static final String BASE_URL = "https://data.alpaca.markets";

    private final String     apiKey;
    private final String     secretKey;
    private final RestClient restClient;

    public AlpacaMarketDataAdapter(
            @Value("${aihealthcare.alpaca.api-key:}") String apiKey,
            @Value("${aihealthcare.alpaca.secret-key:}") String secretKey) {
        this(apiKey, secretKey, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /** Package-private for testing — accepts a pre-built RestClient. */
    AlpacaMarketDataAdapter(String apiKey, String secretKey, RestClient restClient) {
        log.debug("AlpacaMarketDataAdapter() | apiKeyPresent={}, secretKeyPresent={}",
                apiKey != null && !apiKey.isBlank(), secretKey != null && !secretKey.isBlank());
        this.apiKey     = apiKey;
        this.secretKey  = secretKey;
        this.restClient = restClient;
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("AlpacaMarketDataAdapter() | ALPACA_API_KEY or ALPACA_SECRET_KEY not set — market data disabled");
        }
        log.debug("AlpacaMarketDataAdapter() | return=void");
    }

    @Override
    public Optional<Quote> getQuote(String tickerSymbol) {
        log.debug("getQuote() | tickerSymbol={}", tickerSymbol);

        if (!keysPresent()) {
            log.debug("getQuote() | return=empty (no credentials)");
            return Optional.empty();
        }

        try {
            Map<String, AlpacaQuoteResponse> response = restClient.get()
                    .uri("/v2/stocks/snapshots?symbols={symbol}&feed=sip", tickerSymbol)
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            if (response == null || !response.containsKey(tickerSymbol)) {
                log.warn("getQuote() | no snapshot for ticker={}", tickerSymbol);
                log.debug("getQuote() | return=empty");
                return Optional.empty();
            }

            AlpacaQuoteResponse snapshot = response.get(tickerSymbol);
            Quote quote = toQuote(tickerSymbol, snapshot);
            if (quote == null) {
                log.debug("getQuote() | return=empty (insufficient data)");
                return Optional.empty();
            }

            log.debug("getQuote() | return={}", quote);
            return Optional.of(quote);

        } catch (HttpClientErrorException e) {
            log.warn("getQuote() | HTTP {} for ticker={}: {}", e.getStatusCode(), tickerSymbol, e.getMessage());
            log.debug("getQuote() | return=empty");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("getQuote() | error fetching quote for ticker={}: {}", tickerSymbol, e.getMessage());
            log.debug("getQuote() | return=empty");
            return Optional.empty();
        }
    }

    @Override
    public Optional<PriceHistory> getPriceHistory(String tickerSymbol, LocalDate start, LocalDate end) {
        log.debug("getPriceHistory() | ticker={}, start={}, end={}", tickerSymbol, start, end);

        if (!keysPresent()) {
            log.debug("getPriceHistory() | return=empty (no credentials)");
            return Optional.empty();
        }

        try {
            String startStr = start.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            String endStr   = end.atStartOfDay(ZoneOffset.UTC).toInstant().toString();

            AlpacaBarsResponse response = restClient.get()
                    .uri("/v2/stocks/{symbol}/bars?timeframe=1Day&start={start}&end={end}",
                            tickerSymbol, startStr, endStr)
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .body(AlpacaBarsResponse.class);

            if (response == null || response.bars() == null || response.bars().isEmpty()) {
                log.warn("getPriceHistory() | no bars for ticker={}", tickerSymbol);
                log.debug("getPriceHistory() | return=empty");
                return Optional.empty();
            }

            List<PriceBar> bars = new ArrayList<>();
            for (AlpacaBarsResponse.AlpacaBar bar : response.bars()) {
                LocalDate barDate = bar.timestamp() != null
                        ? bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate()
                        : start;
                bars.add(new PriceBar(barDate, bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()));
            }

            PriceHistory result = new PriceHistory(tickerSymbol, bars);
            log.debug("getPriceHistory() | return={}", result);
            return Optional.of(result);

        } catch (HttpClientErrorException e) {
            log.warn("getPriceHistory() | HTTP {} for ticker={}: {}", e.getStatusCode(), tickerSymbol, e.getMessage());
            log.debug("getPriceHistory() | return=empty");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("getPriceHistory() | error fetching history for ticker={}: {}", tickerSymbol, e.getMessage());
            log.debug("getPriceHistory() | return=empty");
            return Optional.empty();
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private boolean keysPresent() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    Quote toQuote(String ticker, AlpacaQuoteResponse snapshot) {
        if (snapshot.latestTrade() == null || snapshot.latestTrade().price() == null) {
            log.warn("toQuote() | no latestTrade price for ticker={}", ticker);
            return null;
        }
        if (snapshot.prevDailyBar() == null || snapshot.prevDailyBar().close() == null) {
            log.warn("toQuote() | no prevDailyBar for ticker={}", ticker);
            return null;
        }

        BigDecimal price    = snapshot.latestTrade().price();
        BigDecimal prevClose = snapshot.prevDailyBar().close();

        BigDecimal changePercent = BigDecimal.ZERO;
        if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
            changePercent = price.subtract(prevClose)
                    .divide(prevClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new Quote(ticker, price, changePercent, null);
    }
}
