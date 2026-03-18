package com.stopforfuel.backend.service;

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
}
