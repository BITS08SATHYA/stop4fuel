package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.BunkAuditReport;
import com.stopforfuel.backend.dto.MonthlyAuditSummary;
import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
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
 * Bunk Audit / P&L aggregator. Rolls one or more shifts into a single money view
 * and answers "does the bunk make profit?"
 *
 * Strategy: for each shift in the range, prefer the persisted ShiftClosingReport
 * line items (which include the COGS snapshot taken at shift close). If a shift
 * has no closing report yet, compute line items on the fly via the existing
 * ShiftSalesCalculationService / ShiftFinancialCalculationService (cost snapshot
 * will use today's WAC — approximate).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BunkAuditService {

    private static final BigDecimal SHRINKAGE_FLAG_THRESHOLD = new BigDecimal("0.005"); // 0.5%

    private final ShiftRepository shiftRepository;
    private final ShiftClosingReportRepository shiftReportRepository;
    private final ShiftSalesCalculationService salesCalc;
    private final ShiftFinancialCalculationService financialCalc;
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
        for (Shift shift : shifts) {
            accumulateShift(shift, acc);
        }

        report.setInputs(acc.buildInputs());
        report.setOutputs(acc.buildOutputs());
        addFuelReceived(report, scid, from, to);
        report.setVariance(acc.buildVariance());
        report.setProfitability(acc.buildProfitability());
        return report;
    }

    /**
     * Produce 12 per-month roll-ups for a year. Each month is a compact summary
     * powering the yearly scorecard on the /audit page.
     */
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
        report.setInputs(acc.buildInputs());
        report.setOutputs(acc.buildOutputs());
        // fuelReceived aggregated from purchase invoices on the shift date
        addFuelReceived(report, scid, shiftDate, shiftDate);
        report.setVariance(acc.buildVariance());
        report.setProfitability(acc.buildProfitability());
        return report;
    }

    // =========================== internals ===========================

    private void accumulateShift(Shift shift, Accumulator acc) {
        Long shiftId = shift.getId();
        Optional<ShiftClosingReport> persisted = shiftReportRepository.findByShift_Id(shiftId);
        List<ReportLineItem> lineItems;
        if (persisted.isPresent() && persisted.get().getLineItems() != null
                && !persisted.get().getLineItems().isEmpty()) {
            lineItems = persisted.get().getLineItems();
        } else {
            lineItems = computeLineItemsFallback(shiftId);
        }
        for (ReportLineItem li : lineItems) {
            acc.ingestLineItem(li);
        }
        // variance per shift → aggregate
        for (ShiftReportPrintData.SalesDifference sd : salesCalc.computeSalesDifferences(shiftId)) {
            acc.ingestVariance(sd);
        }
    }

    private List<ReportLineItem> computeLineItemsFallback(Long shiftId) {
        ShiftClosingReport transientReport = new ShiftClosingReport();
        ShiftSalesCalculationService.SalesResult salesResult = salesCalc.computeSalesLineItems(transientReport, shiftId, 0);
        List<ReportLineItem> all = new ArrayList<>(salesResult.getLineItems());
        int nextSort = all.size();
        all.addAll(financialCalc.computeFinancialLineItems(transientReport, shiftId,
                salesResult.getCreditBillTotal(), nextSort,
                salesResult.getTestLitres(), salesResult.getTestAmount()));
        return all;
    }

    private void addFuelReceived(BunkAuditReport report, Long scid, LocalDate from, LocalDate to) {
        List<PurchaseInvoice> invoices = purchaseInvoiceRepository
                .findByDateRangeWithDetails(scid, from, to);
        Map<String, double[]> perProduct = new LinkedHashMap<>(); // name -> [litres, amount]
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
            report.getInputs().getFuelReceived().add(fr);
        }
    }

    /** Running state while walking shifts. */
    private static class Accumulator {
        // REVENUE side
        final Map<String, double[]> fuelSold = new LinkedHashMap<>();   // name -> [qty, revenue, cogs]
        final Map<String, double[]> oilSold = new LinkedHashMap<>();    // name -> [qty, revenue, cogs]
        BigDecimal billPayments = BigDecimal.ZERO;
        BigDecimal statementPayments = BigDecimal.ZERO;
        BigDecimal externalInflow = BigDecimal.ZERO;
        double testLitres = 0;
        BigDecimal testAmount = BigDecimal.ZERO;

        // ADVANCE side
        BigDecimal creditBilled = BigDecimal.ZERO;
        final Map<String, BigDecimal> eAdvances = new LinkedHashMap<>(); // mode -> amount
        final Map<String, BigDecimal> opAdvances = new LinkedHashMap<>(); // type -> amount
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal incentives = BigDecimal.ZERO;

        // variance
        final Map<String, double[]> varianceByProduct = new LinkedHashMap<>(); // name -> [tank, meter]

        void ingestLineItem(ReportLineItem li) {
            String section = li.getSection();
            String category = li.getCategory();
            String label = li.getLabel() != null ? li.getLabel() : "";
            BigDecimal amount = li.getAmount() != null ? li.getAmount() : BigDecimal.ZERO;
            BigDecimal cost = li.getCostAmount() != null ? li.getCostAmount() : BigDecimal.ZERO;
            double qty = li.getQuantity() != null ? li.getQuantity() : 0;

            if ("REVENUE".equals(section)) {
                switch (category) {
                    case "FUEL_SALES":
                        fuelSold.merge(label, new double[]{qty, amount.doubleValue(), cost.doubleValue()},
                                (o, n) -> new double[]{o[0] + n[0], o[1] + n[1], o[2] + n[2]});
                        break;
                    case "OIL_SALES":
                        oilSold.merge(label, new double[]{qty, amount.doubleValue(), cost.doubleValue()},
                                (o, n) -> new double[]{o[0] + n[0], o[1] + n[1], o[2] + n[2]});
                        break;
                    case "BILL_PAYMENT":
                        billPayments = billPayments.add(amount);
                        break;
                    case "STATEMENT_PAYMENT":
                        statementPayments = statementPayments.add(amount);
                        break;
                    case "EXTERNAL_INFLOW":
                        externalInflow = externalInflow.add(amount);
                        break;
                    case "TEST_QUANTITY":
                        testLitres += qty;
                        testAmount = testAmount.add(amount);
                        break;
                }
            } else if ("ADVANCE".equals(section)) {
                switch (category) {
                    case "CREDIT_BILLS":
                        creditBilled = creditBilled.add(amount);
                        break;
                    case "CARD":
                    case "UPI":
                    case "CCMS":
                    case "BANK":
                    case "CHEQUE":
                        eAdvances.merge(category, amount, BigDecimal::add);
                        break;
                    case "EXPENSES":
                        expenses = expenses.add(amount);
                        break;
                    case "INCENTIVE":
                        incentives = incentives.add(amount);
                        break;
                    case "TEST_QUANTITY":
                        // same line already counted in REVENUE side — skip to avoid double-count
                        break;
                    default:
                        if (category.endsWith("_ADVANCE")) {
                            opAdvances.merge(category.replace("_ADVANCE", ""), amount, BigDecimal::add);
                        }
                }
            }
        }

        void ingestVariance(ShiftReportPrintData.SalesDifference sd) {
            varianceByProduct.merge(sd.getProductName(),
                    new double[]{sd.getTankSale(), sd.getMeterSale()},
                    (o, n) -> new double[]{o[0] + n[0], o[1] + n[1]});
        }

        BunkAuditReport.Inputs buildInputs() {
            BunkAuditReport.Inputs in = new BunkAuditReport.Inputs();
            in.setCreditBilled(creditBilled);
            in.setCreditCollected(billPayments.add(statementPayments));
            in.setExternalInflow(externalInflow);
            for (Map.Entry<String, BigDecimal> e : eAdvances.entrySet()) {
                BunkAuditReport.AmountByMode m = new BunkAuditReport.AmountByMode();
                m.setMode(e.getKey());
                m.setAmount(e.getValue());
                in.getEAdvances().add(m);
            }
            return in;
        }

        BunkAuditReport.Outputs buildOutputs() {
            BunkAuditReport.Outputs out = new BunkAuditReport.Outputs();
            for (Map.Entry<String, double[]> e : fuelSold.entrySet()) {
                out.getFuelSold().add(productSale(e.getKey(), e.getValue()));
            }
            for (Map.Entry<String, double[]> e : oilSold.entrySet()) {
                out.getOilSold().add(productSale(e.getKey(), e.getValue()));
            }
            for (Map.Entry<String, BigDecimal> e : opAdvances.entrySet()) {
                BunkAuditReport.AmountByType t = new BunkAuditReport.AmountByType();
                t.setType(e.getKey());
                t.setAmount(e.getValue());
                out.getOpAdvances().add(t);
            }
            BunkAuditReport.AmountByType expRow = new BunkAuditReport.AmountByType();
            expRow.setType("SHIFT_EXPENSES");
            expRow.setAmount(expenses);
            out.getExpenses().add(expRow);
            out.setIncentives(incentives);
            BunkAuditReport.TestQuantity tq = new BunkAuditReport.TestQuantity();
            tq.setLitres(testLitres);
            tq.setAmount(testAmount);
            out.setTestQuantity(tq);
            return out;
        }

        private BunkAuditReport.ProductSale productSale(String name, double[] vals) {
            BunkAuditReport.ProductSale ps = new BunkAuditReport.ProductSale();
            ps.setProductName(name);
            ps.setQuantity(vals[0]);
            BigDecimal revenue = BigDecimal.valueOf(vals[1]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cogs = BigDecimal.valueOf(vals[2]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal margin = revenue.subtract(cogs);
            ps.setRevenue(revenue);
            ps.setCogs(cogs);
            ps.setMargin(margin);
            if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                ps.setMarginPct(margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP));
            }
            return ps;
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

        BunkAuditReport.Profitability buildProfitability() {
            BigDecimal grossRevenue = BigDecimal.ZERO;
            BigDecimal totalCogs = BigDecimal.ZERO;
            for (double[] v : fuelSold.values()) {
                grossRevenue = grossRevenue.add(BigDecimal.valueOf(v[1]));
                totalCogs = totalCogs.add(BigDecimal.valueOf(v[2]));
            }
            for (double[] v : oilSold.values()) {
                grossRevenue = grossRevenue.add(BigDecimal.valueOf(v[1]));
                totalCogs = totalCogs.add(BigDecimal.valueOf(v[2]));
            }
            BigDecimal grossProfit = grossRevenue.subtract(totalCogs);
            BigDecimal opEx = expenses.add(incentives);
            BigDecimal netProfit = grossProfit.subtract(opEx);

            BunkAuditReport.Profitability p = new BunkAuditReport.Profitability();
            p.setGrossRevenue(grossRevenue.setScale(2, RoundingMode.HALF_UP));
            p.setTotalCogs(totalCogs.setScale(2, RoundingMode.HALF_UP));
            p.setGrossProfit(grossProfit.setScale(2, RoundingMode.HALF_UP));
            p.setOperatingExpenses(opEx.setScale(2, RoundingMode.HALF_UP));
            p.setNetProfit(netProfit.setScale(2, RoundingMode.HALF_UP));
            if (grossRevenue.compareTo(BigDecimal.ZERO) > 0) {
                p.setMarginPct(netProfit.multiply(BigDecimal.valueOf(100))
                        .divide(grossRevenue, 2, RoundingMode.HALF_UP));
            }
            return p;
        }
    }
}
