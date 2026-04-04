package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Calculate opening balance for a customer at a given date.
     * Opening Balance = Total Credit Billed (before date) - Total Payments Received (before date)
     */
    @Transactional(readOnly = true)
    public BigDecimal getOpeningBalance(Long customerId, LocalDate asOfDate) {
        LocalDateTime beforeDate = asOfDate.atStartOfDay();

        BigDecimal totalBilled = invoiceBillRepository.sumCreditBillsByCustomerBefore(customerId, beforeDate);
        BigDecimal totalPaid = paymentRepository.sumPaymentsByCustomerBefore(customerId, beforeDate);

        return totalBilled.subtract(totalPaid);
    }

    /**
     * Get customer ledger between dates.
     * Returns chronological list of all credit bills (debits) and payments (credits)
     * with running balance.
     */
    @Transactional(readOnly = true)
    public CustomerLedger getCustomerLedger(Long customerId, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        BigDecimal openingBalance = getOpeningBalance(customerId, fromDate);

        // Fetch all credit bills in period (both linked and unlinked to statements)
        List<InvoiceBill> allCreditBills = invoiceBillRepository.findAllByScid(SecurityUtils.getScid()).stream()
                .filter(b -> b.getCustomer() != null && b.getCustomer().getId().equals(customerId))
                .filter(b -> com.stopforfuel.backend.enums.BillType.CREDIT.equals(b.getBillType()))
                .filter(b -> b.getDate() != null
                        && !b.getDate().isBefore(fromDateTime)
                        && !b.getDate().isAfter(toDateTime))
                .toList();

        // Fetch payments in period
        List<Payment> payments = paymentRepository.findByCustomerIdAndPaymentDateBetween(
                customerId, fromDateTime, toDateTime);

        // Build ledger entries
        List<LedgerEntry> entries = new ArrayList<>();

        for (InvoiceBill bill : allCreditBills) {
            LedgerEntry entry = new LedgerEntry();
            entry.date = bill.getDate();
            entry.type = "DEBIT";
            entry.description = "Credit Bill"
                    + (bill.getBillDesc() != null ? " - " + bill.getBillDesc() : "");
            entry.referenceId = bill.getId();
            entry.debitAmount = bill.getNetAmount();
            entry.creditAmount = BigDecimal.ZERO;
            entries.add(entry);
        }

        for (Payment payment : payments) {
            LedgerEntry entry = new LedgerEntry();
            entry.date = payment.getPaymentDate();
            entry.type = "CREDIT";
            entry.description = "Payment"
                    + (payment.getPaymentMode() != null ? " (" + payment.getPaymentMode().name() + ")" : "")
                    + (payment.getReferenceNo() != null ? " Ref: " + payment.getReferenceNo() : "");
            entry.referenceId = payment.getId();
            entry.debitAmount = BigDecimal.ZERO;
            entry.creditAmount = payment.getAmount();
            entries.add(entry);
        }

        // Sort chronologically
        entries.sort(Comparator.comparing(e -> e.date));

        // Calculate running balance
        BigDecimal runningBalance = openingBalance;
        for (LedgerEntry entry : entries) {
            runningBalance = runningBalance.add(entry.debitAmount).subtract(entry.creditAmount);
            entry.runningBalance = runningBalance;
        }

        CustomerLedger ledger = new CustomerLedger();
        ledger.customerId = customerId;
        ledger.fromDate = fromDate;
        ledger.toDate = toDate;
        ledger.openingBalance = openingBalance;
        ledger.closingBalance = runningBalance;
        ledger.entries = entries;
        ledger.totalDebits = entries.stream()
                .map(e -> e.debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ledger.totalCredits = entries.stream()
                .map(e -> e.creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ledger;
    }

    /**
     * Get outstanding (unpaid) credit bills for a customer.
     */
    @Transactional(readOnly = true)
    public List<InvoiceBill> getOutstandingBills(Long customerId) {
        return invoiceBillRepository.findByCustomerIdAndPaymentStatus(customerId, com.stopforfuel.backend.enums.PaymentStatus.NOT_PAID);
    }

    // DTOs
    public static class CustomerLedger {
        public Long customerId;
        public LocalDate fromDate;
        public LocalDate toDate;
        public BigDecimal openingBalance;
        public BigDecimal closingBalance;
        public BigDecimal totalDebits;
        public BigDecimal totalCredits;
        public List<LedgerEntry> entries;
    }

    public static class LedgerEntry {
        public LocalDateTime date;
        public String type; // DEBIT or CREDIT
        public String description;
        public Long referenceId;
        public BigDecimal debitAmount;
        public BigDecimal creditAmount;
        public BigDecimal runningBalance;
    }
}
