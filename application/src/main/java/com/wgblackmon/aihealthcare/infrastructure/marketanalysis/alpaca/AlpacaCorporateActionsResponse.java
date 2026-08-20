package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Jackson deserialization record for the Alpaca Corporate Actions API response.
 *
 * <p>Maps {@code GET /v1/corporate-actions} where the {@code corporate_actions}
 * object contains a key per action type, each holding a list of action items.
 * Only the action types most relevant to AI-healthcare market events are explicitly
 * mapped; {@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures future
 * Alpaca additions do not break parsing.
 *
 * <p>Each {@link ActionItem} captures the minimal common fields present across
 * action types. Not every field is populated for every type — callers should
 * treat all date fields except {@code declarationDate} as nullable.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaCorporateActionsResponse(
        @JsonProperty("corporate_actions") ActionsByType corporateActions,
        @JsonProperty("next_page_token") String nextPageToken
) {

    /**
     * Groups action items by type. All list fields default to null if the
     * corresponding key is absent in the response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionsByType(
            @JsonProperty("cash_dividends")         List<ActionItem> cashDividends,
            @JsonProperty("stock_dividends")        List<ActionItem> stockDividends,
            @JsonProperty("cash_mergers")           List<ActionItem> cashMergers,
            @JsonProperty("stock_mergers")          List<ActionItem> stockMergers,
            @JsonProperty("stock_and_cash_mergers") List<ActionItem> stockAndCashMergers,
            @JsonProperty("spinoffs")               List<ActionItem> spinoffs,
            @JsonProperty("forward_splits")         List<ActionItem> forwardSplits,
            @JsonProperty("reverse_splits")         List<ActionItem> reverseSplits
    ) {}

    /**
     * Single corporate action item. Fields vary by action type;
     * {@code declarationDate} is the most consistently present date field.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionItem(
            @JsonProperty("symbol")           String    symbol,
            @JsonProperty("declaration_date") LocalDate declarationDate,
            @JsonProperty("ex_date")          LocalDate exDate,
            @JsonProperty("record_date")      LocalDate recordDate,
            @JsonProperty("payable_date")     LocalDate payableDate
    ) {}
}
