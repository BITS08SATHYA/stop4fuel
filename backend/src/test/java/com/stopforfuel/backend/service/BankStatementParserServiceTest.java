package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.bank.BankStatementRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BankStatementParserServiceTest {

    private final BankStatementParserService parser = new BankStatementParserService();

    @Test
    void parsesGenericIndianBankFormat() {
        String text = String.join("\n",
                "Statement of Account for 01-04-2026 to 30-04-2026",
                "Date       Narration                              Debit       Credit      Balance",
                "05/04/2026 UPI/SUNDARAM TRANSPORT/PAYMENT          0.00        25,000.00   1,25,000.00",
                "07/04/2026 NEFT/RAVI KUMAR TRADERS                 0.00        5,432.50    1,30,432.50",
                "09/04/2026 ATM WITHDRAWAL HDFC BANK                2,000.00    0.00        1,28,432.50"
        );
        BankStatementParserService.ParseResult result = parser.parseText(text);
        assertEquals(3, result.rows.size(), "should parse three transaction rows");

        BankStatementRow r0 = result.rows.get(0);
        assertNotNull(r0.getTxnDate());
        assertEquals(new BigDecimal("25000.00"), r0.getCredit());
        assertTrue(r0.getNarration().toUpperCase().contains("SUNDARAM"));

        BankStatementRow r1 = result.rows.get(1);
        assertEquals(new BigDecimal("5432.50"), r1.getCredit());

        BankStatementRow r2 = result.rows.get(2);
        assertEquals(new BigDecimal("2000.00"), r2.getDebit());
    }

    @Test
    void stitchesMultiLineNarrations() {
        String text = String.join("\n",
                "05-04-2026 UPI TRANSFER                             0.00        10,000.00   50,000.00",
                "            RAVI ENTERPRISES/UTR12345               ",
                "06-04-2026 IMPS/SRI KRISHNA LOGISTICS               0.00        7,500.00    57,500.00"
        );
        BankStatementParserService.ParseResult result = parser.parseText(text);
        assertEquals(2, result.rows.size());
        assertTrue(result.rows.get(0).getNarration().toUpperCase().contains("RAVI ENTERPRISES"));
    }

    @Test
    void emptyPdfYieldsWarning() {
        BankStatementParserService.ParseResult result = parser.parseText("");
        assertTrue(result.rows.isEmpty());
        assertFalse(result.warnings.isEmpty());
    }

    @Test
    void handlesDateWithShortYearAndDots() {
        String text = "05.04.26 UPI/TEST CUSTOMER                      0.00        1,234.00    10,234.00";
        BankStatementParserService.ParseResult result = parser.parseText(text);
        assertEquals(1, result.rows.size());
        assertEquals(2026, result.rows.get(0).getTxnDate().getYear());
        assertEquals(new BigDecimal("1234.00"), result.rows.get(0).getCredit());
    }
}
