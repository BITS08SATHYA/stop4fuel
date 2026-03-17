package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
    }

    @Test
    void getOpeningBalance_calculatesCorrectly() {
        LocalDate asOfDate = LocalDate.of(2026, 3, 1);
        when(invoiceBillRepository.sumCreditBillsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("50000"));
        when(paymentRepository.sumPaymentsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("30000"));

        BigDecimal balance = ledgerService.getOpeningBalance(1L, asOfDate);
        assertEquals(new BigDecimal("20000"), balance);
    }

    @Test
    void getCustomerLedger_buildsEntriesWithRunningBalance() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 15);

        // Opening balance
        when(invoiceBillRepository.sumCreditBillsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("10000"));
        when(paymentRepository.sumPaymentsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("5000"));

        // Credit bill in period
        InvoiceBill bill = new InvoiceBill();
        bill.setId(10L);
        bill.setCustomer(testCustomer);
        bill.setBillType("CREDIT");
        bill.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));
        bill.setNetAmount(new BigDecimal("3000"));

        when(invoiceBillRepository.findAll()).thenReturn(List.of(bill));

        // Payment in period
        Payment payment = new Payment();
        payment.setId(20L);
        payment.setCustomer(testCustomer);
        payment.setPaymentDate(LocalDateTime.of(2026, 3, 10, 14, 0));
        payment.setAmount(new BigDecimal("2000"));

        when(paymentRepository.findByCustomerIdAndPaymentDateBetween(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(payment));

        LedgerService.CustomerLedger ledger = ledgerService.getCustomerLedger(1L, from, to);

        assertEquals(new BigDecimal("5000"), ledger.openingBalance);
        assertEquals(2, ledger.entries.size());
        assertEquals("DEBIT", ledger.entries.get(0).type);
        assertEquals("CREDIT", ledger.entries.get(1).type);
        // Opening 5000 + debit 3000 - credit 2000 = 6000
        assertEquals(new BigDecimal("6000"), ledger.closingBalance);
        assertEquals(new BigDecimal("3000"), ledger.totalDebits);
        assertEquals(new BigDecimal("2000"), ledger.totalCredits);
    }

    @Test
    void getCustomerLedger_emptyPeriod_returnsOpeningAsClosing() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 15);

        when(invoiceBillRepository.sumCreditBillsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("10000"));
        when(paymentRepository.sumPaymentsByCustomerBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("10000"));
        when(invoiceBillRepository.findAll()).thenReturn(List.of());
        when(paymentRepository.findByCustomerIdAndPaymentDateBetween(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        LedgerService.CustomerLedger ledger = ledgerService.getCustomerLedger(1L, from, to);

        assertEquals(BigDecimal.ZERO, ledger.openingBalance);
        assertEquals(BigDecimal.ZERO, ledger.closingBalance);
        assertTrue(ledger.entries.isEmpty());
    }

    @Test
    void getOutstandingBills_returnsList() {
        InvoiceBill bill = new InvoiceBill();
        bill.setId(1L);
        when(invoiceBillRepository.findByCustomerIdAndPaymentStatus(1L, "NOT_PAID"))
                .thenReturn(List.of(bill));

        assertEquals(1, ledgerService.getOutstandingBills(1L).size());
    }
}
