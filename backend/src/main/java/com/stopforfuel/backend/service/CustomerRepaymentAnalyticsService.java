package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.CustomerConsumptionDTO;
import com.stopforfuel.backend.dto.CustomerRepaymentAnalyticsDTO;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.InvoiceProductRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerRepaymentAnalyticsService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    /** Fallback repayment window when the customer has none configured. */
    private static final int DEFAULT_REPAYMENT_DAYS = 30;
    /** Consumption change beyond ±this % vs the trailing baseline is flagged. */
    private static final double ANOMALY_THRESHOLD_PERCENT = 30;
    /** Baseline = average of this many equal-length periods before the range. */
    private static final int BASELINE_PERIODS = 3;

    private final CustomerRepository customerRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final InvoiceProductRepository invoiceProductRepository;
    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;

    @Transactional(readOnly = true)
    public CustomerRepaymentAnalyticsDTO getRepaymentAnalytics(LocalDate from, LocalDate to) {
        Long scid = SecurityUtils.getScid();
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate toDate = to != null ? to : today;
        LocalDate fromDate = from != null ? from : toDate.minusDays(29);
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must be on or before toDate");
        }
        int rangeDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDateTime fromTs = fromDate.atStartOfDay();
        LocalDateTime toTs = toDate.atTime(LocalTime.MAX);

        // Current-range billed totals per customer
        Map<Long, BigDecimal[]> current = new HashMap<>();   // [liters, amount, billCount]
        for (Object[] row : invoiceProductRepository.getCustomerSalesTotals(scid, fromTs, toTs)) {
            current.put((Long) row[0], new BigDecimal[]{
                    toBd(row[1]), toBd(row[2]), BigDecimal.valueOf(((Number) row[3]).longValue())});
        }

        // Baseline: 3 equal-length periods immediately before the range
        LocalDate prevFrom = fromDate.minusDays((long) rangeDays * BASELINE_PERIODS);
        Map<Long, BigDecimal> prevTotals = new HashMap<>();
        for (Object[] row : invoiceProductRepository.getCustomerSalesTotals(
                scid, prevFrom.atStartOfDay(), fromDate.minusDays(1).atTime(LocalTime.MAX))) {
            prevTotals.put((Long) row[0], toBd(row[2]));
        }

        // Repayment lags (payments made within the range):
        // statement customers → payment vs statement date; local customers → payment vs bill date
        Map<Long, List<double[]>> lagsByCustomer = new HashMap<>();  // [lagDays, amount]
        int[] histogram = new int[5];
        double lagWeightedSum = 0;
        double lagWeight = 0;
        for (Object[] row : paymentRepository.getStatementPaymentLags(scid, fromTs, toTs)) {
            Long customerId = (Long) row[0];
            LocalDate statementDate = (LocalDate) row[1];
            LocalDateTime paymentDate = (LocalDateTime) row[2];
            double amount = row[3] != null ? ((BigDecimal) row[3]).doubleValue() : 0;
            if (statementDate == null || paymentDate == null) continue;
            double lag = Math.max(ChronoUnit.DAYS.between(statementDate, paymentDate.toLocalDate()), 0);
            lagsByCustomer.computeIfAbsent(customerId, k -> new ArrayList<>()).add(new double[]{lag, amount});
            histogram[lagBucket(lag)]++;
            lagWeightedSum += lag * amount;
            lagWeight += amount;
        }
        for (Object[] row : paymentRepository.getBillPaymentLags(scid, fromTs, toTs)) {
            Long customerId = (Long) row[0];
            LocalDateTime billDate = (LocalDateTime) row[1];
            LocalDateTime paymentDate = (LocalDateTime) row[2];
            double amount = row[3] != null ? ((BigDecimal) row[3]).doubleValue() : 0;
            if (billDate == null || paymentDate == null) continue;
            double lag = Math.max(ChronoUnit.DAYS.between(billDate.toLocalDate(), paymentDate.toLocalDate()), 0);
            lagsByCustomer.computeIfAbsent(customerId, k -> new ArrayList<>()).add(new double[]{lag, amount});
            histogram[lagBucket(lag)]++;
            lagWeightedSum += lag * amount;
            lagWeight += amount;
        }

        // Outstanding + oldest unpaid per customer: statement balances plus
        // unfiled local credit bills (same definition as Credit Monitoring)
        Map<Long, Object[]> unpaid = new HashMap<>();  // [balance BigDecimal, oldest LocalDate]
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        for (Object[] row : statementRepository.getUnpaidBalancesByCustomer(scid)) {
            unpaid.put((Long) row[0], new Object[]{toBd(row[1]), row[2]});
            totalOutstanding = totalOutstanding.add(toBd(row[1]));
        }
        for (Object[] row : invoiceBillRepository.getUnpaidLocalCreditByCustomer(scid)) {
            Long customerId = (Long) row[0];
            BigDecimal balance = toBd(row[1]);
            LocalDate oldest = row[2] != null ? ((LocalDateTime) row[2]).toLocalDate() : null;
            Object[] existing = unpaid.get(customerId);
            if (existing == null) {
                unpaid.put(customerId, new Object[]{balance, oldest});
            } else {
                existing[0] = ((BigDecimal) existing[0]).add(balance);
                LocalDate prev = (LocalDate) existing[1];
                if (oldest != null && (prev == null || oldest.isBefore(prev))) existing[1] = oldest;
            }
            totalOutstanding = totalOutstanding.add(balance);
        }

        Set<Long> statementCustomerIds = new HashSet<>(statementRepository.findCustomerIdsWithStatements(scid));

        // Assemble per-customer rows
        Set<Long> ids = new HashSet<>();
        ids.addAll(current.keySet());
        ids.addAll(unpaid.keySet());
        ids.addAll(lagsByCustomer.keySet());
        Map<Long, Customer> customers = new HashMap<>();
        for (Customer c : customerRepository.findAllById(ids)) {
            if (scid.equals(c.getScid())) customers.put(c.getId(), c);
        }

        BigDecimal totalBilled = BigDecimal.ZERO;
        int overdueCount = 0;
        List<CustomerRepaymentAnalyticsDTO.Row> rows = new ArrayList<>();
        for (Long id : ids) {
            Customer c = customers.get(id);
            if (c == null) continue;
            BigDecimal[] cur = current.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal billed = cur[1];
            totalBilled = totalBilled.add(billed);

            int allowedDays = c.getRepaymentDays() != null ? c.getRepaymentDays() : DEFAULT_REPAYMENT_DAYS;

            Double avgLag = null;
            Double onTimePct = null;
            List<double[]> lags = lagsByCustomer.get(id);
            if (lags != null && !lags.isEmpty()) {
                double wSum = 0, w = 0;
                int onTime = 0;
                for (double[] l : lags) {
                    wSum += l[0] * l[1];
                    w += l[1];
                    if (l[0] <= allowedDays) onTime++;
                }
                avgLag = w > 0 ? round1(wSum / w) : null;
                onTimePct = round1(onTime * 100.0 / lags.size());
            }

            Object[] up = unpaid.get(id);
            BigDecimal outstanding = up != null ? (BigDecimal) up[0] : BigDecimal.ZERO;
            Integer oldestUnpaidDays = null;
            if (up != null && up[1] != null) {
                oldestUnpaidDays = (int) ChronoUnit.DAYS.between((LocalDate) up[1], today);
            }
            boolean overdue = oldestUnpaidDays != null && oldestUnpaidDays > allowedDays;
            if (overdue) overdueCount++;

            BigDecimal prevTotal = prevTotals.get(id);
            BigDecimal prevAvg = null;
            Double changePct = null;
            String trend = "NEW";
            if (prevTotal != null && prevTotal.compareTo(BigDecimal.ZERO) > 0) {
                prevAvg = prevTotal.divide(BigDecimal.valueOf(BASELINE_PERIODS), 2, RoundingMode.HALF_UP);
                changePct = round1(billed.subtract(prevAvg)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(prevAvg, 4, RoundingMode.HALF_UP).doubleValue());
                trend = changePct >= ANOMALY_THRESHOLD_PERCENT ? "MORE"
                        : changePct <= -ANOMALY_THRESHOLD_PERCENT ? "LESS" : "NORMAL";
            }

            // Canonical classification: Party.partyType == "Statement", with generated
            // statements as backstop (mirrors LedgerService/CreditMonitoringService)
            boolean isStatement = (c.getParty() != null
                    && "Statement".equalsIgnoreCase(c.getParty().getPartyType()))
                    || statementCustomerIds.contains(id);

            rows.add(CustomerRepaymentAnalyticsDTO.Row.builder()
                    .customerId(id)
                    .name(c.getName())
                    .partyType(isStatement ? "STATEMENT" : "LOCAL")
                    .repaymentDaysAllowed(c.getRepaymentDays())
                    .billedInRange(billed)
                    .litersInRange(cur[0])
                    .billCount(cur[2].longValue())
                    .outstanding(outstanding)
                    .oldestUnpaidDays(oldestUnpaidDays)
                    .avgRepaymentLagDays(avgLag)
                    .onTimePercent(onTimePct)
                    .prevAvgBilled(prevAvg)
                    .changePercent(changePct)
                    .consumptionTrend(trend)
                    .overdue(overdue)
                    .build());
        }
        rows.sort((a, b) -> b.getBilledInRange().compareTo(a.getBilledInRange()));

        // Monthly billed + collected trend
        Map<String, BigDecimal[]> monthly = new LinkedHashMap<>();  // [billed, collected]
        for (Object[] row : invoiceProductRepository.getMonthlyCustomerBilled(scid, fromTs, toTs)) {
            monthly.computeIfAbsent(ym(row[0], row[1]), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[0] = toBd(row[3]);
        }
        for (Object[] row : paymentRepository.getMonthlyCollected(scid, fromTs, toTs)) {
            monthly.computeIfAbsent(ym(row[0], row[1]), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[1] = toBd(row[2]);
        }
        BigDecimal totalCollected = BigDecimal.ZERO;
        List<CustomerRepaymentAnalyticsDTO.MonthlyPoint> monthlyPoints = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : new java.util.TreeMap<>(monthly).entrySet()) {
            totalCollected = totalCollected.add(e.getValue()[1]);
            monthlyPoints.add(CustomerRepaymentAnalyticsDTO.MonthlyPoint.builder()
                    .month(e.getKey())
                    .billed(e.getValue()[0])
                    .collected(e.getValue()[1])
                    .build());
        }

        String[] bucketNames = {"0-7d", "8-15d", "16-30d", "31-60d", "60d+"};
        List<CustomerRepaymentAnalyticsDTO.LagBucket> lagBuckets = new ArrayList<>();
        for (int i = 0; i < bucketNames.length; i++) {
            lagBuckets.add(CustomerRepaymentAnalyticsDTO.LagBucket.builder()
                    .bucket(bucketNames[i]).count(histogram[i]).build());
        }

        return CustomerRepaymentAnalyticsDTO.builder()
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .rangeDays(rangeDays)
                .totalBilled(totalBilled)
                .totalCollected(totalCollected)
                .totalOutstanding(totalOutstanding)
                .avgRepaymentLagDays(lagWeight > 0 ? round1(lagWeightedSum / lagWeight) : null)
                .overdueCustomers(overdueCount)
                .activeCreditCustomers(rows.size())
                .monthlyTurnover(monthlyPoints)
                .lagHistogram(lagBuckets)
                .customers(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerConsumptionDTO getCustomerConsumption(Long customerId, int months) {
        Long scid = SecurityUtils.getScid();
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> scid.equals(c.getScid()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        if (months < 1 || months > 36) {
            throw new BusinessException("months must be between 1 and 36");
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate from = today.minusMonths(months - 1).withDayOfMonth(1);
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = today.atTime(LocalTime.MAX);

        // Pre-seed every month so the chart shows gaps as zeros
        Map<String, BigDecimal[]> byMonth = new LinkedHashMap<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(today)) {
            byMonth.put(cursor.toString().substring(0, 7), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cursor = cursor.plusMonths(1);
        }
        for (Object[] row : invoiceProductRepository.getMonthlySalesForCustomer(scid, customerId, fromTs, toTs)) {
            BigDecimal[] acc = byMonth.get(ym(row[0], row[1]));
            if (acc != null) {
                acc[0] = toBd(row[2]);
                acc[1] = toBd(row[3]);
            }
        }
        List<CustomerConsumptionDTO.MonthlyPoint> monthly = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : byMonth.entrySet()) {
            monthly.add(CustomerConsumptionDTO.MonthlyPoint.builder()
                    .month(e.getKey()).quantity(e.getValue()[0]).amount(e.getValue()[1]).build());
        }

        List<CustomerConsumptionDTO.ProductShare> mix = new ArrayList<>();
        for (Object[] row : invoiceProductRepository.getProductMixForCustomer(scid, customerId, fromTs, toTs)) {
            mix.add(CustomerConsumptionDTO.ProductShare.builder()
                    .product((String) row[0]).quantity(toBd(row[1])).amount(toBd(row[2])).build());
        }

        return CustomerConsumptionDTO.builder()
                .customerId(customerId)
                .name(customer.getName())
                .fromDate(from.toString())
                .toDate(today.toString())
                .monthly(monthly)
                .productMix(mix)
                .build();
    }

    private static int lagBucket(double lag) {
        if (lag <= 7) return 0;
        if (lag <= 15) return 1;
        if (lag <= 30) return 2;
        if (lag <= 60) return 3;
        return 4;
    }

    private static String ym(Object year, Object month) {
        return String.format("%04d-%02d", ((Number) year).intValue(), ((Number) month).intValue());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static BigDecimal toBd(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
