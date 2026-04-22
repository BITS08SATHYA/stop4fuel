package com.stopforfuel.backend.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankMatchCandidate {
    private String type; // "INVOICE" or "STATEMENT"
    private Long id;
    private String displayNo; // billNo or statementNo
    private Long customerId;
    private String customerName;
    private BigDecimal amount;
    private LocalDate docDate;
    private String matchReason; // e.g., "amount+name", "amount"
    private int score;
}
