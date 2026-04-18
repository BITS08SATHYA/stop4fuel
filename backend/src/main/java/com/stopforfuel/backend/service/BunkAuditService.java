package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.BunkAuditReport;
import com.stopforfuel.backend.dto.MonthlyAuditSummary;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.AdvanceStatus;
import com.stopforfuel.backend.enums.AdvanceType;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.enums.CashAdvanceDestination;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Bunk Audit aggregator.
 *
 * Produces two views over the same period:
 *  - CashFlow: IN (cash invoices, payments, external inflows) vs OUT (credit
 *    invoices, e-advances, expenses, incentives, salary advance, cash advance
 *    when SPENT, inflow repayments) vs Internal Transfers (management advance,
 *    cash advance when BANK_DEPOSIT). Net = IN − OUT.
 *  - Profitability: Σ invoice revenue vs Σ (qty × WAC cost) − opex.
 *
 * Queries source entities per shift directly (no line-item dependency) so the
 * cash-flow classification is explicit from the data, not from string category
 * labels on ReportLineItem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BunkAuditService {

    private static final BigDecimal SHRINKAGE_FLAG_THRESHOLD = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ShiftRepository shiftRepository;
    private final ShiftSalesCalculationService salesCalc;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final ExternalCashInflowRepository inflowRepository;
    private final OperationalAdvanceRepository operationalAdvanceRepository;
    private final ExpenseRepository expenseRepository;
    private final StationExpenseRepository stationExpenseRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;
    private final EAdvanceRepository eAdvanceRepository;
    private final CashInflowRepaymentRepository repaymentRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;

    @Transactional(readOnly = true)
    public BunkAuditReport compute(LocalDate from, LocalDate to, BunkAuditReport.Granularity granularity) {
        Long scid = SecurityUtils.getScid();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        List<Shift> shifts = shiftRepository.findByScidAndStartTimeBetweenOrderByStartTimeAsc(scid, fromDt, toDt);
        log.info("Bunk audit {}..{}: {} shifts for scid={}", from, to, shifts.size(), scid);

        BunkAuditReport report = new BunkAuditReport();
        report.setFromDate(from);
        report.setToDate(to);
        report.setGranularity(granularity);
        report.setShiftCount(shifts.size());

        Accumulator acc = new Accumulator();
        for (Shift shift : shifts) accumulateShift(shift, acc);

        report.setCashFlow(acc.buildCashFlow());
        report.setProductSales(acc.buildProductSales());
        report.setProfitability(acc.buildProfitability());
        report.setVariance(acc.buildVariance());
        addFuelReceived(report, scid, from, to);
        return report;
    }

    @Transactional(readOnly = true)
    public BunkAuditReport computeForShift(Long shiftId) {
        Long scid = SecurityUtils.getScid();
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));
        if (!Objects.equals(shift.getScid(), scid)) {
            throw new ResourceNotFoundException("Shift not found: " + shiftId);
        }

        LocalDate shiftDate = shift.getStartTime() != null ? shift.getStartTime().toLocalDate() : LocalDate.now();
        BunkAuditReport report = new BunkAuditReport();
        report.setFromDate(shiftDate);
        report.setToDate(shiftDate);
        report.setGranularity(BunkAuditReport.Granularity.SHIFT);
        report.setShiftCount(1);

        Accumulator acc = new Accumulator();
        accumulateShift(shift, acc);
        report.setCashFlow(acc.buildCashFlow());
        report.setProductSales(acc.buildProductSales());
        report.setProfitability(acc.buildProfitability());
        report.setVariance(acc.buildVariance());
        addFuelReceived(report, scid, shiftDate, shiftDate);
        return report;
    }

    @Transactional(readOnly = true)
    public List<MonthlyAuditSummary> monthlyForYear(int year) {
        List<MonthlyAuditSummary> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate from = LocalDate.of(year, month, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            BunkAuditReport report = compute(from, to, BunkAuditReport.Granularity.MONTH);
            BunkAuditReport.Profitability p = report.getProfitability();
            result.add(new MonthlyAuditSummary(
                    year, month, report.getShiftCount(),
                    p.getGrossRevenue(), p.getTotalCogs(), p.getGrossProfit(),
                    p.getOperatingExpenses(), p.getNetProfit(), p.getMarginPct()));
        }
        return result;
    }

    // ======================================================================
    //  per-shift ingestion — reads source entities directly
    // ======================================================================

    private void accumulateShift(Shift shift, Accumulator acc) {
        Long shiftId = shift.getId();

        // Invoices: cash vs credit totals; per-product revenue + COGS
        for (InvoiceBill bill : invoiceBillRepository.findByShiftIdOrderByIdDesc(shiftId)) {
            BigDecimal net = bill.getNetAmount() != null ? bill.getNetAmount() : BigDecimal.ZERO;
            if (BillType.CASH.equals(bill.getBillType())) {
                acc.cashInvoices = acc.cashInvoices.add(net);
            } else if (BillType.CREDIT.equals(bill.getBillType())) {
                acc.creditInvoices = acc.creditInvoices.add(net);
            }
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) acc.ingestInvoiceProduct(ip);
            }
        }

        // Payments: bill payments + statement payments
        for (Payment p : paymentRepository.findByShiftIdEager(shiftId)) {
            if (p.getAmount() == null) continue;
            if (p.getInvoiceBill() != null) {
                acc.billPayments = acc.billPayments.add(p.getAmount());
            } else if (p.getStatement() != null) {
                acc.statementPayments = acc.statementPayments.add(p.getAmount());
            }
        }

        // External inflows
        for (ExternalCashInflow inf : inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId)) {
            if (inf.getAmount() != null) acc.externalInflow = acc.externalInflow.add(inf.getAmount());
        }

        // E-advances per mode
        for (PaymentMode mode : new PaymentMode[]{PaymentMode.CARD, PaymentMode.UPI,
                PaymentMode.CCMS, PaymentMode.CHEQUE, PaymentMode.BANK_TRANSFER, PaymentMode.NEFT}) {
            BigDecimal modeSum = eAdvanceRepository.sumByShiftAndType(shiftId, mode);
            if (modeSum != null && modeSum.compareTo(BigDecimal.ZERO) > 0) {
                acc.eAdvancesByMode.merge(mode.name(), modeSum, BigDecimal::add);
            }
        }

        // Operational advances — classify by type × destination
        for (OperationalAdvance oa : operationalAdvanceRepository.findByShiftIdOrderByIdDesc(shiftId)) {
            if (oa.getStatus() == AdvanceStatus.CANCELLED) continue;
            BigDecimal amt = oa.getAmount() != null ? oa.getAmount() : BigDecimal.ZERO;
            AdvanceType type = oa.getAdvanceType();
            if (type == AdvanceType.SALARY) {
                acc.salaryAdvance = acc.salaryAdvance.add(amt);
            } else if (type == AdvanceType.MANAGEMENT) {
                acc.managementAdvance = acc.managementAdvance.add(amt);
            } else if (type == AdvanceType.CASH) {
                // Historical nulls are treated as SPENT (conservative)
                if (oa.getCashDestination() == CashAdvanceDestination.BANK_DEPOSIT) {
                    acc.cashAdvanceBankDeposit = acc.cashAdvanceBankDeposit.add(amt);
                } else {
                    acc.cashAdvanceSpent = acc.cashAdvanceSpent.add(amt);
                }
            }
        }

        // Expenses + station expenses
        BigDecimal shiftExpense = expenseRepository.sumByShift(shiftId);
        if (shiftExpense != null) acc.shiftExpenses = acc.shiftExpenses.add(shiftExpense);
        Double stationExpense = stationExpenseRepository.sumAmountByShift(shiftId);
        if (stationExpense != null && stationExpense > 0) {
            acc.stationExpenses = acc.stationExpenses.add(BigDecimal.valueOf(stationExpense));
        }

        // Incentives
        BigDecimal incentive = incentivePaymentRepository.sumByShift(shiftId);
        if (incentive != null) acc.incentives = acc.incentives.add(incentive);

        // Inflow repayments (cash returned to lenders)
        for (CashInflowRepayment r : repaymentRepository.findByShiftIdOrderByRepaymentDateDesc(shiftId)) {
            if (r.getAmount() != null) acc.inflowRepayments = acc.inflowRepayments.add(r.getAmount());
        }

        // Test quantity — from nozzle readings × product price
        for (NozzleInventory ni : nozzleInventoryRepository.findByShiftId(shiftId)) {
            if (ni.getTestQuantity() == null) continue;
            double test = ni.getTestQuantity();
            if (test <= 0) continue;
            Product product = ni.getNozzle() != null && ni.getNozzle().getTank() != null
                    ? ni.getNozzle().getTank().getProduct() : null;
            double rate = product != null && product.getPrice() != null ? product.getPrice().doubleValue() : 0;
            acc.testLitres += test;
            acc.testAmount = acc.testAmount.add(BigDecimal.valueOf(test * rate));
        }

        // Variance (tank-sale vs meter-sale per product)
        for (ShiftReportPrintData.SalesDifference sd : salesCalc.computeSalesDifferences(shiftId)) {
            acc.varianceByProduct.merge(sd.getProductName(),
                    new double[]{sd.getTankSale(), sd.getMeterSale()},
                    (o, n) -> new double[]{o[0] + n[0], o[1] + n[1]});
        }
    }

    private void addFuelReceived(BunkAuditReport report, Long scid, LocalDate from, LocalDate to) {
        List<PurchaseInvoice> invoices = purchaseInvoiceRepository
                .findByDateRangeWithDetails(scid, from, to);
        Map<String, double[]> perProduct = new LinkedHashMap<>();
        for (PurchaseInvoice inv : invoices) {
            if (!"VERIFIED".equals(inv.getStatus()) && !"PAID".equals(inv.getStatus())) continue;
            if (inv.getItems() == null) continue;
            for (PurchaseInvoiceItem item : inv.getItems()) {
                if (item.getProduct() == null || item.getQuantity() == null) continue;
                String name = item.getProduct().getName();
                double qty = item.getQuantity();
                double amt = item.getTotalPrice() != null ? item.getTotalPrice().doubleValue()
                        : (item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() * qty : 0);
                perProduct.merge(name, new double[]{qty, amt}, (o, n) -> new double[]{o[0] + n[0], o[1] + n[1]});
            }
        }
        for (Map.Entry<String, double[]> e : perProduct.entrySet()) {
            BunkAuditReport.FuelReceived fr = new BunkAuditReport.FuelReceived();
            fr.setProductName(e.getKey());
            fr.setLitres(e.getValue()[0]);
            fr.setPurchaseAmount(BigDecimal.valueOf(e.getValue()[1]).setScale(2, RoundingMode.HALF_UP));
            report.getFuelReceived().add(fr);
        }
    }

    // ======================================================================
    //  accumulator — running totals + per-product sales
    // ======================================================================

    private static class Accumulator {
        // Cash-flow IN
        BigDecimal cashInvoices = BigDecimal.ZERO;
        BigDecimal billPayments = BigDecimal.ZERO;
        BigDecimal statementPayments = BigDecimal.ZERO;
        BigDecimal externalInflow = BigDecimal.ZERO;

        // Cash-flow OUT
        BigDecimal creditInvoices = BigDecimal.ZERO;
        final Map<String, BigDecimal> eAdvancesByMode = new LinkedHashMap<>();
        BigDecimal shiftExpenses = BigDecimal.ZERO;
        BigDecimal stationExpenses = BigDecimal.ZERO;
        BigDecimal incentives = BigDecimal.ZERO;
        BigDecimal salaryAdvance = BigDecimal.ZERO;
        BigDecimal cashAdvanceSpent = BigDecimal.ZERO;
        BigDecimal inflowRepayments = BigDecimal.ZERO;
        double testLitres = 0;
        BigDecimal testAmount = BigDecimal.ZERO;

        // Internal transfers
        BigDecimal managementAdvance = BigDecimal.ZERO;
        BigDecimal cashAdvanceBankDeposit = BigDecimal.ZERO;

        // Per-product accrual sales: name -> [qty, revenue, cogs]
        final Map<String, double[]> productSales = new LinkedHashMap<>();

        // Variance
        final Map<String, double[]> varianceByProduct = new LinkedHashMap<>();

        void ingestInvoiceProduct(InvoiceProduct ip) {
            if (ip.getProduct() == null || ip.getQuantity() == null) return;
            String name = ip.getProduct().getName() != null ? ip.getProduct().getName() : "Unknown";
            double qty = ip.getQuantity().doubleValue();
            double revenue = ip.getAmount() != null ? ip.getAmount().doubleValue() : 0;
            double wac = ip.getProduct().getWacCost() != null ? ip.getProduct().getWacCost().doubleValue() : 0;
            double cogs = qty * wac;
            productSales.merge(name, new double[]{qty, revenue, cogs},
                    (o, n) -> new double[]{o[0] + n[0], o[1] + n[1], o[2] + n[2]});
        }

        BunkAuditReport.CashFlow buildCashFlow() {
            BunkAuditReport.CashFlow cf = new BunkAuditReport.CashFlow();

            BunkAuditReport.CashIn in = cf.getIn();
            in.setCashInvoices(scale(cashInvoices));
            in.setBillPayments(scale(billPayments));
            in.setStatementPayments(scale(statementPayments));
            in.setExternalInflow(scale(externalInflow));

            BunkAuditReport.CashOut out = cf.getOut();
            out.setCreditInvoices(scale(creditInvoices));
            for (Map.Entry<String, BigDecimal> e : eAdvancesByMode.entrySet()) {
                BunkAuditReport.AmountByMode m = new BunkAuditReport.AmountByMode();
                m.setMode(e.getKey());
                m.setAmount(scale(e.getValue()));
                out.getEAdvances().add(m);
            }
            BunkAuditReport.AmountByType shift = new BunkAuditReport.AmountByType();
            shift.setType("SHIFT_EXPENSES");
            shift.setAmount(scale(shiftExpenses));
            if (shiftExpenses.compareTo(BigDecimal.ZERO) > 0) out.getExpenses().add(shift);
            out.setStationExpenses(scale(stationExpenses));
            out.setIncentives(scale(incentives));
            out.setSalaryAdvance(scale(salaryAdvance));
            out.setCashAdvanceSpent(scale(cashAdvanceSpent));
            out.setInflowRepayments(scale(inflowRepayments));
            BunkAuditReport.TestQuantity tq = out.getTestQuantity();
            tq.setLitres(testLitres);
            tq.setAmount(scale(testAmount));

            BunkAuditReport.InternalTransfers xfer = cf.getInternalTransfers();
            xfer.setManagementAdvance(scale(managementAdvance));
            xfer.setCashAdvanceBankDeposit(scale(cashAdvanceBankDeposit));

            BigDecimal totalIn = cashInvoices.add(billPayments).add(statementPayments).add(externalInflow);
            BigDecimal eAdvanceTotal = BigDecimal.ZERO;
            for (BigDecimal v : eAdvancesByMode.values()) eAdvanceTotal = eAdvanceTotal.add(v);
            BigDecimal totalOut = creditInvoices.add(eAdvanceTotal).add(shiftExpenses)
                    .add(stationExpenses).add(incentives).add(salaryAdvance)
                    .add(cashAdvanceSpent).add(inflowRepayments);
            cf.setNetPosition(scale(totalIn.subtract(totalOut)));
            return cf;
        }

        List<BunkAuditReport.ProductSale> buildProductSales() {
            List<BunkAuditReport.ProductSale> list = new ArrayList<>();
            for (Map.Entry<String, double[]> e : productSales.entrySet()) {
                double[] v = e.getValue();
                BigDecimal revenue = scale(BigDecimal.valueOf(v[1]));
                BigDecimal cogs = scale(BigDecimal.valueOf(v[2]));
                BigDecimal margin = revenue.subtract(cogs);
                BunkAuditReport.ProductSale ps = new BunkAuditReport.ProductSale();
                ps.setProductName(e.getKey());
                ps.setQuantity(v[0]);
                ps.setRevenue(revenue);
                ps.setCogs(cogs);
                ps.setMargin(margin);
                if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                    ps.setMarginPct(margin.multiply(HUNDRED).divide(revenue, 2, RoundingMode.HALF_UP));
                }
                list.add(ps);
            }
            return list;
        }

        BunkAuditReport.Profitability buildProfitability() {
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal cogs = BigDecimal.ZERO;
            for (double[] v : productSales.values()) {
                revenue = revenue.add(BigDecimal.valueOf(v[1]));
                cogs = cogs.add(BigDecimal.valueOf(v[2]));
            }
            BigDecimal grossProfit = revenue.subtract(cogs);
            // Test-quantity cost treated as an opex-like loss in the accrual view
            BigDecimal opex = shiftExpenses.add(stationExpenses).add(incentives)
                    .add(salaryAdvance).add(testAmount);
            BigDecimal netProfit = grossProfit.subtract(opex);

            BunkAuditReport.Profitability p = new BunkAuditReport.Profitability();
            p.setGrossRevenue(scale(revenue));
            p.setTotalCogs(scale(cogs));
            p.setGrossProfit(scale(grossProfit));
            p.setOperatingExpenses(scale(opex));
            p.setNetProfit(scale(netProfit));
            if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                p.setMarginPct(netProfit.multiply(HUNDRED).divide(revenue, 2, RoundingMode.HALF_UP));
            }
            return p;
        }

        List<BunkAuditReport.ProductVariance> buildVariance() {
            List<BunkAuditReport.ProductVariance> list = new ArrayList<>();
            for (Map.Entry<String, double[]> e : varianceByProduct.entrySet()) {
                double tank = e.getValue()[0];
                double meter = e.getValue()[1];
                double shrinkage = tank - meter;
                BunkAuditReport.ProductVariance v = new BunkAuditReport.ProductVariance();
                v.setProductName(e.getKey());
                v.setExpectedLitres(tank);
                v.setActualLitres(meter);
                v.setShrinkageLitres(shrinkage);
                if (tank > 0) {
                    BigDecimal pct = BigDecimal.valueOf(shrinkage / tank).setScale(4, RoundingMode.HALF_UP);
                    v.setShrinkagePct(pct);
                    v.setFlagged(pct.abs().compareTo(SHRINKAGE_FLAG_THRESHOLD) > 0);
                }
                list.add(v);
            }
            return list;
        }

        private static BigDecimal scale(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
