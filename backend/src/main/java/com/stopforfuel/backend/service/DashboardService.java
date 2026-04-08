package com.stopforfuel.backend.service;

import com.stopforfuel.backend.controller.DashboardController.*;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final TankRepository tankRepository;
    private final PumpRepository pumpRepository;
    private final NozzleRepository nozzleRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final ShiftService shiftService;
    private final EAdvanceService eAdvanceService;
    private final ExpenseService expenseService;
    private final OperationalAdvanceRepository operationalAdvanceRepository;
    private final IncentivePaymentRepository incentivePaymentRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final ProductRepository productRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        Long scid = com.stopforfuel.config.SecurityUtils.getScid();

        // --- Today's stats (simple aggregate queries) ---
        stats.setTodayRevenue(invoiceBillRepository.sumRevenueByDateRange(todayStart, todayEnd, scid));
        stats.setTodayInvoiceCount((int) invoiceBillRepository.countByDateRange(todayStart, todayEnd, scid));
        stats.setTodayCashInvoices(invoiceBillRepository.countCashByDateRange(todayStart, todayEnd, scid));
        stats.setTodayCreditInvoices(invoiceBillRepository.countCreditByDateRange(todayStart, todayEnd, scid));
        stats.setTodayFuelVolume(invoiceBillRepository.sumFuelVolumeByDateRange(todayStart, todayEnd, scid));

        // --- Active shift stats ---
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            stats.setActiveShiftId(activeShift.getId());
            stats.setActiveShiftStartTime(activeShift.getStartTime() != null
                    ? activeShift.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);

            Long sid = activeShift.getId();
            BigDecimal shiftCash = invoiceBillRepository.sumCashBillsByShift(sid);
            Map<String, BigDecimal> eAdvSummary = eAdvanceService.getShiftSummary(sid);
            BigDecimal shiftUpi = eAdvSummary.getOrDefault("upi", BigDecimal.ZERO);
            BigDecimal shiftCard = eAdvSummary.getOrDefault("card", BigDecimal.ZERO);
            BigDecimal shiftExpense = expenseService.sumByShift(sid);
            BigDecimal shiftTotal = shiftCash.add(eAdvSummary.getOrDefault("total", BigDecimal.ZERO));
            BigDecimal shiftNet = shiftTotal.subtract(shiftExpense);

            stats.setShiftCash(shiftCash);
            stats.setShiftUpi(shiftUpi);
            stats.setShiftCard(shiftCard);
            stats.setShiftExpense(shiftExpense);
            stats.setShiftTotal(shiftTotal);
            stats.setShiftNet(shiftNet);
        }

        // --- Station stats (count queries instead of loading all entities) ---
        stats.setTotalTanks(tankRepository.countByScid(scid));
        stats.setActiveTanks(tankRepository.countByActiveAndScid(true, scid));
        stats.setTotalPumps(pumpRepository.countByScid(scid));
        stats.setActivePumps(pumpRepository.countByActiveAndScid(true, scid));
        stats.setTotalNozzles(nozzleRepository.countByScid(scid));
        stats.setActiveNozzles(nozzleRepository.countByActiveAndScid(true, scid));

        // --- Credit stats + aging (aggregate queries, no entity loading) ---
        try {
            stats.setTotalOutstanding(invoiceBillRepository.sumTotalOutstanding(scid));
            stats.setTotalCreditCustomers((int) invoiceBillRepository.countCreditCustomersWithOutstanding(scid));

            LocalDateTime d30 = today.minusDays(30).atStartOfDay();
            LocalDateTime d60 = today.minusDays(60).atStartOfDay();
            LocalDateTime d90 = today.minusDays(90).atStartOfDay();
            stats.setCreditAging0to30(invoiceBillRepository.sumOutstandingAfter(d30, scid));
            stats.setCreditAging31to60(invoiceBillRepository.sumOutstandingBetween(d60, d30, scid));
            stats.setCreditAging61to90(invoiceBillRepository.sumOutstandingBetween(d90, d60, scid));
            stats.setCreditAging90Plus(invoiceBillRepository.sumOutstandingBefore(d90, scid));
        } catch (Exception e) {
            stats.setTotalOutstanding(BigDecimal.ZERO);
            stats.setTotalCreditCustomers(0);
            stats.setCreditAging0to30(BigDecimal.ZERO);
            stats.setCreditAging31to60(BigDecimal.ZERO);
            stats.setCreditAging61to90(BigDecimal.ZERO);
            stats.setCreditAging90Plus(BigDecimal.ZERO);
        }

        // --- Daily revenue trend (last 7 days — aggregate query) ---
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        List<Object[]> dailyData = invoiceBillRepository.getDailyRevenueSummary(weekStart, todayEnd, scid);
        Map<LocalDate, Object[]> dailyMap = new HashMap<>();
        for (Object[] row : dailyData) {
            dailyMap.put((LocalDate) row[0], row);
        }
        List<DailyRevenue> dailyRevenue = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DailyRevenue dr = new DailyRevenue();
            dr.setDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            Object[] row = dailyMap.get(date);
            if (row != null) {
                dr.setRevenue(row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
                dr.setInvoiceCount(row[2] != null ? ((Number) row[2]).intValue() : 0);
                dr.setFuelVolume(BigDecimal.ZERO);
            } else {
                dr.setRevenue(BigDecimal.ZERO);
                dr.setInvoiceCount(0);
                dr.setFuelVolume(BigDecimal.ZERO);
            }
            dailyRevenue.add(dr);
        }
        stats.setDailyRevenue(dailyRevenue);

        // --- Product-wise sales for today (aggregate query) ---
        List<Object[]> productData = invoiceBillRepository.getProductSalesToday(todayStart, todayEnd, scid);
        List<ProductSales> productSales = productData.stream().map(row -> {
            ProductSales ps = new ProductSales();
            ps.setProductName((String) row[0]);
            ps.setQuantity(row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
            ps.setAmount(row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
            return ps;
        }).collect(Collectors.toList());
        stats.setProductSales(productSales);

        // --- Tank status ---
        List<Tank> tanks = tankRepository.findAllByScid(scid);
        List<TankStatus> tankStatuses = tanks.stream().map(tank -> {
            TankStatus ts = new TankStatus();
            ts.setTankId(tank.getId());
            ts.setTankName(tank.getName());
            ts.setProductName(tank.getProduct() != null ? tank.getProduct().getName() : null);
            ts.setCapacity(tank.getCapacity());
            ts.setCurrentStock(tank.getAvailableStock() != null ? tank.getAvailableStock() : 0.0);
            ts.setThresholdStock(tank.getThresholdStock());
            ts.setProductPrice(tank.getProduct() != null ? tank.getProduct().getPrice() : null);
            ts.setActive(tank.isActive());

            TankInventory latestInv = tankInventoryRepository.findTopByTankIdAndScidOrderByDateDescIdDesc(tank.getId(), scid);
            if (latestInv != null) {
                ts.setLastReadingDate(latestInv.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            return ts;
        }).collect(Collectors.toList());
        stats.setTankStatuses(tankStatuses);

        // --- Recent invoices (last 10 — lightweight query, no entity graph) ---
        List<Object[]> recentData = invoiceBillRepository.findRecentInvoicesLight(scid, PageRequest.of(0, 10));
        List<RecentInvoiceItem> recentInvoices = recentData.stream()
                .map(row -> {
                    RecentInvoiceItem item = new RecentInvoiceItem();
                    item.setId(((Number) row[0]).longValue());
                    item.setDate(row[1] != null ? ((LocalDateTime) row[1]).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
                    item.setCustomerName((String) row[2]);
                    item.setBillType(row[3] != null ? row[3].toString() : null);
                    item.setAmount(row[4] != null ? (BigDecimal) row[4] : null);
                    item.setPaymentStatus(row[5] != null ? row[5].toString() : null);
                    return item;
                })
                .collect(Collectors.toList());
        stats.setRecentInvoices(recentInvoices);

        return stats;
    }

    @Transactional(readOnly = true)
    public InvoiceAnalytics getInvoiceAnalytics(LocalDate from, LocalDate to) {
        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(29);
        LocalDateTime fromDt = startDate.atStartOfDay();
        LocalDateTime toDt = endDate.atTime(LocalTime.MAX);

        Long scid = com.stopforfuel.config.SecurityUtils.getScid();
        InvoiceAnalytics analytics = new InvoiceAnalytics();
        analytics.setFromDate(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        analytics.setToDate(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Summary by billType + paymentStatus
        List<Object[]> summary = invoiceBillRepository.getInvoiceSummary(fromDt, toDt, scid);
        long totalCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long cashCount = 0, creditCount = 0;
        BigDecimal cashAmount = BigDecimal.ZERO, creditAmount = BigDecimal.ZERO;
        long paidCount = 0, unpaidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO, unpaidAmount = BigDecimal.ZERO;

        for (Object[] row : summary) {
            String billType = row[0].toString();
            String paymentStatus = row[1].toString();
            long count = ((Number) row[2]).longValue();
            BigDecimal amount = (BigDecimal) row[3];

            totalCount += count;
            totalRevenue = totalRevenue.add(amount);

            if ("CASH".equals(billType)) {
                cashCount += count;
                cashAmount = cashAmount.add(amount);
            } else {
                creditCount += count;
                creditAmount = creditAmount.add(amount);
            }
            if ("PAID".equals(paymentStatus)) {
                paidCount += count;
                paidAmount = paidAmount.add(amount);
            } else {
                unpaidCount += count;
                unpaidAmount = unpaidAmount.add(amount);
            }
        }

        analytics.setTotalInvoices(totalCount);
        analytics.setTotalRevenue(totalRevenue);
        analytics.setAvgInvoiceValue(totalCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        analytics.setCashCount(cashCount);
        analytics.setCashAmount(cashAmount);
        analytics.setCreditCount(creditCount);
        analytics.setCreditAmount(creditAmount);
        analytics.setPaidCount(paidCount);
        analytics.setPaidAmount(paidAmount);
        analytics.setUnpaidCount(unpaidCount);
        analytics.setUnpaidAmount(unpaidAmount);

        // Daily trend
        List<Object[]> dailyRaw = invoiceBillRepository.getDailyInvoiceStats(fromDt, toDt, scid);
        Map<LocalDate, InvoiceDailyTrend> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            InvoiceDailyTrend t = new InvoiceDailyTrend();
            t.setDate(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dailyMap.put(d, t);
        }
        for (Object[] row : dailyRaw) {
            LocalDate date = (LocalDate) row[0];
            String billType = row[1].toString();
            long count = ((Number) row[2]).longValue();
            BigDecimal amount = (BigDecimal) row[3];
            InvoiceDailyTrend t = dailyMap.get(date);
            if (t != null) {
                t.setTotalCount(t.getTotalCount() + count);
                t.setTotalAmount(t.getTotalAmount().add(amount));
                if ("CASH".equals(billType)) {
                    t.setCashAmount(t.getCashAmount().add(amount));
                    t.setCashCount(t.getCashCount() + count);
                } else {
                    t.setCreditAmount(t.getCreditAmount().add(amount));
                    t.setCreditCount(t.getCreditCount() + count);
                }
            }
        }
        analytics.setDailyTrend(new ArrayList<>(dailyMap.values()));

        // Payment mode distribution
        List<Object[]> modeRaw = invoiceBillRepository.getPaymentModeDistribution(fromDt, toDt, scid);
        List<NameCountAmount> paymentModes = new ArrayList<>();
        for (Object[] row : modeRaw) {
            paymentModes.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setPaymentModeDistribution(paymentModes);

        // Top customers
        List<Object[]> custRaw = invoiceBillRepository.getTopCustomersByRevenue(fromDt, toDt, scid);
        List<NameCountAmount> topCustomers = new ArrayList<>();
        for (int i = 0; i < Math.min(10, custRaw.size()); i++) {
            Object[] row = custRaw.get(i);
            topCustomers.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setTopCustomers(topCustomers);

        // Product breakdown
        var productSummaries = invoiceBillRepository.getProductSalesSummary(null, null, null, fromDt, toDt, scid);
        List<ProductBreakdown> products = new ArrayList<>();
        for (var ps : productSummaries) {
            ProductBreakdown pb = new ProductBreakdown();
            pb.setProductName(ps.getProductName());
            pb.setQuantity(ps.getTotalQuantity());
            pb.setAmount(ps.getTotalAmount());
            products.add(pb);
        }
        analytics.setProductBreakdown(products);

        // Hourly distribution
        List<Object[]> hourlyRaw = invoiceBillRepository.getHourlyDistribution(fromDt, toDt, scid);
        List<HourlyData> hourlyData = new ArrayList<>();
        Map<Integer, Long> hourMap = new HashMap<>();
        for (Object[] row : hourlyRaw) {
            hourMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        for (int h = 0; h < 24; h++) {
            HourlyData hd = new HourlyData();
            hd.setHour(h);
            hd.setCount(hourMap.getOrDefault(h, 0L));
            hourlyData.add(hd);
        }
        analytics.setHourlyDistribution(hourlyData);

        return analytics;
    }

    @Transactional(readOnly = true)
    public PaymentAnalytics getPaymentAnalytics(LocalDate from, LocalDate to) {
        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(29);
        LocalDateTime fromDt = startDate.atStartOfDay();
        LocalDateTime toDt = endDate.atTime(LocalTime.MAX);

        Long scid = com.stopforfuel.config.SecurityUtils.getScid();
        PaymentAnalytics analytics = new PaymentAnalytics();
        analytics.setFromDate(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        analytics.setToDate(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // KPIs
        BigDecimal totalCollected = paymentRepository.sumPaymentsInDateRange(fromDt, toDt, scid);
        long totalPayments = paymentRepository.countPaymentsInDateRange(fromDt, toDt, scid);
        analytics.setTotalCollected(totalCollected);
        analytics.setTotalPayments(totalPayments);
        analytics.setAvgPaymentAmount(totalPayments > 0
                ? totalCollected.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Credit outstanding (lightweight aggregate queries instead of loading all entities)
        try {
            analytics.setTotalOutstanding(invoiceBillRepository.sumUnpaidCreditAmount(scid));
            analytics.setCreditCustomers((int) invoiceBillRepository.countCustomersWithUnpaidCredit(scid));
            Object[] aging = invoiceBillRepository.getUnpaidCreditAgingBuckets(scid);
            if (aging != null) {
                analytics.setAging0to30((BigDecimal) aging[0]);
                analytics.setAging31to60((BigDecimal) aging[1]);
                analytics.setAging61to90((BigDecimal) aging[2]);
                analytics.setAging90Plus((BigDecimal) aging[3]);
            }
        } catch (Exception e) {
            analytics.setTotalOutstanding(BigDecimal.ZERO);
            analytics.setAging0to30(BigDecimal.ZERO);
            analytics.setAging31to60(BigDecimal.ZERO);
            analytics.setAging61to90(BigDecimal.ZERO);
            analytics.setAging90Plus(BigDecimal.ZERO);
        }

        // Collection rate
        BigDecimal denominator = totalCollected.add(analytics.getTotalOutstanding());
        analytics.setCollectionRate(denominator.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Daily trend
        List<Object[]> dailyRaw = paymentRepository.getDailyPaymentStats(fromDt, toDt, scid);
        Map<LocalDate, PaymentDailyTrend> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            PaymentDailyTrend t = new PaymentDailyTrend();
            t.setDate(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dailyMap.put(d, t);
        }
        for (Object[] row : dailyRaw) {
            LocalDate date = (LocalDate) row[0];
            PaymentDailyTrend t = dailyMap.get(date);
            if (t != null) {
                t.setCount(((Number) row[1]).longValue());
                t.setAmount((BigDecimal) row[2]);
            }
        }
        analytics.setDailyTrend(new ArrayList<>(dailyMap.values()));

        // Payment mode breakdown
        List<Object[]> modeRaw = paymentRepository.getPaymentModeBreakdown(fromDt, toDt, scid);
        List<NameCountAmount> modes = new ArrayList<>();
        for (Object[] row : modeRaw) {
            modes.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setPaymentModeBreakdown(modes);

        // Top paying customers
        List<Object[]> custRaw = paymentRepository.getTopPayingCustomers(fromDt, toDt, scid);
        List<NameCountAmount> topCustomers = new ArrayList<>();
        for (int i = 0; i < Math.min(10, custRaw.size()); i++) {
            Object[] row = custRaw.get(i);
            topCustomers.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setTopCustomers(topCustomers);

        return analytics;
    }

    @Transactional(readOnly = true)
    public CashierDashboard getCashierDashboard() {
        CashierDashboard dashboard = new CashierDashboard();

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            dashboard.setHasActiveShift(false);
            return dashboard;
        }

        dashboard.setHasActiveShift(true);
        dashboard.setShiftId(activeShift.getId());
        dashboard.setShiftStatus(activeShift.getStatus() != null ? activeShift.getStatus().name() : null);
        dashboard.setStartTime(activeShift.getStartTime() != null
                ? activeShift.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        dashboard.setEndTime(activeShift.getEndTime() != null
                ? activeShift.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        if (activeShift.getAttendant() != null) {
            dashboard.setAttendantName(activeShift.getAttendant().getName());
        }

        Long sid = activeShift.getId();

        // Invoice totals
        BigDecimal cashBills = invoiceBillRepository.sumCashBillsByShift(sid);
        BigDecimal creditBills = invoiceBillRepository.sumCreditBillsByShift(sid);
        dashboard.setCashBillTotal(cashBills);
        dashboard.setCreditBillTotal(creditBills);

        // E-Advance totals
        Map<String, BigDecimal> eAdvSummary = eAdvanceService.getShiftSummary(sid);
        dashboard.setEAdvanceTotals(eAdvSummary);

        // Expenses
        BigDecimal expenses = expenseService.sumByShift(sid);
        dashboard.setExpenseTotal(expenses);

        // Payments collected
        BigDecimal billPayments = paymentRepository.sumBillPaymentsByShift(sid);
        BigDecimal stmtPayments = paymentRepository.sumStatementPaymentsByShift(sid);
        dashboard.setBillPaymentTotal(billPayments != null ? billPayments : BigDecimal.ZERO);
        dashboard.setStatementPaymentTotal(stmtPayments != null ? stmtPayments : BigDecimal.ZERO);

        // Operational advances
        List<OperationalAdvance> opAdvances = operationalAdvanceRepository.findByShiftIdOrderByIdDesc(sid);
        BigDecimal opAdvanceTotal = opAdvances.stream()
                .map(oa -> oa.getAmount() != null ? oa.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dashboard.setOperationalAdvanceTotal(opAdvanceTotal);
        dashboard.setOperationalAdvanceCount(opAdvances.size());

        // Incentive payments
        List<IncentivePayment> incentives = incentivePaymentRepository.findByShiftIdOrderByIdDesc(sid);
        BigDecimal incentiveTotal = incentives.stream()
                .map(ip -> ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dashboard.setIncentiveTotal(incentiveTotal);
        dashboard.setIncentiveCount(incentives.size());

        // Invoice counts
        List<InvoiceBill> shiftInvoices = invoiceBillRepository.findByShiftIdOrderByIdDesc(sid);
        dashboard.setTotalInvoiceCount(shiftInvoices.size());
        dashboard.setCashInvoiceCount((int) shiftInvoices.stream()
                .filter(inv -> com.stopforfuel.backend.enums.BillType.CASH.equals(inv.getBillType())).count());
        dashboard.setCreditInvoiceCount((int) shiftInvoices.stream()
                .filter(inv -> com.stopforfuel.backend.enums.BillType.CREDIT.equals(inv.getBillType())).count());

        // Recent invoices (last 10)
        List<CashierInvoiceItem> recentInvoices = shiftInvoices.stream()
                .sorted((a, b) -> {
                    if (a.getDate() == null) return 1;
                    if (b.getDate() == null) return -1;
                    return b.getDate().compareTo(a.getDate());
                })
                .limit(10)
                .map(inv -> {
                    CashierInvoiceItem item = new CashierInvoiceItem();
                    item.setId(inv.getId());
                    item.setBillNo(inv.getBillNo());
                    item.setBillType(inv.getBillType() != null ? inv.getBillType().name() : null);
                    item.setPaymentMode(inv.getPaymentMode() != null ? inv.getPaymentMode().name() : null);
                    item.setNetAmount(inv.getNetAmount());
                    item.setDate(inv.getDate() != null
                            ? inv.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
                    if (inv.getCustomer() != null) {
                        item.setCustomerName(inv.getCustomer().getName());
                    }
                    return item;
                })
                .toList();
        dashboard.setRecentInvoices(recentInvoices);

        // Computed: cash in hand
        BigDecimal totalIn = cashBills
                .add(dashboard.getBillPaymentTotal())
                .add(dashboard.getStatementPaymentTotal());
        BigDecimal totalOut = expenses.add(opAdvanceTotal).add(incentiveTotal);
        dashboard.setCashInHand(totalIn.subtract(totalOut));

        return dashboard;
    }

    @Transactional(readOnly = true)
    public SystemHealth getSystemHealth() {
        SystemHealth health = new SystemHealth();
        Long scid = com.stopforfuel.config.SecurityUtils.getScid();

        health.setTotalCustomers(customerRepository.count());
        health.setActiveCustomers(customerRepository.countByStatus(com.stopforfuel.backend.enums.EntityStatus.ACTIVE));
        health.setBlockedCustomers(customerRepository.countByStatus(com.stopforfuel.backend.enums.EntityStatus.BLOCKED));
        health.setInactiveCustomers(customerRepository.countByStatus(com.stopforfuel.backend.enums.EntityStatus.INACTIVE));
        health.setTotalVehicles(vehicleRepository.count());
        health.setTotalEmployees(employeeRepository.countByScid(scid));
        health.setTotalUsers(userRepository.countByScid(scid));
        health.setTotalProducts(productRepository.countByScid(scid));

        // Active shifts
        health.setActiveShiftCount(shiftRepository.countByScidAndStatus(scid, com.stopforfuel.backend.enums.ShiftStatus.OPEN));

        // Today's attendance
        LocalDate today = LocalDate.now();
        health.setTodayAttendanceCount(attendanceRepository.countByDateAndScid(today, scid));

        return health;
    }
}
