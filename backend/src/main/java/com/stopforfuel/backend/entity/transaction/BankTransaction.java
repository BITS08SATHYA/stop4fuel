package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("BANK")
@Getter @Setter
public class BankTransaction extends ShiftTransaction {
    @Column(name = "bank_name")
    private String bankName;
}
