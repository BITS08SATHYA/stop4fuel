package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.PaytmTxnStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paytm_transaction", indexes = {
    @Index(name = "idx_paytm_txn_merchant_txn_id", columnList = "merchant_txn_id", unique = true),
    @Index(name = "idx_paytm_txn_cpay_id", columnList = "cpay_id"),
    @Index(name = "idx_paytm_txn_status", columnList = "status"),
    @Index(name = "idx_paytm_txn_invoice_bill_id", columnList = "invoice_bill_id"),
    @Index(name = "idx_paytm_txn_statement_id", columnList = "statement_id")
})
@Getter
@Setter
public class PaytmTransaction extends BaseEntity {

    @Column(name = "merchant_txn_id", nullable = false, unique = true)
    private String merchantTxnId;

    @Column(name = "cpay_id")
    private String cpayId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "amount_in_paisa")
    private String amountInPaisa;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaytmTxnStatus status;

    /** CASH_INVOICE or CREDIT_PAYMENT */
    @Column(name = "txn_type", nullable = false)
    private String txnType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InvoiceBill invoiceBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Statement statement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User initiatedBy;

    // --- Paytm callback response fields ---

    @Column(name = "paytm_txn_id")
    private String paytmTxnId;

    @Column(name = "bank_txn_id")
    private String bankTxnId;

    @Column(name = "paytm_payment_mode")
    private String paytmPaymentMode;

    @Column(name = "paytm_status")
    private String paytmStatus;

    @Column(name = "paytm_resp_code")
    private String paytmRespCode;

    @Column(name = "paytm_resp_msg")
    private String paytmRespMsg;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;
}
