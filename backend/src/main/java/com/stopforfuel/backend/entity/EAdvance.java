package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "e_advance", indexes = {
    @Index(name = "idx_e_adv_shift_id", columnList = "shift_id"),
    @Index(name = "idx_e_adv_advance_type", columnList = "advance_type"),
    @Index(name = "idx_e_adv_transaction_date", columnList = "transaction_date")
})
@Getter
@Setter
public class EAdvance extends BaseEntity {

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "advance_type", nullable = false)
    private String advanceType; // CARD, UPI, CHEQUE, CCMS, BANK_TRANSFER

    @Column(name = "remarks")
    private String remarks;

    // --- Card-specific fields ---
    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "tid")
    private String tid;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "card_last4_digit")
    private String cardLast4Digit;

    // --- Shared: Card, Cheque, Bank ---
    @Column(name = "bank_name")
    private String bankName;

    // --- Cheque-specific fields ---
    @Column(name = "cheque_no")
    private String chequeNo;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "in_favor_of")
    private String inFavorOf;

    // --- CCMS-specific field ---
    @Column(name = "ccms_number")
    private String ccmsNumber;

    // --- UPI-specific field ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upi_company_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private UpiCompany upiCompany;

    // --- Source references (auto-linked when created from invoice/payment) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "products", "statement", "operationalAdvance"})
    private InvoiceBill invoiceBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "statement", "invoiceBill"})
    private Payment payment;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.transactionDate == null) {
            this.transactionDate = LocalDateTime.now();
        }
    }
}
