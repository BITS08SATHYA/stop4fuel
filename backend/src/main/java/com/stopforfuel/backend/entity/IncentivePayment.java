package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "incentive_payment", indexes = {
    @Index(name = "idx_incpay_shift_id", columnList = "shift_id"),
    @Index(name = "idx_incpay_customer_id", columnList = "customer_id"),
    @Index(name = "idx_incpay_invoice_id", columnList = "invoice_bill_id"),
    @Index(name = "idx_incpay_date", columnList = "payment_date")
})
@Getter
@Setter
public class IncentivePayment extends BaseEntity {

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    // Resolved payee name captured at creation time. For walk-in CASH discounts
    // there is no linked Customer entity (the name lives only on the invoice's
    // signatory), so this lets the report show the name without a relation.
    @Column(name = "customer_name")
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InvoiceBill invoiceBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Statement statement;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
    }
}
