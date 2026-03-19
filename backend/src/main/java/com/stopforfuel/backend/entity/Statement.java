package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "statement")
@Getter
@Setter
public class Statement extends BaseEntity {

    @Column(name = "statement_no", nullable = false, unique = true)
    private String statementNo; // e.g., "S26/12"

    @NotNull(message = "Customer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @NotNull(message = "From date is required")
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @NotNull(message = "To date is required")
    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "number_of_bills")
    private Integer numberOfBills;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "rounding_amount", precision = 19, scale = 4)
    private BigDecimal roundingAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "received_amount", precision = 19, scale = 4)
    private BigDecimal receivedAmount = BigDecimal.ZERO;

    @Column(name = "balance_amount", precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "status", nullable = false)
    private String status = "NOT_PAID"; // PAID, NOT_PAID

    @OneToMany(mappedBy = "statement")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<InvoiceBill> invoiceBills = new ArrayList<>();

    @OneToMany(mappedBy = "statement")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Payment> payments = new ArrayList<>();

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.statementDate == null) {
            this.statementDate = LocalDate.now();
        }
        if (this.balanceAmount == null) {
            this.balanceAmount = this.netAmount;
        }
    }
}
