package com.stopforfuel.backend.entity.transaction;
import com.stopforfuel.backend.entity.UpiCompany;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("UPI")
@Getter @Setter
public class UpiTransaction extends ShiftTransaction {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upi_company_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private UpiCompany upiCompany;
}
