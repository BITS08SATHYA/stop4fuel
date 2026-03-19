package com.stopforfuel.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "upi_company")
@Getter @Setter
public class UpiCompany extends SimpleBaseEntity {
    @jakarta.validation.constraints.NotBlank(message = "UPI company name is required")
    @Column(name = "company_name", nullable = false)
    private String companyName;
}
