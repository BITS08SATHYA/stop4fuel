package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payment_mode")
@Getter
@Setter
public class PaymentMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.validation.constraints.NotBlank(message = "Payment mode name is required")
    @Column(name = "mode_name", nullable = false, unique = true)
    @JsonProperty("name")
    private String modeName; // CASH, CHEQUE, UPI, NEFT, CARD, CCMS, BANK_TRANSFER
}
