package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA entity for the {@code corporate_action_confirmation} table.
 *
 * <p>Stores Alpaca Corporate Actions API matches against a market digest date.
 * Keyed by a UUID {@code confirmation_id}; linked to the digest by {@code digest_date}
 * and {@code ticker_symbol} (no JPA {@code @ManyToOne} — FK managed manually).
 *
 * <p>{@code ex_date}, {@code record_date}, and {@code payable_date} are nullable
 * because Alpaca's response omits them for certain action types (e.g. mergers).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "corporate_action_confirmation")
public class CorporateActionConfirmationEntity {

    @Id
    @Column(name = "confirmation_id", nullable = false, length = 36)
    private String confirmationId;

    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @Column(name = "ticker_symbol", nullable = false, length = 16)
    private String tickerSymbol;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "declaration_date", nullable = false)
    private LocalDate declarationDate;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "payable_date")
    private LocalDate payableDate;

    protected CorporateActionConfirmationEntity() {}

    public CorporateActionConfirmationEntity(String confirmationId, LocalDate digestDate,
                                              String tickerSymbol, String actionType,
                                              LocalDate declarationDate, LocalDate exDate,
                                              LocalDate recordDate, LocalDate payableDate) {
        this.confirmationId  = confirmationId;
        this.digestDate      = digestDate;
        this.tickerSymbol    = tickerSymbol;
        this.actionType      = actionType;
        this.declarationDate = declarationDate;
        this.exDate          = exDate;
        this.recordDate      = recordDate;
        this.payableDate     = payableDate;
    }

    public String    getConfirmationId()  { return confirmationId; }
    public LocalDate getDigestDate()      { return digestDate; }
    public String    getTickerSymbol()    { return tickerSymbol; }
    public String    getActionType()      { return actionType; }
    public LocalDate getDeclarationDate() { return declarationDate; }
    public LocalDate getExDate()          { return exDate; }
    public LocalDate getRecordDate()      { return recordDate; }
    public LocalDate getPayableDate()     { return payableDate; }
}
