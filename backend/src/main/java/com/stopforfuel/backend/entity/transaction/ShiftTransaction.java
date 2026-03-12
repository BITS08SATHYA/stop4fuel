package com.stopforfuel.backend.entity.transaction;

import com.stopforfuel.backend.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shift_transaction")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "txn_type", discriminatorType = DiscriminatorType.STRING)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "txnType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CashTransaction.class, name = "CASH"),
    @JsonSubTypes.Type(value = NightCashTransaction.class, name = "NIGHT_CASH"),
    @JsonSubTypes.Type(value = UpiTransaction.class, name = "UPI"),
    @JsonSubTypes.Type(value = CardTransaction.class, name = "CARD"),
    @JsonSubTypes.Type(value = ChequeTransaction.class, name = "CHEQUE"),
    @JsonSubTypes.Type(value = BankTransaction.class, name = "BANK"),
    @JsonSubTypes.Type(value = CcmsTransaction.class, name = "CCMS"),
    @JsonSubTypes.Type(value = ExpenseTransaction.class, name = "EXPENSE")
})
@Getter
@Setter
public abstract class ShiftTransaction extends BaseEntity {

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "received_amount", precision = 19, scale = 4)
    private BigDecimal receivedAmount;

    @Column(name = "remarks")
    private String remarks;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.transactionDate == null) {
            this.transactionDate = LocalDateTime.now();
        }
    }
}
