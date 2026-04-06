package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.MatchConfidence;
import com.stopforfuel.backend.enums.ReconStatus;
import com.stopforfuel.backend.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paytm_transaction",
    uniqueConstraints = @UniqueConstraint(name = "uk_paytm_txn_scid_order", columnNames = {"scid", "paytm_order_id"}),
    indexes = {
        @Index(name = "idx_paytm_txn_date", columnList = "scid, txn_date"),
        @Index(name = "idx_paytm_recon_status", columnList = "scid, recon_status"),
        @Index(name = "idx_paytm_settlement_status", columnList = "scid, settlement_status"),
        @Index(name = "idx_paytm_matched_invoice", columnList = "matched_invoice_id")
    })
@Getter
@Setter
public class PaytmTransaction extends BaseEntity {

    @Column(name = "paytm_order_id", nullable = false)
    private String paytmOrderId;

    @Column(name = "paytm_txn_id")
    private String paytmTxnId;

    @Column(name = "txn_date")
    private LocalDateTime txnDate;

    @Column(name = "txn_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal txnAmount;

    @Column(name = "txn_status")
    private String txnStatus; // TXN_SUCCESS, TXN_FAILURE, PENDING

    @Column(name = "paytm_payment_mode")
    private String paytmPaymentMode; // CC, DC, UPI, PPI, NB (PayTM's sub-type)

    @Column(name = "gateway_name")
    private String gatewayName;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_txn_id")
    private String bankTxnId;

    @Column(name = "currency")
    private String currency = "INR";

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "response_message")
    private String responseMessage;

    // Settlement fields
    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "settlement_amount", precision = 19, scale = 4)
    private BigDecimal settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status")
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    // Reconciliation fields
    @Enumerated(EnumType.STRING)
    @Column(name = "recon_status", nullable = false)
    private ReconStatus reconStatus = ReconStatus.UNMATCHED;

    @Column(name = "matched_invoice_id")
    private Long matchedInvoiceId;

    @Column(name = "matched_e_advance_id")
    private Long matchedEAdvanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_confidence")
    private MatchConfidence matchConfidence;

    @Column(name = "recon_note")
    private String reconNote;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;
}
