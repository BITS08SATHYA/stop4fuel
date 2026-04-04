package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment", indexes = {
    @Index(name = "idx_payment_shift_id", columnList = "shift_id"),
    @Index(name = "idx_payment_customer_id", columnList = "customer_id"),
    @Index(name = "idx_payment_statement_id", columnList = "statement_id"),
    @Index(name = "idx_payment_invoice_bill_id", columnList = "invoice_bill_id"),
    @Index(name = "idx_payment_scid", columnList = "scid")
})
@Getter
@Setter
public class Payment extends BaseEntity {

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be positive")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull(message = "Payment mode is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private com.stopforfuel.backend.enums.PaymentMode paymentMode;

    @Column(name = "reference_no")
    private String referenceNo; // cheque no, UTR, UPI ref

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Statement statement; // nullable — set for statement customer payments

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InvoiceBill invoiceBill; // nullable — set for local customer bill payments

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "proof_image_key")
    private String proofImageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User receivedBy; // Employee who collected this payment

    /**
     * Computed field: payment status of the linked bill/statement.
     * Returns PAID, PARTIAL, or NOT_PAID.
     */
    @Transient
    public String getTargetPaymentStatus() {
        if (statement != null) {
            if ("PAID".equals(statement.getStatus())) return "PAID";
            if (statement.getReceivedAmount() != null
                    && statement.getReceivedAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                return "PARTIAL";
            }
            return "NOT_PAID";
        }
        if (invoiceBill != null) {
            if (com.stopforfuel.backend.enums.PaymentStatus.PAID.equals(invoiceBill.getPaymentStatus())) return "PAID";
            return "NOT_PAID";
        }
        return null;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
    }
}
