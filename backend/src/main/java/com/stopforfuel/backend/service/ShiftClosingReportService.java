package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftClosingReportService {

    private final ShiftClosingReportRepository reportRepository;
    private final ReportLineItemRepository lineItemRepository;
    private final ReportCashBillBreakdownRepository breakdownRepository;
    private final ReportAuditLogRepository auditLogRepository;
    private final ShiftRepository shiftRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final ShiftTransactionRepository shiftTransactionRepository;
    private final CashAdvanceRepository cashAdvanceRepository;
    private final ExternalCashInflowRepository inflowRepository;
    private final CashInflowRepaymentRepository repaymentRepository;
    private final EmployeeAdvanceRepository employeeAdvanceRepository;
    private final StatementRepository statementRepository;
    private final ShiftTransactionService shiftTransactionService;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public ShiftClosingReport generateReport(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        if (!"CLOSED".equals(shift.getStatus()) && !"RECONCILED".equals(shift.getStatus())) {
            throw new RuntimeException("Shift must be CLOSED before generating a report");
        }

        // If report already exists, recompute it
        Optional<ShiftClosingReport> existing = reportRepository.findByShiftId(shiftId);
        if (existing.isPresent()) {
            return recomputeReport(existing.get().getId());
        }

        ShiftClosingReport report = new ShiftClosingReport();
        report.setShift(shift);
        report.setShiftId(shift.getShiftId());
        report.setScid(shift.getScid());
        report.setStatus("DRAFT");
        report.setReportDate(LocalDateTime.now());

        ShiftClosingReport saved = reportRepository.save(report);
        populateReportData(saved, shift);
        return reportRepository.save(saved);
    }

    public ShiftClosingReport getReport(Long shiftId) {
        return reportRepository.findByShiftId(shiftId)
                .orElseThrow(() -> new RuntimeException("Report not found for shift: " + shiftId));
    }

    public ShiftClosingReport getReportById(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found: " + reportId));
    }

    public List<ShiftClosingReport> getAllReports(String status) {
        if (status != null && !status.isEmpty()) {
            return reportRepository.findByStatusOrderByReportDateDesc(status);
        }
        return reportRepository.findAllByOrderByReportDateDesc();
    }

    @Transactional
    public ShiftClosingReport editLineItem(Long reportId, Long lineItemId, BigDecimal newAmount, String reason) {
        ShiftClosingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new RuntimeException("Cannot edit a finalized report");
        }

        ReportLineItem lineItem = lineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Line item not found"));

        if (!lineItem.getReport().getId().equals(reportId)) {
            throw new RuntimeException("Line item does not belong to this report");
        }

        BigDecimal oldAmount = lineItem.getAmount();
        lineItem.setOriginalAmount(oldAmount);
        lineItem.setAmount(newAmount);
        lineItemRepository.save(lineItem);

        // Cascade: update source entity
        cascadeEditToSource(lineItem, newAmount);

        // Audit log
        ReportAuditLog log = new ReportAuditLog();
        log.setReport(report);
        log.setAction("LINE_ITEM_EDITED");
        log.setDescription(reason != null ? reason : "Line item edited: " + lineItem.getLabel());
        log.setLineItemId(lineItemId);
        log.setPreviousValue(oldAmount);
        log.setNewValue(newAmount);
        log.setPerformedBy("manager");
        auditLogRepository.save(log);

        // Recompute totals from existing line items (don't re-aggregate from source)
        recomputeTotals(report);
        return reportRepository.save(report);
    }

    @Transactional
    public ShiftClosingReport transferEntry(Long sourceReportId, Long lineItemId,
                                            Long targetReportId, String reason) {
        ShiftClosingReport sourceReport = reportRepository.findById(sourceReportId)
                .orElseThrow(() -> new RuntimeException("Source report not found"));
        ShiftClosingReport targetReport = reportRepository.findById(targetReportId)
                .orElseThrow(() -> new RuntimeException("Target report not found"));

        if ("FINALIZED".equals(sourceReport.getStatus()) || "FINALIZED".equals(targetReport.getStatus())) {
            throw new RuntimeException("Cannot transfer entries involving finalized reports");
        }

        ReportLineItem lineItem = lineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Line item not found"));

        if (!lineItem.getReport().getId().equals(sourceReportId)) {
            throw new RuntimeException("Line item does not belong to source report");
        }

        // Move the source entity's shiftId to the target shift
        if (lineItem.getSourceEntityType() != null && lineItem.getSourceEntityId() != null) {
            transferSourceEntityShift(lineItem, targetReport.getShift().getId());
        }

        // Mark the line item as transferred
        lineItem.setTransferredToReportId(targetReportId);
        lineItemRepository.save(lineItem);

        // Create a copy in the target report
        ReportLineItem newItem = new ReportLineItem();
        newItem.setReport(targetReport);
        newItem.setSection(lineItem.getSection());
        newItem.setCategory(lineItem.getCategory());
        newItem.setLabel(lineItem.getLabel());
        newItem.setQuantity(lineItem.getQuantity());
        newItem.setRate(lineItem.getRate());
        newItem.setAmount(lineItem.getAmount());
        newItem.setSourceEntityType(lineItem.getSourceEntityType());
        newItem.setSourceEntityId(lineItem.getSourceEntityId());
        newItem.setSortOrder(lineItem.getSortOrder());
        newItem.setTransferredFromReportId(sourceReportId);
        lineItemRepository.save(newItem);

        // Audit logs in both reports
        ReportAuditLog sourceLog = new ReportAuditLog();
        sourceLog.setReport(sourceReport);
        sourceLog.setAction("ENTRY_TRANSFERRED_OUT");
        sourceLog.setDescription(reason != null ? reason : "Entry transferred out: " + lineItem.getLabel());
        sourceLog.setLineItemId(lineItemId);
        sourceLog.setPerformedBy("manager");
        auditLogRepository.save(sourceLog);

        ReportAuditLog targetLog = new ReportAuditLog();
        targetLog.setReport(targetReport);
        targetLog.setAction("ENTRY_TRANSFERRED_IN");
        targetLog.setDescription(reason != null ? reason : "Entry transferred in: " + lineItem.getLabel());
        targetLog.setLineItemId(newItem.getId());
        targetLog.setPerformedBy("manager");
        auditLogRepository.save(targetLog);

        // Recompute both reports
        recomputeTotals(sourceReport);
        recomputeTotals(targetReport);
        reportRepository.save(sourceReport);
        reportRepository.save(targetReport);

        return sourceReport;
    }

    @Transactional
    public ShiftClosingReport finalizeReport(Long reportId, String finalizedBy) {
        ShiftClosingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new RuntimeException("Report is already finalized");
        }

        report.setStatus("FINALIZED");
        report.setFinalizedBy(finalizedBy != null ? finalizedBy : "manager");
        report.setFinalizedAt(LocalDateTime.now());

        // Mark the shift as RECONCILED
        Shift shift = report.getShift();
        if (shift != null) {
            shift.setStatus("RECONCILED");
            shiftRepository.save(shift);
        }

        // Audit log
        ReportAuditLog log = new ReportAuditLog();
        log.setReport(report);
        log.setAction("FINALIZED");
        log.setDescription("Report finalized by " + report.getFinalizedBy());
        log.setPerformedBy(report.getFinalizedBy());
        auditLogRepository.save(log);

        return reportRepository.save(report);
    }

    @Transactional
    public ShiftClosingReport recomputeReport(Long reportId) {
        ShiftClosingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new RuntimeException("Cannot recompute a finalized report");
        }

        Shift shift = report.getShift();

        // Clear existing line items and breakdowns
        lineItemRepository.deleteByReportId(reportId);
        breakdownRepository.deleteByReportId(reportId);
        report.getLineItems().clear();
        report.getCashBillBreakdowns().clear();

        // Re-aggregate from source data
        populateReportData(report, shift);

        // Audit log
        ReportAuditLog log = new ReportAuditLog();
        log.setReport(report);
        log.setAction("RECOMPUTED");
        log.setDescription("Report recomputed from source data");
        log.setPerformedBy("system");
        auditLogRepository.save(log);

        return reportRepository.save(report);
    }

    public List<ReportAuditLog> getAuditLog(Long reportId) {
        return auditLogRepository.findByReportIdOrderByPerformedAtDesc(reportId);
    }

    // --- Private helpers ---

    private void populateReportData(ShiftClosingReport report, Shift shift) {
        Long shiftId = shift.getId();
        List<ReportLineItem> lineItems = new ArrayList<>();
        int sortOrder = 0;

        // === REVENUE SECTION ===

        // 1. Fuel Sales: group ALL invoices by product
        List<InvoiceBill> allInvoices = invoiceBillRepository.findByShiftId(shiftId);
        Map<String, double[]> fuelSales = new LinkedHashMap<>(); // productName -> [litres, amount, rate]

        BigDecimal cashBillTotal = BigDecimal.ZERO;
        BigDecimal creditBillTotal = BigDecimal.ZERO;

        for (InvoiceBill inv : allInvoices) {
            if ("CASH".equals(inv.getBillType())) {
                cashBillTotal = cashBillTotal.add(inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO);
            } else if ("CREDIT".equals(inv.getBillType())) {
                creditBillTotal = creditBillTotal.add(inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO);
            }

            if (inv.getProducts() != null) {
                for (InvoiceProduct ip : inv.getProducts()) {
                    String productName = ip.getProduct() != null ? ip.getProduct().getName() : "Unknown";
                    String category = ip.getProduct() != null ? ip.getProduct().getCategory() : "FUEL";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                    double amt = ip.getAmount() != null ? ip.getAmount().doubleValue() : 0;
                    double rate = ip.getUnitPrice() != null ? ip.getUnitPrice().doubleValue() : 0;

                    if ("FUEL".equalsIgnoreCase(category)) {
                        fuelSales.merge(productName, new double[]{qty, amt, rate},
                                (old, nw) -> new double[]{old[0] + nw[0], old[1] + nw[1], nw[2]});
                    } else {
                        // Oil/lubricant sales grouped separately
                        fuelSales.merge("OIL:" + productName, new double[]{qty, amt, rate},
                                (old, nw) -> new double[]{old[0] + nw[0], old[1] + nw[1], nw[2]});
                    }
                }
            }
        }

        // Add fuel product lines
        for (Map.Entry<String, double[]> entry : fuelSales.entrySet()) {
            String key = entry.getKey();
            double[] vals = entry.getValue();
            boolean isOil = key.startsWith("OIL:");
            String name = isOil ? key.substring(4) : key;

            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory(isOil ? "OIL_SALES" : "FUEL_SALES");
            item.setLabel(name);
            item.setQuantity(vals[0]);
            item.setRate(BigDecimal.valueOf(vals[2]).setScale(4, RoundingMode.HALF_UP));
            item.setAmount(BigDecimal.valueOf(vals[1]).setScale(4, RoundingMode.HALF_UP));
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 2. Bill Payments (payments against individual invoices in this shift)
        List<Payment> shiftPayments = paymentRepository.findByShiftId(shiftId);
        BigDecimal billPaymentTotal = BigDecimal.ZERO;
        BigDecimal statementPaymentTotal = BigDecimal.ZERO;

        for (Payment p : shiftPayments) {
            if (p.getInvoiceBill() != null) {
                billPaymentTotal = billPaymentTotal.add(p.getAmount());
            } else if (p.getStatement() != null) {
                statementPaymentTotal = statementPaymentTotal.add(p.getAmount());
            }
        }

        if (billPaymentTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("BILL_PAYMENT");
            item.setLabel("Bill Payments");
            item.setAmount(billPaymentTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 3. Statement Payments
        if (statementPaymentTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("STATEMENT_PAYMENT");
            item.setLabel("Statement Payments");
            item.setAmount(statementPaymentTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 4. External Cash Inflows
        List<ExternalCashInflow> inflows = inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId);
        BigDecimal inflowTotal = BigDecimal.ZERO;
        for (ExternalCashInflow inflow : inflows) {
            inflowTotal = inflowTotal.add(inflow.getAmount());
        }
        if (inflowTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("EXTERNAL_INFLOW");
            item.setLabel("External Cash Inflow");
            item.setAmount(inflowTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // === ADVANCE SECTION ===

        // 5. Credit Bills
        if (creditBillTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("CREDIT_BILLS");
            item.setLabel("Credit Bills");
            item.setAmount(creditBillTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 6-10. Electronic payment advances (from ShiftTransactions)
        addTransactionLineItem(lineItems, report, shiftId, "CARD",
                shiftTransactionRepository.sumCardByShift(shiftId), "Card Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "CCMS",
                shiftTransactionRepository.sumCcmsByShift(shiftId), "CCMS Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "UPI",
                shiftTransactionRepository.sumUpiByShift(shiftId), "UPI Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "BANK",
                shiftTransactionRepository.sumBankByShift(shiftId), "Bank Transfer Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "CHEQUE",
                shiftTransactionRepository.sumChequeByShift(shiftId), "Cheque Advance", ++sortOrder);

        // 11. Cash Advance & Home Advance
        List<CashAdvance> cashAdvances = cashAdvanceRepository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
        BigDecimal regularAdvanceTotal = BigDecimal.ZERO;
        BigDecimal homeAdvanceTotal = BigDecimal.ZERO;
        for (CashAdvance ca : cashAdvances) {
            if ("CANCELLED".equals(ca.getStatus())) continue;
            if ("HOME_ADVANCE".equals(ca.getAdvanceType())) {
                homeAdvanceTotal = homeAdvanceTotal.add(ca.getAmount());
            } else {
                regularAdvanceTotal = regularAdvanceTotal.add(ca.getAmount());
            }
        }

        if (regularAdvanceTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("CASH_ADVANCE");
            item.setLabel("Cash Advance");
            item.setAmount(regularAdvanceTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        if (homeAdvanceTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("HOME_ADVANCE");
            item.setLabel("Home Advance");
            item.setAmount(homeAdvanceTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 12. Expenses (sum of ExpenseTransactions, minus auto-created ones for advances/repayments)
        BigDecimal expenseTotal = shiftTransactionRepository.sumExpenseByShift(shiftId);
        // Subtract auto-created expense transactions for cash advances and inflow repayments
        // to avoid double counting (they're already in Cash Advance / Inflow Repayment lines)
        BigDecimal autoAdvanceExpenses = BigDecimal.ZERO;
        for (CashAdvance ca : cashAdvances) {
            if (!"CANCELLED".equals(ca.getStatus())) {
                autoAdvanceExpenses = autoAdvanceExpenses.add(ca.getAmount());
            }
        }
        List<CashInflowRepayment> repayments = repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId);
        BigDecimal autoRepaymentExpenses = BigDecimal.ZERO;
        for (CashInflowRepayment r : repayments) {
            autoRepaymentExpenses = autoRepaymentExpenses.add(r.getAmount());
        }
        BigDecimal pureExpenses = expenseTotal.subtract(autoAdvanceExpenses).subtract(autoRepaymentExpenses);
        if (pureExpenses.compareTo(BigDecimal.ZERO) < 0) {
            pureExpenses = BigDecimal.ZERO;
        }

        if (pureExpenses.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("EXPENSES");
            item.setLabel("Expenses");
            item.setAmount(pureExpenses);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 13. Incentive (total discount from invoices)
        BigDecimal incentiveTotal = BigDecimal.ZERO;
        for (InvoiceBill inv : allInvoices) {
            if (inv.getTotalDiscount() != null && inv.getTotalDiscount().compareTo(BigDecimal.ZERO) > 0) {
                incentiveTotal = incentiveTotal.add(inv.getTotalDiscount());
            }
        }
        if (incentiveTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("INCENTIVE");
            item.setLabel("Incentive / Discount");
            item.setAmount(incentiveTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 14. Salary Advance (employee advances during shift's date)
        if (shift.getStartTime() != null) {
            java.time.LocalDate shiftDate = shift.getStartTime().toLocalDate();
            List<EmployeeAdvance> empAdvances = employeeAdvanceRepository
                    .findByAdvanceDateBetween(shiftDate, shiftDate);
            BigDecimal salaryAdvanceTotal = BigDecimal.ZERO;
            for (EmployeeAdvance ea : empAdvances) {
                if (ea.getAmount() != null) {
                    salaryAdvanceTotal = salaryAdvanceTotal.add(BigDecimal.valueOf(ea.getAmount()));
                }
            }
            if (salaryAdvanceTotal.compareTo(BigDecimal.ZERO) > 0) {
                ReportLineItem item = new ReportLineItem();
                item.setReport(report);
                item.setSection("ADVANCE");
                item.setCategory("SALARY_ADVANCE");
                item.setLabel("Salary Advance");
                item.setAmount(salaryAdvanceTotal);
                item.setSortOrder(++sortOrder);
                lineItems.add(item);
            }
        }

        // 15. Inflow Repayments
        BigDecimal repaymentTotal = BigDecimal.ZERO;
        for (CashInflowRepayment r : repayments) {
            repaymentTotal = repaymentTotal.add(r.getAmount());
        }
        if (repaymentTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("INFLOW_REPAYMENT");
            item.setLabel("Cash Inflow Repayment");
            item.setAmount(repaymentTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // Save all line items
        lineItemRepository.saveAll(lineItems);
        report.getLineItems().addAll(lineItems);

        // === CASH BILL BREAKDOWN ===
        List<ReportCashBillBreakdown> breakdowns = computeCashBillBreakdown(report, allInvoices);
        breakdownRepository.saveAll(breakdowns);
        report.getCashBillBreakdowns().addAll(breakdowns);

        // === COMPUTE TOTALS ===
        report.setCashBillAmount(cashBillTotal);
        report.setCreditBillAmount(creditBillTotal);
        recomputeTotals(report);
    }

    private void addTransactionLineItem(List<ReportLineItem> items, ShiftClosingReport report,
                                         Long shiftId, String category, BigDecimal amount,
                                         String label, int sortOrder) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory(category);
            item.setLabel(label);
            item.setAmount(amount);
            item.setSourceEntityType("ShiftTransaction");
            item.setSortOrder(sortOrder);
            items.add(item);
        }
    }

    private List<ReportCashBillBreakdown> computeCashBillBreakdown(ShiftClosingReport report,
                                                                     List<InvoiceBill> allInvoices) {
        // Only CASH invoices for breakdown
        Map<String, ReportCashBillBreakdown> productBreakdowns = new LinkedHashMap<>();

        for (InvoiceBill inv : allInvoices) {
            if (!"CASH".equals(inv.getBillType())) continue;

            String paymentMode = inv.getPaymentMode() != null ? inv.getPaymentMode().toUpperCase() : "CASH";

            if (inv.getProducts() != null) {
                for (InvoiceProduct ip : inv.getProducts()) {
                    String productName = ip.getProduct() != null ? ip.getProduct().getName() : "Unknown";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;

                    ReportCashBillBreakdown bd = productBreakdowns.computeIfAbsent(productName, k -> {
                        ReportCashBillBreakdown b = new ReportCashBillBreakdown();
                        b.setReport(report);
                        b.setProductName(k);
                        return b;
                    });

                    switch (paymentMode) {
                        case "CARD":
                            bd.setCardLitres(bd.getCardLitres() + qty);
                            break;
                        case "CCMS":
                            bd.setCcmsLitres(bd.getCcmsLitres() + qty);
                            break;
                        case "UPI":
                            bd.setUpiLitres(bd.getUpiLitres() + qty);
                            break;
                        case "CHEQUE":
                            bd.setChequeLitres(bd.getChequeLitres() + qty);
                            break;
                        default:
                            bd.setCashLitres(bd.getCashLitres() + qty);
                            break;
                    }
                    bd.setTotalLitres(bd.getCashLitres() + bd.getCardLitres() + bd.getCcmsLitres()
                            + bd.getUpiLitres() + bd.getChequeLitres());
                }
            }
        }

        return new ArrayList<>(productBreakdowns.values());
    }

    private void recomputeTotals(ShiftClosingReport report) {
        List<ReportLineItem> items = lineItemRepository.findByReportIdOrderBySortOrder(report.getId());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalAdvances = BigDecimal.ZERO;

        for (ReportLineItem item : items) {
            // Skip transferred-out items
            if (item.getTransferredToReportId() != null) continue;

            if ("REVENUE".equals(item.getSection())) {
                totalRevenue = totalRevenue.add(item.getAmount());
            } else if ("ADVANCE".equals(item.getSection())) {
                totalAdvances = totalAdvances.add(item.getAmount());
            }
        }

        report.setTotalRevenue(totalRevenue);
        report.setTotalAdvances(totalAdvances);
        report.setBalance(totalRevenue.subtract(totalAdvances));
    }

    private void cascadeEditToSource(ReportLineItem lineItem, BigDecimal newAmount) {
        if (lineItem.getSourceEntityType() == null || lineItem.getSourceEntityId() == null) {
            return;
        }

        if ("ShiftTransaction".equals(lineItem.getSourceEntityType())) {
            ShiftTransaction txn = shiftTransactionRepository.findById(lineItem.getSourceEntityId()).orElse(null);
            if (txn != null) {
                txn.setReceivedAmount(newAmount);
                shiftTransactionRepository.save(txn);

                // Check if this was auto-created from a Payment
                if (txn.getRemarks() != null && txn.getRemarks().startsWith("Auto: Payment #")) {
                    String paymentIdStr = txn.getRemarks().replace("Auto: Payment #", "").split(" ")[0];
                    try {
                        Long paymentId = Long.parseLong(paymentIdStr);
                        Payment payment = paymentRepository.findById(paymentId).orElse(null);
                        if (payment != null) {
                            payment.setAmount(newAmount);
                            paymentRepository.save(payment);

                            // Recalculate invoice/statement
                            recalculatePaymentTarget(payment);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private void recalculatePaymentTarget(Payment payment) {
        if (payment.getInvoiceBill() != null) {
            InvoiceBill bill = payment.getInvoiceBill();
            BigDecimal totalReceived = paymentRepository.sumPaymentsByInvoiceBillId(bill.getId());
            if (totalReceived.compareTo(bill.getNetAmount()) >= 0) {
                bill.setPaymentStatus("PAID");
            } else {
                bill.setPaymentStatus("NOT_PAID");
            }
            invoiceBillRepository.save(bill);
        }

        if (payment.getStatement() != null) {
            Statement statement = payment.getStatement();
            BigDecimal totalReceived = paymentRepository.sumPaymentsByStatementId(statement.getId());
            statement.setReceivedAmount(totalReceived);
            statement.setBalanceAmount(statement.getNetAmount().subtract(totalReceived));
            if (statement.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                statement.setStatus("PAID");
                statement.setBalanceAmount(BigDecimal.ZERO);
            } else {
                statement.setStatus("NOT_PAID");
            }
            statementRepository.save(statement);
        }
    }

    private void transferSourceEntityShift(ReportLineItem lineItem, Long targetShiftId) {
        if ("ShiftTransaction".equals(lineItem.getSourceEntityType())) {
            ShiftTransaction txn = shiftTransactionRepository.findById(lineItem.getSourceEntityId()).orElse(null);
            if (txn != null) {
                txn.setShiftId(targetShiftId);
                shiftTransactionRepository.save(txn);
            }
        }
    }

    // === PRINT DATA ===

    public ShiftReportPrintData getPrintData(Long shiftId) {
        ShiftClosingReport report = reportRepository.findByShiftId(shiftId)
                .orElseThrow(() -> new RuntimeException("Report not found for shift: " + shiftId));
        Shift shift = report.getShift();

        ShiftReportPrintData data = new ShiftReportPrintData();

        // Header
        List<Company> companies = companyRepository.findByScid(shift.getScid() != null ? shift.getScid() : 1L);
        data.setCompanyName(!companies.isEmpty() ? companies.get(0).getName() : "StopForFuel");
        data.setEmployeeName(shift.getAttendant() != null ? shift.getAttendant().getName() : "-");
        data.setShiftId(shift.getId());
        data.setShiftStart(shift.getStartTime());
        data.setShiftEnd(shift.getEndTime());
        data.setReportStatus(report.getStatus());

        // Meter Readings
        List<NozzleInventory> nozzleInvs = nozzleInventoryRepository.findByShiftId(shiftId);
        for (NozzleInventory ni : nozzleInvs) {
            ShiftReportPrintData.MeterReading mr = new ShiftReportPrintData.MeterReading();
            mr.setPumpName(ni.getNozzle().getPump() != null ? ni.getNozzle().getPump().getName() : "-");
            mr.setNozzleName(ni.getNozzle().getNozzleName());
            mr.setProductName(ni.getNozzle().getTank() != null && ni.getNozzle().getTank().getProduct() != null
                    ? ni.getNozzle().getTank().getProduct().getName() : "-");
            mr.setOpenReading(ni.getOpenMeterReading());
            mr.setCloseReading(ni.getCloseMeterReading());
            mr.setSales(ni.getSales());
            data.getMeterReadings().add(mr);
        }

        // Tank Readings
        List<TankInventory> tankInvs = tankInventoryRepository.findByShiftId(shiftId);
        for (TankInventory ti : tankInvs) {
            ShiftReportPrintData.TankReading tr = new ShiftReportPrintData.TankReading();
            tr.setTankName(ti.getTank().getName());
            tr.setProductName(ti.getTank().getProduct() != null ? ti.getTank().getProduct().getName() : "-");
            tr.setOpenDip(ti.getOpenDip());
            tr.setOpenStock(ti.getOpenStock());
            tr.setIncomeStock(ti.getIncomeStock());
            tr.setTotalStock(ti.getTotalStock());
            tr.setCloseDip(ti.getCloseDip());
            tr.setCloseStock(ti.getCloseStock());
            tr.setSaleStock(ti.getSaleStock());
            data.getTankReadings().add(tr);
        }

        // Sales Difference (tank sale vs meter sale by product)
        Map<String, double[]> tankSalesByProduct = new LinkedHashMap<>();
        for (TankInventory ti : tankInvs) {
            String productName = ti.getTank().getProduct() != null ? ti.getTank().getProduct().getName() : "Unknown";
            double sale = ti.getSaleStock() != null ? ti.getSaleStock() : 0;
            tankSalesByProduct.merge(productName, new double[]{sale}, (o, n) -> new double[]{o[0] + n[0]});
        }
        Map<String, double[]> meterSalesByProduct = new LinkedHashMap<>();
        for (NozzleInventory ni : nozzleInvs) {
            String productName = ni.getNozzle().getTank() != null && ni.getNozzle().getTank().getProduct() != null
                    ? ni.getNozzle().getTank().getProduct().getName() : "Unknown";
            double sale = ni.getSales() != null ? ni.getSales() : 0;
            meterSalesByProduct.merge(productName, new double[]{sale}, (o, n) -> new double[]{o[0] + n[0]});
        }
        Set<String> allProducts = new LinkedHashSet<>();
        allProducts.addAll(tankSalesByProduct.keySet());
        allProducts.addAll(meterSalesByProduct.keySet());
        for (String product : allProducts) {
            double tankSale = tankSalesByProduct.containsKey(product) ? tankSalesByProduct.get(product)[0] : 0;
            double meterSale = meterSalesByProduct.containsKey(product) ? meterSalesByProduct.get(product)[0] : 0;
            ShiftReportPrintData.SalesDifference sd = new ShiftReportPrintData.SalesDifference();
            sd.setProductName(product);
            sd.setTankSale(tankSale);
            sd.setMeterSale(meterSale);
            sd.setDifference(tankSale - meterSale);
            data.getSalesDifferences().add(sd);
        }

        // Credit Bill Details (grouped by customer)
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);
        List<InvoiceBill> creditBills = invoices.stream()
                .filter(inv -> "CREDIT".equals(inv.getBillType()))
                .sorted(Comparator.comparing(inv -> inv.getCustomer() != null ? inv.getCustomer().getName() : ""))
                .collect(Collectors.toList());

        for (InvoiceBill bill : creditBills) {
            ShiftReportPrintData.CreditBillDetail cbd = new ShiftReportPrintData.CreditBillDetail();
            cbd.setCustomerName(bill.getCustomer() != null ? bill.getCustomer().getName() : "-");
            cbd.setBillNo(bill.getBillNo());
            cbd.setVehicleNo(bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-");
            cbd.setAmount(bill.getNetAmount());

            // Compact products: "P:500 HSD:200"
            StringBuilder prodStr = new StringBuilder();
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) {
                    String pName = ip.getProduct() != null ? abbreviateProduct(ip.getProduct().getName()) : "?";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                    if (prodStr.length() > 0) prodStr.append(" ");
                    prodStr.append(pName).append(":").append(String.format("%.0f", qty));
                }
            }
            cbd.setProducts(prodStr.toString());
            data.getCreditBillDetails().add(cbd);
        }

        // Stock Summary — only products with sales > 0
        // Build a map of ProductInventory records for this shift by product ID
        List<ProductInventory> shiftProductInvs = productInventoryRepository.findByShiftId(shiftId);
        Map<Long, ProductInventory> productInvMap = new HashMap<>();
        for (ProductInventory pi : shiftProductInvs) {
            productInvMap.put(pi.getProduct().getId(), pi);
        }

        List<Product> allProductEntities = productRepository.findByActive(true);
        for (Product product : allProductEntities) {
            ShiftReportPrintData.StockSummaryRow row = new ShiftReportPrintData.StockSummaryRow();
            row.setProductName(product.getName());
            row.setRate(product.getPrice());

            if ("FUEL".equalsIgnoreCase(product.getCategory())) {
                // For fuel products, use tank data
                double open = 0, receipt = 0, total = 0, sales = 0;
                for (TankInventory ti : tankInvs) {
                    if (ti.getTank().getProduct() != null && ti.getTank().getProduct().getId().equals(product.getId())) {
                        open += ti.getOpenStock() != null ? ti.getOpenStock() : 0;
                        receipt += ti.getIncomeStock() != null ? ti.getIncomeStock() : 0;
                        total += ti.getTotalStock() != null ? ti.getTotalStock() : 0;
                        sales += ti.getSaleStock() != null ? ti.getSaleStock() : 0;
                    }
                }
                if (sales == 0) continue; // skip fuel with zero sales
                row.setOpenStock(open);
                row.setReceipt(receipt);
                row.setTotalStock(total);
                row.setSales(sales);
            } else {
                // Non-fuel: use ProductInventory records from the shift
                ProductInventory pi = productInvMap.get(product.getId());
                if (pi != null) {
                    double sales = pi.getSales() != null ? pi.getSales() : 0;
                    if (sales == 0) continue; // skip non-fuel with zero sales
                    row.setOpenStock(pi.getOpenStock() != null ? pi.getOpenStock() : 0);
                    row.setReceipt(pi.getIncomeStock() != null ? pi.getIncomeStock() : 0);
                    row.setTotalStock(pi.getTotalStock() != null ? pi.getTotalStock() : 0);
                    row.setSales(sales);
                } else {
                    // Fallback: calculate from invoices (if no ProductInventory exists)
                    double sales = 0;
                    for (InvoiceBill inv : invoices) {
                        if (inv.getProducts() != null) {
                            for (InvoiceProduct ip : inv.getProducts()) {
                                if (ip.getProduct() != null && ip.getProduct().getId().equals(product.getId())) {
                                    sales += ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                                }
                            }
                        }
                    }
                    if (sales == 0) continue;
                    row.setSales(sales);
                    row.setOpenStock(0.0);
                    row.setReceipt(0.0);
                    row.setTotalStock(0.0);
                }
            }

            BigDecimal salesAmt = product.getPrice() != null && row.getSales() != null
                    ? product.getPrice().multiply(BigDecimal.valueOf(row.getSales()))
                    : BigDecimal.ZERO;
            row.setAmount(salesAmt.setScale(2, RoundingMode.HALF_UP));
            data.getStockSummary().add(row);
        }

        // Advance Entry Details (individual transactions)
        List<ShiftTransaction> allTxns = shiftTransactionRepository.findByShiftId(shiftId);
        for (ShiftTransaction txn : allTxns) {
            String txnType = txn.getClass().getSimpleName().replace("Transaction", "").toUpperCase();
            if ("CASH".equals(txnType) || "NIGHTCASH".equals(txnType)) continue; // skip cash-in transactions
            if ("EXPENSE".equals(txnType)) {
                // Check if it's an auto-created expense for advance/repayment — skip those
                if (txn.getRemarks() != null && (txn.getRemarks().startsWith("Auto: Advance #")
                        || txn.getRemarks().startsWith("Auto: Inflow Repayment"))) {
                    continue;
                }
            }

            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType(txnType);
            entry.setDescription(txn.getRemarks() != null ? txn.getRemarks() : "-");
            entry.setAmount(txn.getReceivedAmount());
            data.getAdvanceEntries().add(entry);
        }

        // Add cash advances as advance entries
        List<CashAdvance> cashAdvances = cashAdvanceRepository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
        for (CashAdvance ca : cashAdvances) {
            if ("CANCELLED".equals(ca.getStatus())) continue;
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType("HOME_ADVANCE".equals(ca.getAdvanceType()) ? "HOME_ADV" : "CASH_ADV");
            entry.setDescription(ca.getRecipientName() != null ? ca.getRecipientName() : "-");
            entry.setAmount(ca.getAmount());
            entry.setReference(ca.getPurpose());
            data.getAdvanceEntries().add(entry);
        }

        // Add inflow repayments
        List<CashInflowRepayment> repayments = repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId);
        for (CashInflowRepayment r : repayments) {
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType("REPAYMENT");
            entry.setDescription(r.getCashInflow() != null ? r.getCashInflow().getSource() : "-");
            entry.setAmount(r.getAmount());
            data.getAdvanceEntries().add(entry);
        }

        // Add incentive from invoices
        BigDecimal incentiveTotal = BigDecimal.ZERO;
        for (InvoiceBill inv : invoices) {
            if (inv.getTotalDiscount() != null && inv.getTotalDiscount().compareTo(BigDecimal.ZERO) > 0) {
                incentiveTotal = incentiveTotal.add(inv.getTotalDiscount());
            }
        }
        if (incentiveTotal.compareTo(BigDecimal.ZERO) > 0) {
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType("INCENTIVE");
            entry.setDescription("Discounts given");
            entry.setAmount(incentiveTotal);
            data.getAdvanceEntries().add(entry);
        }

        // Add salary advances
        if (shift.getStartTime() != null) {
            java.time.LocalDate shiftDate = shift.getStartTime().toLocalDate();
            List<EmployeeAdvance> empAdvances = employeeAdvanceRepository.findByAdvanceDateBetween(shiftDate, shiftDate);
            for (EmployeeAdvance ea : empAdvances) {
                ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
                entry.setType("SAL_ADV");
                entry.setDescription(ea.getEmployee() != null ? ea.getEmployee().getName() : "-");
                entry.setAmount(ea.getAmount() != null ? BigDecimal.valueOf(ea.getAmount()) : BigDecimal.ZERO);
                data.getAdvanceEntries().add(entry);
            }
        }

        // Payment Entries (bill and statement payments)
        List<Payment> shiftPayments = paymentRepository.findByShiftId(shiftId);
        for (Payment p : shiftPayments) {
            ShiftReportPrintData.PaymentEntryDetail pe = new ShiftReportPrintData.PaymentEntryDetail();
            pe.setCustomerName(p.getCustomer() != null ? p.getCustomer().getName() : "-");
            pe.setPaymentMode(p.getPaymentMode() != null ? p.getPaymentMode().getModeName() : "CASH");
            pe.setAmount(p.getAmount());

            if (p.getInvoiceBill() != null) {
                pe.setType("BILL");
                pe.setReference(p.getInvoiceBill().getBillNo());
            } else if (p.getStatement() != null) {
                pe.setType("STMT");
                pe.setReference(p.getStatement().getStatementNo());
            } else {
                pe.setType("OTHER");
                pe.setReference("-");
            }
            data.getPaymentEntries().add(pe);
        }

        return data;
    }

    private String abbreviateProduct(String name) {
        if (name == null) return "?";
        String upper = name.toUpperCase();
        if (upper.contains("PETROL") || upper.equals("MS")) return "P";
        if (upper.contains("XTRA") || upper.contains("XP") || upper.contains("PREMIUM")) return "XP";
        if (upper.contains("DIESEL") || upper.equals("HSD") || upper.contains("HIGH SPEED")) return "HSD";
        // For non-fuel, return first 3 chars
        return name.length() > 4 ? name.substring(0, 4) : name;
    }
}
