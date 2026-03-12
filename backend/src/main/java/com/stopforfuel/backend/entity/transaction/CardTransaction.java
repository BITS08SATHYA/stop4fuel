package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("CARD")
@Getter @Setter
public class CardTransaction extends ShiftTransaction {
    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "tid")
    private String tid;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "card_last4_digit")
    private String cardLast4Digit;
}
