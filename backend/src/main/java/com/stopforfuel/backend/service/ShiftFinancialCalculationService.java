package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Handles all financial calculation logic for shift closing reports:
 * cash collection, e-advance totals (card/UPI/cheque/CCMS), expense summaries,
 * operational advance summaries, payment collections, cash-in-hand, turnover.
 */
@Service
@RequiredArgsConstructor
public class ShiftFinancialCalculationService {

    private final EAdvanceRepository eAdvanceRepository;
    private final OperationalAdvanceRepository operationalAdvanceRepository;
    private final ExpenseRepository expenseRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;
    private final CashInflowRepaymentRepository repaymentRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Compute all ADVANCE-section line items for the given shift.
     * Returns the list of line items starting from the given sort order.
     * The creditBillTotal from sales is passed in to create the CREDIT_BILLS line item.
     */
    @Transactional(readOnly = true)
    public List<ReportLineItem> computeFinancialLineItems(ShiftClosingReport report, Long shiftId,
                                                           BigDecimal creditBillTotal, int startSortOrder) {
        List<ReportLineItem> lineItems = new ArrayList<>();
        int sortOrder = startSortOrder;

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

        // 6-10. Electronic payment advances (from e_advance table)
        addTransactionLineItem(lineItems, report, shiftId, "CARD",
                eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.EAdvanceType.CARD), "Card Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "CCMS",
                eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.EAdvanceType.CCMS), "CCMS Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "UPI",
                eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.EAdvanceType.UPI), "UPI Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "BANK",
                eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.EAdvanceType.BANK_TRANSFER), "Bank Transfer Advance", ++sortOrder);
        addTransactionLineItem(lineItems, report, shiftId, "CHEQUE",
                eAdvanceRepository.sumByShiftAndType(shiftId, com.stopforfuel.backend.enums.EAdvanceType.CHEQUE), "Cheque Advance", ++sortOrder);

        // 11. Operational Advances (Cash, Salary, Management)
        List<OperationalAdvance> opAdvances = operationalAdvanceRepository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
        Map<String, BigDecimal> opAdvanceTotals = new HashMap<>();
        for (OperationalAdvance oa : opAdvances) {
            if (oa.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.CANCELLED) continue;
            String type = oa.getAdvanceType() != null ? oa.getAdvanceType().name() : "CASH";
            opAdvanceTotals.merge(type, oa.getAmount(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : opAdvanceTotals.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                ReportLineItem item = new ReportLineItem();
                item.setReport(report);
                item.setSection("ADVANCE");
                item.setCategory(entry.getKey() + "_ADVANCE");
                item.setLabel(entry.getKey().substring(0, 1) + entry.getKey().substring(1).toLowerCase() + " Advance");
                item.setAmount(entry.getValue());
                item.setSortOrder(++sortOrder);
                lineItems.add(item);
            }
        }

        // 12. Expenses (from expense table)
        BigDecimal expenseTotal = expenseRepository.sumByShift(shiftId);
        if (expenseTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("ADVANCE");
            item.setCategory("EXPENSES");
            item.setLabel("Expenses");
            item.setAmount(expenseTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 13. Incentive (from incentive_payment table)
        BigDecimal incentiveTotal = incentivePaymentRepository.sumByShift(shiftId);
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

        // 14. Inflow Repayments
        List<CashInflowRepayment> repayments = repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId);
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

        return lineItems;
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
            item.setSourceEntityType("EAdvance");
            item.setSortOrder(sortOrder);
            items.add(item);
        }
    }

    // === PRINT DATA: Financial sections ===

    /**
     * Populate advance entry details into print data (e-advances, operational advances, repayments, incentives).
     */
    @Transactional(readOnly = true)
    public void populateAdvanceEntries(ShiftReportPrintData data, Long shiftId, List<InvoiceBill> invoices) {
        // EAdvance entries (Card, UPI, Cheque, CCMS, Bank)
        List<EAdvance> eAdvances = eAdvanceRepository.findByShiftIdOrderByTransactionDateDesc(shiftId);
        for (EAdvance eAdv : eAdvances) {
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType(eAdv.getAdvanceType() != null ? eAdv.getAdvanceType().name() : "OTHER");
            entry.setDescription(eAdv.getRemarks() != null ? eAdv.getRemarks() : "-");
            entry.setAmount(eAdv.getAmount());
            data.getAdvanceEntries().add(entry);
        }

        // Operational advances
        List<OperationalAdvance> opAdvances = operationalAdvanceRepository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
        for (OperationalAdvance oa : opAdvances) {
            if (oa.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.CANCELLED) continue;
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType(oa.getAdvanceType() != null ? oa.getAdvanceType().name() : "CASH");
            String desc = oa.getRecipientName() != null ? oa.getRecipientName() : "-";
            StringBuilder linkInfo = new StringBuilder();
            if (oa.getInvoiceBills() != null && !oa.getInvoiceBills().isEmpty()) {
                for (InvoiceBill ib : oa.getInvoiceBills()) {
                    if (linkInfo.length() > 0) linkInfo.append(", ");
                    linkInfo.append(ib.getBillNo() != null ? ib.getBillNo() : "#" + ib.getId());
                    linkInfo.append("(").append(ib.getBillType()).append(")");
                }
            }
            if (oa.getStatement() != null) {
                if (linkInfo.length() > 0) linkInfo.append(", ");
                linkInfo.append("Stmt#").append(oa.getStatement().getStatementNo());
            }
            if (linkInfo.length() > 0) {
                desc += " [" + linkInfo + "]";
            }
            if (oa.getUtilizedAmount() != null && oa.getUtilizedAmount().compareTo(BigDecimal.ZERO) > 0) {
                desc += " Util:" + oa.getUtilizedAmount().setScale(2, RoundingMode.HALF_UP);
            }
            entry.setDescription(desc);
            entry.setAmount(oa.getAmount());
            entry.setReference(oa.getPurpose());
            data.getAdvanceEntries().add(entry);
        }

        // Inflow Repayments
        List<CashInflowRepayment> repayments = repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId);
        for (CashInflowRepayment r : repayments) {
            ShiftReportPrintData.AdvanceEntryDetail entry = new ShiftReportPrintData.AdvanceEntryDetail();
            entry.setType("REPAYMENT");
            entry.setDescription(r.getCashInflow() != null ? r.getCashInflow().getSource() : "-");
            entry.setAmount(r.getAmount());
            data.getAdvanceEntries().add(entry);
        }

        // Incentive from invoices
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
    }

    /**
     * Populate payment entries (bill and statement payments) into print data.
     */
    @Transactional(readOnly = true)
    public void populatePaymentEntries(ShiftReportPrintData data, Long shiftId) {
        List<Payment> shiftPayments = paymentRepository.findByShiftIdEager(shiftId);
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
    }
}
