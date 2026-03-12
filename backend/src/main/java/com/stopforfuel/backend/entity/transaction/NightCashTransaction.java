package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("NIGHT_CASH")
@Getter @Setter
public class NightCashTransaction extends ShiftTransaction {
}
