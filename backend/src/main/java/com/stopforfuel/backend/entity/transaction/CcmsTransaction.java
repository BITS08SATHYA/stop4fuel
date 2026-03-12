package com.stopforfuel.backend.entity.transaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("CCMS")
@Getter @Setter
public class CcmsTransaction extends ShiftTransaction {
    @Column(name = "ccms_number")
    private String ccmsNumber;
}
