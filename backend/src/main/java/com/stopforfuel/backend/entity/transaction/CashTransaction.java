package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("CASH")
@Getter @Setter
public class CashTransaction extends ShiftTransaction {
}
