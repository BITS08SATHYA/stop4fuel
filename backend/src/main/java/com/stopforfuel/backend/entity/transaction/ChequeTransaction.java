package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("CHEQUE")
@Getter @Setter
public class ChequeTransaction extends ShiftTransaction {
    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "in_favor_of")
    private String inFavorOf;

    @Column(name = "cheque_no")
    private String chequeNo;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;
}
