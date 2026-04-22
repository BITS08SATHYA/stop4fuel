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
public class BankStatementRow {
    private int rowIndex;
    private LocalDate txnDate;
    private String narration;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balance;
    private String rawLine;
}
