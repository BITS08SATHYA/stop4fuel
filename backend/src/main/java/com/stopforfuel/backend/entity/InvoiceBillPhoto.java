package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_bill_photo", indexes = {
    @Index(name = "idx_invoice_bill_photo_invoice_id", columnList = "invoice_bill_id"),
    @Index(name = "idx_invoice_bill_photo_type", columnList = "invoice_bill_id, photo_type")
})
@Getter
@Setter
public class InvoiceBillPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_bill_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InvoiceBill invoiceBill;

    @Column(name = "photo_type", nullable = false, length = 30)
    private String photoType; // bill-pic, indent-pic, pump-bill-pic

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "ocr_verified")
    private Boolean ocrVerified;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
