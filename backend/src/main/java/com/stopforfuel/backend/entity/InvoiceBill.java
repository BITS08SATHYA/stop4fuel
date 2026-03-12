package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice_bill")
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

    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "total_discount", precision = 19, scale = 4)
    private BigDecimal totalDiscount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "bill_type", nullable = false)
    private String billType; // CASH, CREDIT

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

    @Column(name = "payment_status")
    private String paymentStatus; // PAID, NOT_PAID (for credit bills)

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
            this.paymentStatus = "CREDIT".equals(this.billType) ? "NOT_PAID" : "PAID";
        }
    }
}
