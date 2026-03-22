package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final BillSequenceService billSequenceService;
    private final StatementPdfGenerator pdfGenerator;
    private final S3StorageService s3StorageService;

    public Page<Statement> getStatements(Long customerId, String status, LocalDate fromDate, LocalDate toDate, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return statementRepository.findWithFiltersAndSearch(customerId, status, fromDate, toDate, search.trim(), pageable);
        }
        return statementRepository.findWithFilters(customerId, status, fromDate, toDate, pageable);
    }

    public List<Statement> getAllStatements() {
        return statementRepository.findAll();
    }

    public Statement getStatementById(Long id) {
        return statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));
    }

    public Statement getStatementByNo(String statementNo) {
        return statementRepository.findByStatementNo(statementNo)
                .orElseThrow(() -> new RuntimeException("Statement not found with no: " + statementNo));
    }

    public List<Statement> getStatementsByCustomer(Long customerId) {
        return statementRepository.findByCustomerId(customerId);
    }

    public List<Statement> getOutstandingStatements() {
        return statementRepository.findByStatus("NOT_PAID");
    }

    public List<Statement> getOutstandingByCustomer(Long customerId) {
        return statementRepository.findByCustomerIdAndStatus(customerId, "NOT_PAID");
    }

    /**
     * Generate a statement for a customer for the given date range.
     * Supports optional filters: vehicleId, productId, or specific billIds.
     */
    @Transactional
    public Statement generateStatement(Long customerId, LocalDate fromDate, LocalDate toDate,
                                       Long vehicleId, Long productId, List<Long> billIds) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        List<InvoiceBill> bills;

        if (billIds != null && !billIds.isEmpty()) {
            // Bill-wise: user selected specific bills
            bills = invoiceBillRepository.findUnlinkedCreditBillsByIds(billIds);
            // Validate all bills belong to this customer
            for (InvoiceBill bill : bills) {
                if (!bill.getCustomer().getId().equals(customerId)) {
                    throw new RuntimeException("Bill " + bill.getId() + " does not belong to customer " + customerId);
                }
            }
        } else {
            // Convert dates to LocalDateTime for query (full day range)
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

            if (vehicleId != null && productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                        customerId, fromDateTime, toDateTime, vehicleId, productId);
            } else if (vehicleId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                        customerId, fromDateTime, toDateTime, vehicleId);
            } else if (productId != null) {
                bills = invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                        customerId, fromDateTime, toDateTime, productId);
            } else {
                bills = invoiceBillRepository.findUnlinkedCreditBills(
                        customerId, fromDateTime, toDateTime);
            }
        }

        if (bills.isEmpty()) {
            throw new RuntimeException("No unlinked credit bills found for the selected filters");
        }

        // Compute totals
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill bill : bills) {
            if (bill.getNetAmount() != null) {
                totalAmount = totalAmount.add(bill.getNetAmount());
            }
        }

        // Rounding to nearest rupee
        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);

        // Get next statement number
        String nextStatementNo = billSequenceService.getNextBillNo("STMT");

        // Create statement
        Statement statement = new Statement();
        statement.setScid(customer.getScid());
        statement.setStatementNo(nextStatementNo);
        statement.setCustomer(customer);
        statement.setFromDate(fromDate);
        statement.setToDate(toDate);
        statement.setStatementDate(LocalDate.now());
        statement.setNumberOfBills(bills.size());
        statement.setTotalAmount(totalAmount);
        statement.setRoundingAmount(roundingAmount);
        statement.setNetAmount(netAmount);
        statement.setReceivedAmount(BigDecimal.ZERO);
        statement.setBalanceAmount(netAmount);
        statement.setStatus("NOT_PAID");

        Statement saved = statementRepository.save(statement);

        // Link all bills to this statement
        for (InvoiceBill bill : bills) {
            bill.setStatement(saved);
            invoiceBillRepository.save(bill);
        }

        return saved;
    }

    /**
     * Preview unlinked credit bills matching the given filters (without creating a statement).
     */
    public List<InvoiceBill> previewBills(Long customerId, LocalDate fromDate, LocalDate toDate,
                                          Long vehicleId, Long productId) {
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

        if (vehicleId != null && productId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByVehicleAndProduct(
                    customerId, fromDateTime, toDateTime, vehicleId, productId);
        } else if (vehicleId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByVehicle(
                    customerId, fromDateTime, toDateTime, vehicleId);
        } else if (productId != null) {
            return invoiceBillRepository.findUnlinkedCreditBillsByProduct(
                    customerId, fromDateTime, toDateTime, productId);
        } else {
            return invoiceBillRepository.findUnlinkedCreditBills(
                    customerId, fromDateTime, toDateTime);
        }
    }

    /**
     * Remove a disputed/unfit bill from its statement and recalculate totals.
     */
    @Transactional
    public Statement removeBillFromStatement(Long statementId, Long invoiceBillId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        InvoiceBill bill = invoiceBillRepository.findById(invoiceBillId)
                .orElseThrow(() -> new RuntimeException("Invoice bill not found"));

        if (bill.getStatement() == null || !bill.getStatement().getId().equals(statementId)) {
            throw new RuntimeException("Bill does not belong to this statement");
        }

        // Unlink the bill
        bill.setStatement(null);
        invoiceBillRepository.save(bill);

        // Recalculate statement totals from remaining bills
        List<InvoiceBill> remainingBills = invoiceBillRepository.findByStatementId(statementId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceBill remaining : remainingBills) {
            if (remaining.getNetAmount() != null) {
                totalAmount = totalAmount.add(remaining.getNetAmount());
            }
        }

        BigDecimal netAmount = totalAmount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundingAmount = netAmount.subtract(totalAmount);
        BigDecimal balanceAmount = netAmount.subtract(statement.getReceivedAmount());

        statement.setNumberOfBills(remainingBills.size());
        statement.setTotalAmount(totalAmount);
        statement.setRoundingAmount(roundingAmount);
        statement.setNetAmount(netAmount);
        statement.setBalanceAmount(balanceAmount);

        // Check if already fully paid after removal
        if (balanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            statement.setStatus("PAID");
            statement.setBalanceAmount(BigDecimal.ZERO);
            // Mark remaining bills as PAID
            for (InvoiceBill remaining : remainingBills) {
                remaining.setPaymentStatus("PAID");
                invoiceBillRepository.save(remaining);
            }
        }

        return statementRepository.save(statement);
    }

    /**
     * Get all bills linked to a statement, useful for statement detail/print view.
     */
    public List<InvoiceBill> getStatementBills(Long statementId) {
        return invoiceBillRepository.findByStatementId(statementId);
    }

    @Transactional
    public Statement generateAndStorePdf(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));

        List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(id);

        List<Company> companies = companyRepository.findByScid(
                statement.getScid() != null ? statement.getScid() : SecurityUtils.getScid());
        String companyName = !companies.isEmpty() ? companies.get(0).getName() : "StopForFuel";

        byte[] pdfBytes = pdfGenerator.generate(statement, bills, companyName);

        LocalDate date = statement.getStatementDate() != null ? statement.getStatementDate() : LocalDate.now();
        String key = String.format("statements/%d/%02d/%d/statement.pdf",
                date.getYear(), date.getMonthValue(), id);

        // Delete old PDF if exists
        if (statement.getStatementPdfUrl() != null && !statement.getStatementPdfUrl().isEmpty()) {
            try { s3StorageService.delete(statement.getStatementPdfUrl()); } catch (Exception ignored) {}
        }

        s3StorageService.upload(key, pdfBytes, "application/pdf");
        statement.setStatementPdfUrl(key);
        return statementRepository.save(statement);
    }

    public String getStatementPdfUrl(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + id));
        if (statement.getStatementPdfUrl() == null || statement.getStatementPdfUrl().isEmpty()) {
            throw new RuntimeException("No PDF generated for this statement");
        }
        return s3StorageService.getPresignedUrl(statement.getStatementPdfUrl());
    }

    public void deleteStatement(Long id) {
        Statement statement = statementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        // Unlink all bills first
        List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(id);
        for (InvoiceBill bill : bills) {
            bill.setStatement(null);
            invoiceBillRepository.save(bill);
        }

        statementRepository.deleteById(id);
    }

    public StatementStats getStats() {
        LocalDate startOfThisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);

        // Last month metrics
        long statementsLastMonth = statementRepository.countByStatementDateRange(startOfLastMonth, startOfThisMonth);
        long paidLastMonth = statementRepository.countPaidByStatementDateRange(startOfLastMonth, startOfThisMonth);
        BigDecimal amountGeneratedLastMonth = statementRepository.sumNetAmountByDateRange(startOfLastMonth, startOfThisMonth);
        BigDecimal amountCollectedLastMonth = statementRepository.sumReceivedAmountByDateRange(startOfLastMonth, startOfThisMonth);

        // All-time metrics
        long totalStatements = statementRepository.count();
        long totalPaid = statementRepository.countPaid();
        double paidPercentage = totalStatements > 0 ? (totalPaid * 100.0) / totalStatements : 0;
        BigDecimal totalUnpaidAmount = statementRepository.sumUnpaidBalance();
        BigDecimal totalNetAmount = statementRepository.sumNetAmount();
        BigDecimal totalReceivedAmount = statementRepository.sumReceivedAmount();
        double collectionRate = totalNetAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalReceivedAmount.multiply(BigDecimal.valueOf(100)).divide(totalNetAmount, 2, RoundingMode.HALF_UP).doubleValue()
                : 0;
        BigDecimal avgStatementAmount = statementRepository.avgNetAmount();

        return new StatementStats(
                statementsLastMonth, paidLastMonth, amountGeneratedLastMonth, amountCollectedLastMonth,
                totalStatements, totalPaid, paidPercentage, totalUnpaidAmount,
                totalNetAmount, totalReceivedAmount, collectionRate, avgStatementAmount
        );
    }
}
