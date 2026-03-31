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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditManagementService {

    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;

    /**
     * Get credit overview: ALL customers with their credit balance and aging breakdown.
     * Balance = total credit billed - total payments received (ledger balance).
     */
    @Transactional(readOnly = true)
    public CreditOverview getCreditOverview(String categoryType) {
        // Load all customers
        List<Customer> allCustomers = customerRepository.findAllByScid(SecurityUtils.getScid());
        if (categoryType != null && !categoryType.isEmpty()) {
            allCustomers = allCustomers.stream()
                    .filter(c -> c.getCustomerCategory() != null && categoryType.equals(c.getCustomerCategory().getCategoryType()))
                    .toList();
        }

        // Load all credit bills (both paid and unpaid) for balance calculation
        List<InvoiceBill> allCreditBills = invoiceBillRepository.findByBillType(com.stopforfuel.backend.enums.BillType.CREDIT);

        // Load all payments
        List<Payment> allPayments = paymentRepository.findAllByScid(SecurityUtils.getScid());

        // Group credit bills by customer
        Map<Long, List<InvoiceBill>> billsByCustomer = allCreditBills.stream()
                .filter(b -> b.getCustomer() != null)
                .collect(Collectors.groupingBy(b -> b.getCustomer().getId()));

        // Group payments by customer
        Map<Long, List<Payment>> paymentsByCustomer = allPayments.stream()
                .filter(p -> p.getCustomer() != null)
                .collect(Collectors.groupingBy(p -> p.getCustomer().getId()));

        // Unpaid bills for aging (only NOT_PAID bills age)
        Map<Long, List<InvoiceBill>> unpaidByCustomer = allCreditBills.stream()
                .filter(b -> b.getCustomer() != null && "NOT_PAID".equals(b.getPaymentStatus()))
                .collect(Collectors.groupingBy(b -> b.getCustomer().getId()));

        LocalDate today = LocalDate.now();

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalAging0to30 = BigDecimal.ZERO;
        BigDecimal totalAging31to60 = BigDecimal.ZERO;
        BigDecimal totalAging61to90 = BigDecimal.ZERO;
        BigDecimal totalAging90Plus = BigDecimal.ZERO;

        List<CreditCustomerSummary> customerSummaries = new ArrayList<>();

        for (Customer customer : allCustomers) {
            Long custId = customer.getId();

            // Calculate ledger balance: total billed - total paid
            List<InvoiceBill> custBills = billsByCustomer.getOrDefault(custId, Collections.emptyList());
            List<Payment> custPayments = paymentsByCustomer.getOrDefault(custId, Collections.emptyList());

            BigDecimal totalBilled = custBills.stream()
                    .map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalPaid = custPayments.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal ledgerBalance = totalBilled.subtract(totalPaid);

            // Aging from unpaid bills
            List<InvoiceBill> unpaidBills = unpaidByCustomer.getOrDefault(custId, Collections.emptyList());
            BigDecimal aging0to30 = BigDecimal.ZERO;
            BigDecimal aging31to60 = BigDecimal.ZERO;
            BigDecimal aging61to90 = BigDecimal.ZERO;
            BigDecimal aging90Plus = BigDecimal.ZERO;

            for (InvoiceBill bill : unpaidBills) {
                BigDecimal amt = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;
                long daysOld = bill.getDate() != null
                        ? ChronoUnit.DAYS.between(bill.getDate().toLocalDate(), today)
                        : 0;

                if (daysOld <= 30) {
                    aging0to30 = aging0to30.add(amt);
                } else if (daysOld <= 60) {
                    aging31to60 = aging31to60.add(amt);
                } else if (daysOld <= 90) {
                    aging61to90 = aging61to90.add(amt);
                } else {
                    aging90Plus = aging90Plus.add(amt);
                }
            }

            // Outstanding statements count
            List<Statement> outstandingStatements = statementRepository
                    .findByCustomerIdAndStatus(custId, "NOT_PAID");

            CreditCustomerSummary summary = new CreditCustomerSummary();
            summary.setCustomerId(custId);
            summary.setCustomerName(customer.getName());
            summary.setPhoneNumbers(customer.getPhoneNumbers());
            summary.setGroupName(customer.getGroup() != null ? customer.getGroup().getGroupName() : null);
            summary.setCategoryType(customer.getCustomerCategory() != null ? customer.getCustomerCategory().getCategoryType() : null);
            summary.setCategoryName(customer.getCustomerCategory() != null ? customer.getCustomerCategory().getCategoryName() : null);
            summary.setCreditLimitAmount(customer.getCreditLimitAmount());
            summary.setStatus(customer.getStatus() != null ? customer.getStatus().name() : null);
            summary.setLedgerBalance(ledgerBalance);
            summary.setTotalBilled(totalBilled);
            summary.setTotalPaid(totalPaid);
            summary.setTotalOutstanding(unpaidBills.stream()
                    .map(b -> b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            summary.setAging0to30(aging0to30);
            summary.setAging31to60(aging31to60);
            summary.setAging61to90(aging61to90);
            summary.setAging90Plus(aging90Plus);
            summary.setPendingBillCount(unpaidBills.size());
            summary.setTotalBillCount(custBills.size());
            summary.setTotalPaymentCount(custPayments.size());
            summary.setPendingStatementCount(outstandingStatements.size());

            customerSummaries.add(summary);

            // Aggregate totals (only from unpaid amounts)
            totalOutstanding = totalOutstanding.add(summary.getTotalOutstanding());
            totalAging0to30 = totalAging0to30.add(aging0to30);
            totalAging31to60 = totalAging31to60.add(aging31to60);
            totalAging61to90 = totalAging61to90.add(aging61to90);
            totalAging90Plus = totalAging90Plus.add(aging90Plus);
        }

        // Sort: customers with outstanding first (desc), then others by name
        customerSummaries.sort((a, b) -> {
            int cmp = b.getTotalOutstanding().compareTo(a.getTotalOutstanding());
            if (cmp != 0) return cmp;
            return (a.getCustomerName() != null ? a.getCustomerName() : "")
                    .compareToIgnoreCase(b.getCustomerName() != null ? b.getCustomerName() : "");
        });

        CreditOverview overview = new CreditOverview();
        overview.setTotalOutstanding(totalOutstanding);
        overview.setTotalAging0to30(totalAging0to30);
        overview.setTotalAging31to60(totalAging31to60);
        overview.setTotalAging61to90(totalAging61to90);
        overview.setTotalAging90Plus(totalAging90Plus);
        overview.setTotalCreditCustomers((int) customerSummaries.stream()
                .filter(c -> c.getTotalOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .count());
        overview.setTotalCustomers(customerSummaries.size());
        overview.setCustomers(customerSummaries);

        // Govt vs Non-Govt outstanding breakdown
        BigDecimal govtOutstanding = customerSummaries.stream()
                .filter(c -> "GOVERNMENT".equals(c.getCategoryType()))
                .map(CreditCustomerSummary::getTotalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nonGovtOutstanding = customerSummaries.stream()
                .filter(c -> !"GOVERNMENT".equals(c.getCategoryType()))
                .map(CreditCustomerSummary::getTotalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.setGovtOutstanding(govtOutstanding);
        overview.setNonGovtOutstanding(nonGovtOutstanding);

        return overview;
    }

    /**
     * Get detailed credit info for a single customer: all credit bills, statements, payments.
     */
    @Transactional(readOnly = true)
    public CreditCustomerDetail getCustomerCreditDetail(Long customerId) {
        // All credit bills (both paid and unpaid)
        List<InvoiceBill> allBills = invoiceBillRepository.findByBillType(com.stopforfuel.backend.enums.BillType.CREDIT).stream()
                .filter(b -> b.getCustomer() != null && b.getCustomer().getId().equals(customerId))
                .sorted((a, b) -> {
                    if (a.getDate() == null || b.getDate() == null) return 0;
                    return b.getDate().compareTo(a.getDate());
                })
                .toList();

        List<InvoiceBill> unpaidBills = allBills.stream()
                .filter(b -> "NOT_PAID".equals(b.getPaymentStatus()))
                .toList();

        List<InvoiceBill> paidBills = allBills.stream()
                .filter(b -> "PAID".equals(b.getPaymentStatus()))
                .toList();

        // All statements (both outstanding and paid)
        List<Statement> allStatements = statementRepository.findByCustomerId(customerId);
        allStatements.sort((a, b) -> {
            if (a.getStatementDate() == null || b.getStatementDate() == null) return 0;
            return b.getStatementDate().compareTo(a.getStatementDate());
        });

        // All payments
        List<Payment> payments = paymentRepository.findByCustomerId(customerId);
        payments.sort((a, b) -> {
            if (a.getPaymentDate() == null || b.getPaymentDate() == null) return 0;
            return b.getPaymentDate().compareTo(a.getPaymentDate());
        });

        CreditCustomerDetail detail = new CreditCustomerDetail();
        detail.setUnpaidBills(unpaidBills);
        detail.setPaidBills(paidBills);
        detail.setStatements(allStatements);
        detail.setPayments(payments);

        return detail;
    }

    // --- DTOs ---

    @Getter @Setter
    public static class CreditOverview {
        private BigDecimal totalOutstanding;
        private BigDecimal totalAging0to30;
        private BigDecimal totalAging31to60;
        private BigDecimal totalAging61to90;
        private BigDecimal totalAging90Plus;
        private int totalCreditCustomers; // customers with outstanding > 0
        private int totalCustomers;       // all customers
        private BigDecimal govtOutstanding;
        private BigDecimal nonGovtOutstanding;
        private List<CreditCustomerSummary> customers;
    }

    @Getter @Setter
    public static class CreditCustomerSummary {
        private Long customerId;
        private String customerName;
        private Set<String> phoneNumbers;
        private String groupName;
        private String categoryType;
        private String categoryName;
        private String status;           // ACTIVE, INACTIVE, BLOCKED
        private BigDecimal creditLimitAmount;
        private BigDecimal ledgerBalance; // total billed - total paid
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private BigDecimal totalOutstanding; // sum of unpaid bill amounts
        private BigDecimal aging0to30;
        private BigDecimal aging31to60;
        private BigDecimal aging61to90;
        private BigDecimal aging90Plus;
        private int pendingBillCount;
        private int totalBillCount;
        private int totalPaymentCount;
        private int pendingStatementCount;
    }

    @Getter @Setter
    public static class CreditCustomerDetail {
        private List<InvoiceBill> unpaidBills;
        private List<InvoiceBill> paidBills;
        private List<Statement> statements;
        private List<Payment> payments;
    }
}
