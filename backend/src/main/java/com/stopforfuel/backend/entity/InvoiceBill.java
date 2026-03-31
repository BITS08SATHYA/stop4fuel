package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice_bill", indexes = {
    @Index(name = "idx_invoice_bill_shift_id", columnList = "shift_id"),
    @Index(name = "idx_invoice_bill_customer_id", columnList = "customer_id"),
    @Index(name = "idx_invoice_bill_bill_type", columnList = "bill_type"),
    @Index(name = "idx_invoice_bill_payment_status", columnList = "payment_status"),
    @Index(name = "idx_invoice_bill_bill_date", columnList = "bill_date"),
    @Index(name = "idx_invoice_bill_statement_id", columnList = "statement_id"),
    @Index(name = "idx_invoice_bill_scid", columnList = "scid"),
    @Index(name = "idx_invoice_bill_cust_type_date", columnList = "customer_id, bill_type, bill_date")
})
@Getter
@Setter
public class InvoiceBill extends BaseEntity {

    @Column(name = "bill_date")
    private LocalDateTime date;

    @Column(name = "bill_desc")
    private String billDesc;

    @Column(name = "pump_bill_pic")
    private String pumpBillPic;

    @Column(name = "bill_pic")
    private String billPic;

    @Column(name = "signatory_name")
    private String signatoryName;

    @Column(name = "signatory_cell_no")
    private String signatoryCellNo;

    @Column(name = "vehicle_km")
    private Long vehicleKM;

    @Column(name = "reading_open")
    private Long readingOpen;

    @Column(name = "reading_close")
    private Long readingClose;

    @Column(name = "customer_gst")
    private String customerGST;

    @PositiveOrZero(message = "Gross amount must be zero or positive")
    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @PositiveOrZero(message = "Total discount must be zero or positive")
    @Column(name = "total_discount", precision = 19, scale = 4)
    private BigDecimal totalDiscount;

    @PositiveOrZero(message = "Net amount must be zero or positive")
    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "bill_no")
    private String billNo; // e.g., "C26/34", "A26/51"

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false)
    private BillType billType;

    @Column(name = "payment_mode")
    private String paymentMode; // CASH, CARD, UPI, etc.

    @Column(name = "indent_no")
    private String indentNo;

    @Column(name = "indent_pic")
    private String indentPic;

    @Column(name = "bill_status")
    private String status; // PENDING, PAID, CANCELLED

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_phone")
    private String driverPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Statement statement; // nullable — linked when bill is added to a statement

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_advance_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "invoiceBills"})
    private OperationalAdvance operationalAdvance;

    @OneToMany(mappedBy = "invoiceBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceProduct> products = new ArrayList<>();

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.date == null) {
            this.date = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "PAID"; // Default status
        }
        if (this.paymentStatus == null) {
            // Cash bills are paid immediately; credit bills start as NOT_PAID
            this.paymentStatus = BillType.CREDIT.equals(this.billType) ? PaymentStatus.NOT_PAID : PaymentStatus.PAID;
        }
    }
}
