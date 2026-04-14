package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
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
    private final CustomerRepository customerRepository;
    private final StatementRepository statementRepository;

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

        // Statement customers pay against statements, not individual bills. For them,
        // the ledger shows one debit row per statement; local-credit customers see each bill.
        // (Opening balance via sumCreditBillsByCustomerBefore is still correct since a
        // statement's total equals the sum of its constituent bills.)
        Customer customer = customerRepository.findById(customerId).orElse(null);
        // Canonical classification: Party.partyType == "Statement" (matches CustomerService,
        // CreditMonitoringService, PaymentService). Backstop: any generated statements means
        // the customer should be treated as a statement customer even if Party isn't set.
        boolean isStatementCustomer =
                (customer != null && customer.getParty() != null
                        && "Statement".equalsIgnoreCase(customer.getParty().getPartyType()))
                || statementRepository.existsByCustomerId(customerId);

        // Fetch payments in period
        List<Payment> payments = paymentRepository.findByCustomerIdAndPaymentDateBetween(
                customerId, fromDateTime, toDateTime);

        // Build ledger entries
        List<LedgerEntry> entries = new ArrayList<>();

        if (isStatementCustomer) {
            List<Statement> statements = statementRepository.findByCustomerIdAndStatementDateBetween(
                    customerId, fromDate, toDate);
            for (Statement s : statements) {
                LedgerEntry entry = new LedgerEntry();
                entry.date = s.getStatementDate() != null ? s.getStatementDate().atStartOfDay() : fromDateTime;
                entry.type = "DEBIT";
                entry.description = "Statement " + (s.getStatementNo() != null ? s.getStatementNo() : "#" + s.getId());
                entry.referenceId = s.getId();
                entry.debitAmount = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
                entry.creditAmount = BigDecimal.ZERO;
                entries.add(entry);
            }
        } else {
            List<InvoiceBill> allCreditBills = invoiceBillRepository.findCreditBillsByCustomerAndDateRange(
                    customerId, fromDateTime, toDateTime, SecurityUtils.getScid());
            for (InvoiceBill bill : allCreditBills) {
                LedgerEntry entry = new LedgerEntry();
                entry.date = bill.getDate();
                entry.type = "DEBIT";
                entry.description = "Credit Bill " + (bill.getBillNo() != null ? bill.getBillNo() : "#" + bill.getId());
                entry.referenceId = bill.getId();
                entry.debitAmount = bill.getNetAmount();
                entry.creditAmount = BigDecimal.ZERO;
                entries.add(entry);
            }
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
        return invoiceBillRepository.findByCustomerIdAndPaymentStatusAndScid(customerId, com.stopforfuel.backend.enums.PaymentStatus.NOT_PAID, SecurityUtils.getScid());
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
