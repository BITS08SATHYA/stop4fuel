package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

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
    private final EAdvanceRepository eAdvanceRepository;
    private final StatementRepository statementRepository;
    private final CompanyRepository companyRepository;
    private final ShiftReportPdfGenerator pdfGenerator;
    private final S3StorageService s3StorageService;
    private final ShiftSalesCalculationService salesCalculationService;
    private final ShiftFinancialCalculationService financialCalculationService;

    @Transactional
    public ShiftClosingReport generateReport(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        if (shift.getStatus() != com.stopforfuel.backend.enums.ShiftStatus.REVIEW && shift.getStatus() != com.stopforfuel.backend.enums.ShiftStatus.CLOSED && shift.getStatus() != com.stopforfuel.backend.enums.ShiftStatus.RECONCILED) {
            throw new BusinessException("Shift must be in REVIEW or CLOSED state before generating a report");
        }

        // If report already exists, recompute it
        Optional<ShiftClosingReport> existing = reportRepository.findByShift_Id(shiftId);
        if (existing.isPresent()) {
            return recomputeReport(existing.get().getId());
        }

        ShiftClosingReport report = new ShiftClosingReport();
        report.setShift(shift);
        report.setScid(shift.getScid());
        report.setStatus("DRAFT");
        report.setReportDate(LocalDateTime.now());

        ShiftClosingReport saved = reportRepository.save(report);
        populateReportData(saved, shift);
        return reportRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public ShiftClosingReport getReport(Long shiftId) {
        ShiftClosingReport report = reportRepository.findByShift_Id(shiftId)
                .orElseThrow(() -> new RuntimeException("Report not found for shift: " + shiftId));
        // Initialize lazy collections within transaction
        report.getCashBillBreakdowns().size();
        report.getAuditLogs().size();
        return report;
    }

    @Transactional(readOnly = true)
    public ShiftClosingReport getReportById(Long reportId) {
        return reportRepository.findByIdAndScid(reportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Report not found: " + reportId));
    }

    @Transactional(readOnly = true)
    public List<ShiftClosingReport> getAllReports(String status) {
        if (status != null && !status.isEmpty()) {
            return reportRepository.findByStatusOrderByReportDateDesc(status);
        }
        return reportRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional
    public ShiftClosingReport editLineItem(Long reportId, Long lineItemId, BigDecimal newAmount, String reason) {
        ShiftClosingReport report = reportRepository.findByIdAndScid(reportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new BusinessException("Cannot edit a finalized report");
        }

        ReportLineItem lineItem = lineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Line item not found"));

        if (!lineItem.getReport().getId().equals(reportId)) {
            throw new BusinessException("Line item does not belong to this report");
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
        ShiftClosingReport sourceReport = reportRepository.findByIdAndScid(sourceReportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Source report not found"));
        ShiftClosingReport targetReport = reportRepository.findByIdAndScid(targetReportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Target report not found"));

        if ("FINALIZED".equals(sourceReport.getStatus()) || "FINALIZED".equals(targetReport.getStatus())) {
            throw new BusinessException("Cannot transfer entries involving finalized reports");
        }

        ReportLineItem lineItem = lineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Line item not found"));

        if (!lineItem.getReport().getId().equals(sourceReportId)) {
            throw new BusinessException("Line item does not belong to source report");
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
        ShiftClosingReport report = reportRepository.findByIdAndScid(reportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new BusinessException("Report is already finalized");
        }

        report.setStatus("FINALIZED");
        report.setFinalizedBy(finalizedBy != null ? finalizedBy : "manager");
        report.setFinalizedAt(LocalDateTime.now());

        // Mark the shift as RECONCILED
        Shift shift = report.getShift();
        if (shift != null) {
            shift.setStatus(com.stopforfuel.backend.enums.ShiftStatus.RECONCILED);
            shiftRepository.save(shift);
        }

        // Generate and store PDF
        try {
            ShiftReportPrintData printData = getPrintData(shift.getId());
            byte[] pdfBytes = pdfGenerator.generate(printData, report);

            LocalDateTime shiftStart = shift.getStartTime() != null ? shift.getStartTime() : LocalDateTime.now();
            Long scid = report.getScid() != null ? report.getScid() : (shift.getScid() != null ? shift.getScid() : 1L);
            String key = String.format("reports/shift-closing/%d/%d/%02d/%02d/shift-%d.pdf",
                    scid, shiftStart.getYear(), shiftStart.getMonthValue(), shiftStart.getDayOfMonth(),
                    shift.getId());

            s3StorageService.upload(key, pdfBytes, "application/pdf");
            report.setReportPdfUrl(key);
        } catch (Exception e) {
            // Log but don't fail finalization if PDF generation fails
            System.err.println("Failed to generate shift report PDF: " + e.getMessage());
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
        ShiftClosingReport report = reportRepository.findByIdAndScid(reportId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Report not found"));

        if ("FINALIZED".equals(report.getStatus())) {
            throw new BusinessException("Cannot recompute a finalized report");
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

    @Transactional(readOnly = true)
    public String getReportPdfUrl(Long shiftId) {
        ShiftClosingReport report = reportRepository.findByShift_Id(shiftId)
                .orElseThrow(() -> new RuntimeException("Report not found for shift: " + shiftId));
        if (report.getReportPdfUrl() == null || report.getReportPdfUrl().isEmpty()) {
            throw new ResourceNotFoundException("No PDF generated for this report");
        }
        return s3StorageService.getPresignedUrl(report.getReportPdfUrl());
    }

    @Transactional(readOnly = true)
    public List<ReportAuditLog> getAuditLog(Long reportId) {
        return auditLogRepository.findByReportIdOrderByPerformedAtDesc(reportId);
    }

    // --- Private helpers ---

    private void populateReportData(ShiftClosingReport report, Shift shift) {
        Long shiftId = shift.getId();

        // === REVENUE SECTION (delegated to ShiftSalesCalculationService) ===
        ShiftSalesCalculationService.SalesResult salesResult =
                salesCalculationService.computeSalesLineItems(report, shiftId, 0);
        List<ReportLineItem> allLineItems = new ArrayList<>(salesResult.getLineItems());

        // === ADVANCE SECTION (delegated to ShiftFinancialCalculationService) ===
        int nextSortOrder = salesResult.getLineItems().stream()
                .mapToInt(ReportLineItem::getSortOrder).max().orElse(0);
        List<ReportLineItem> financialItems = financialCalculationService.computeFinancialLineItems(
                report, shiftId, salesResult.getCreditBillTotal(), nextSortOrder);
        allLineItems.addAll(financialItems);

        // Save all line items
        lineItemRepository.saveAll(allLineItems);
        report.getLineItems().addAll(allLineItems);

        // === CASH BILL BREAKDOWN ===
        List<ReportCashBillBreakdown> breakdowns =
                salesCalculationService.computeCashBillBreakdown(report, salesResult.getAllInvoices());
        breakdownRepository.saveAll(breakdowns);
        report.getCashBillBreakdowns().addAll(breakdowns);

        // === COMPUTE TOTALS ===
        report.setCashBillAmount(salesResult.getCashBillTotal());
        report.setCreditBillAmount(salesResult.getCreditBillTotal());
        recomputeTotals(report);
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

        if ("EAdvance".equals(lineItem.getSourceEntityType())) {
            EAdvance eAdv = eAdvanceRepository.findById(lineItem.getSourceEntityId()).orElse(null);
            if (eAdv != null) {
                eAdv.setAmount(newAmount);
                eAdvanceRepository.save(eAdv);

                // Check if this was auto-created from a Payment
                if (eAdv.getRemarks() != null && eAdv.getRemarks().startsWith("Auto: Payment #")) {
                    String paymentIdStr = eAdv.getRemarks().replace("Auto: Payment #", "").split(" ")[0];
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
                bill.setPaymentStatus(com.stopforfuel.backend.enums.PaymentStatus.PAID);
            } else {
                bill.setPaymentStatus(com.stopforfuel.backend.enums.PaymentStatus.NOT_PAID);
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
        if ("EAdvance".equals(lineItem.getSourceEntityType())) {
            EAdvance eAdv = eAdvanceRepository.findById(lineItem.getSourceEntityId()).orElse(null);
            if (eAdv != null) {
                eAdv.setShiftId(targetShiftId);
                eAdvanceRepository.save(eAdv);
            }
        }
    }

    // === PRINT DATA ===

    @Transactional(readOnly = true)
    public ShiftReportPrintData getPrintData(Long shiftId) {
        ShiftClosingReport report = reportRepository.findByShift_Id(shiftId)
                .orElseThrow(() -> new RuntimeException("Report not found for shift: " + shiftId));
        Shift shift = report.getShift();

        ShiftReportPrintData data = new ShiftReportPrintData();

        // Header
        List<Company> companies = companyRepository.findByScid(shift.getScid() != null ? shift.getScid() : SecurityUtils.getScid());
        if (!companies.isEmpty()) {
            Company company = companies.get(0);
            data.setCompanyName(company.getName() != null ? company.getName() : "StopForFuel");
            data.setCompanyAddress(company.getAddress());
            data.setCompanyGstNo(company.getGstNo());
            data.setCompanyPhone(company.getPhone());
            data.setCompanyEmail(company.getEmail());
        } else {
            data.setCompanyName("StopForFuel");
        }
        data.setEmployeeName(shift.getAttendant() != null ? shift.getAttendant().getName() : "-");
        data.setShiftId(shift.getId());
        data.setShiftStart(shift.getStartTime());
        data.setShiftEnd(shift.getEndTime());
        data.setReportStatus(report.getStatus());

        // Delegate sales-related print data sections
        salesCalculationService.populateMeterReadings(data, shiftId);
        salesCalculationService.populateTankReadings(data, shiftId);
        salesCalculationService.populateSalesDifferences(data, shiftId);
        salesCalculationService.populateBillDetails(data, shiftId);
        salesCalculationService.populateStockData(data, shiftId);

        // Delegate financial print data sections
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);
        financialCalculationService.populateAdvanceEntries(data, shiftId, invoices);
        financialCalculationService.populatePaymentEntries(data, shiftId);

        return data;
    }
}
