package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_mode_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PaymentMode paymentMode;

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

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
    }
}
