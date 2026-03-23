package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.repository.TankInventoryRepository;
import com.stopforfuel.backend.service.CreditManagementService;
import com.stopforfuel.backend.service.ShiftService;
import com.stopforfuel.backend.service.ShiftTransactionService;
import com.stopforfuel.backend.entity.Shift;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final TankRepository tankRepository;
    private final PumpRepository pumpRepository;
    private final NozzleRepository nozzleRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final ShiftService shiftService;
    private final ShiftTransactionService shiftTransactionService;
    private final CreditManagementService creditManagementService;

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();

        // --- Today's stats ---
        List<InvoiceBill> allInvoices = invoiceBillRepository.findAll();
        LocalDate today = LocalDate.now();
        List<InvoiceBill> todayInvoices = allInvoices.stream()
                .filter(inv -> inv.getDate() != null && inv.getDate().toLocalDate().equals(today))
                .collect(Collectors.toList());

        stats.setTodayRevenue(todayInvoices.stream()
                .map(inv -> inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        stats.setTodayFuelVolume(todayInvoices.stream()
                .filter(inv -> inv.getProducts() != null)
                .flatMap(inv -> inv.getProducts().stream())
                .map(ip -> ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        stats.setTodayInvoiceCount(todayInvoices.size());

        stats.setTodayCashInvoices(todayInvoices.stream()
                .filter(inv -> "CASH".equals(inv.getBillType()))
                .count());

        stats.setTodayCreditInvoices(todayInvoices.stream()
                .filter(inv -> "CREDIT".equals(inv.getBillType()))
                .count());

        // --- Active shift stats ---
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            stats.setActiveShiftId(activeShift.getId());
            stats.setActiveShiftStartTime(activeShift.getStartTime() != null
                    ? activeShift.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);

            Map<String, Object> summary = shiftTransactionService.getShiftSummary(activeShift.getId());
            stats.setShiftCash(toBigDecimal(summary.get("cash")));
            stats.setShiftUpi(toBigDecimal(summary.get("upi")));
            stats.setShiftCard(toBigDecimal(summary.get("card")));
            stats.setShiftExpense(toBigDecimal(summary.get("expense")));
            stats.setShiftTotal(toBigDecimal(summary.get("total")));
            stats.setShiftNet(toBigDecimal(summary.get("net")));
        }

        // --- Station stats ---
        stats.setTotalTanks(tankRepository.count());
        stats.setActiveTanks(tankRepository.findByActive(true).size());
        stats.setTotalPumps(pumpRepository.count());
        stats.setActivePumps(pumpRepository.findByActive(true).size());
        stats.setTotalNozzles(nozzleRepository.count());
        stats.setActiveNozzles(nozzleRepository.findByActive(true).size());

        // --- Credit stats + aging ---
        try {
            CreditManagementService.CreditOverview creditOverview = creditManagementService.getCreditOverview(null);
            stats.setTotalOutstanding(creditOverview.getTotalOutstanding());
            stats.setTotalCreditCustomers(creditOverview.getTotalCreditCustomers());
            stats.setCreditAging0to30(creditOverview.getTotalAging0to30());
            stats.setCreditAging31to60(creditOverview.getTotalAging31to60());
            stats.setCreditAging61to90(creditOverview.getTotalAging61to90());
            stats.setCreditAging90Plus(creditOverview.getTotalAging90Plus());
        } catch (Exception e) {
            stats.setTotalOutstanding(BigDecimal.ZERO);
            stats.setTotalCreditCustomers(0);
            stats.setCreditAging0to30(BigDecimal.ZERO);
            stats.setCreditAging31to60(BigDecimal.ZERO);
            stats.setCreditAging61to90(BigDecimal.ZERO);
            stats.setCreditAging90Plus(BigDecimal.ZERO);
        }

        // --- Daily revenue trend (last 7 days) ---
        List<DailyRevenue> dailyRevenue = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            BigDecimal revenue = allInvoices.stream()
                    .filter(inv -> inv.getDate() != null && inv.getDate().toLocalDate().equals(date))
                    .map(inv -> inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = allInvoices.stream()
                    .filter(inv -> inv.getDate() != null && inv.getDate().toLocalDate().equals(date))
                    .count();
            BigDecimal fuelVolume = allInvoices.stream()
                    .filter(inv -> inv.getDate() != null && inv.getDate().toLocalDate().equals(date))
                    .filter(inv -> inv.getProducts() != null)
                    .flatMap(inv -> inv.getProducts().stream())
                    .map(ip -> ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            DailyRevenue dr = new DailyRevenue();
            dr.setDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dr.setRevenue(revenue);
            dr.setInvoiceCount(count);
            dr.setFuelVolume(fuelVolume);
            dailyRevenue.add(dr);
        }
        stats.setDailyRevenue(dailyRevenue);

        // --- Product-wise sales for today ---
        Map<String, BigDecimal[]> productMap = new LinkedHashMap<>();
        todayInvoices.stream()
                .filter(inv -> inv.getProducts() != null)
                .flatMap(inv -> inv.getProducts().stream())
                .forEach(ip -> {
                    String productName = ip.getProduct() != null ? ip.getProduct().getName() : "Unknown";
                    productMap.computeIfAbsent(productName, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    BigDecimal[] vals = productMap.get(productName);
                    vals[0] = vals[0].add(ip.getQuantity() != null ? ip.getQuantity() : BigDecimal.ZERO);
                    vals[1] = vals[1].add(ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO);
                });
        List<ProductSales> productSales = productMap.entrySet().stream()
                .map(e -> {
                    ProductSales ps = new ProductSales();
                    ps.setProductName(e.getKey());
                    ps.setQuantity(e.getValue()[0]);
                    ps.setAmount(e.getValue()[1]);
                    return ps;
                })
                .collect(Collectors.toList());
        stats.setProductSales(productSales);

        // --- Tank status ---
        List<Tank> tanks = tankRepository.findAll();
        List<TankStatus> tankStatuses = tanks.stream().map(tank -> {
            TankStatus ts = new TankStatus();
            ts.setTankId(tank.getId());
            ts.setTankName(tank.getName());
            ts.setProductName(tank.getProduct() != null ? tank.getProduct().getName() : null);
            ts.setCapacity(tank.getCapacity());
            ts.setActive(tank.isActive());

            TankInventory latestInv = tankInventoryRepository.findTopByTankIdOrderByDateDescIdDesc(tank.getId());
            if (latestInv != null) {
                ts.setCurrentStock(latestInv.getCloseStock() != null ? latestInv.getCloseStock() : 0.0);
                ts.setLastReadingDate(latestInv.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            } else {
                ts.setCurrentStock(0.0);
            }
            return ts;
        }).collect(Collectors.toList());
        stats.setTankStatuses(tankStatuses);

        // --- Recent invoices (last 10) ---
        List<RecentInvoiceItem> recentInvoices = allInvoices.stream()
                .filter(inv -> inv.getDate() != null)
                .sorted(Comparator.comparing(InvoiceBill::getDate).reversed())
                .limit(10)
                .map(inv -> {
                    RecentInvoiceItem item = new RecentInvoiceItem();
                    item.setId(inv.getId());
                    item.setDate(inv.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    item.setCustomerName(inv.getCustomer() != null ? inv.getCustomer().getName() : null);
                    item.setBillType(inv.getBillType());
                    item.setAmount(inv.getNetAmount());
                    item.setPaymentStatus(inv.getPaymentStatus());
                    return item;
                })
                .collect(Collectors.toList());
        stats.setRecentInvoices(recentInvoices);

        return stats;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return BigDecimal.ZERO;
    }

    // --- DTOs ---

    @Getter
    @Setter
    public static class DashboardStats {
        // Today's stats
        private BigDecimal todayRevenue;
        private BigDecimal todayFuelVolume;
        private long todayInvoiceCount;
        private long todayCashInvoices;
        private long todayCreditInvoices;

        // Active shift stats (null if no active shift)
        private Long activeShiftId;
        private String activeShiftStartTime;
        private BigDecimal shiftCash;
        private BigDecimal shiftUpi;
        private BigDecimal shiftCard;
        private BigDecimal shiftExpense;
        private BigDecimal shiftTotal;
        private BigDecimal shiftNet;

        // Station stats
        private long totalTanks;
        private long activeTanks;
        private long totalPumps;
        private long activePumps;
        private long totalNozzles;
        private long activeNozzles;

        // Credit stats + aging
        private BigDecimal totalOutstanding;
        private long totalCreditCustomers;
        private BigDecimal creditAging0to30;
        private BigDecimal creditAging31to60;
        private BigDecimal creditAging61to90;
        private BigDecimal creditAging90Plus;

        // 7-day revenue trend
        private List<DailyRevenue> dailyRevenue;

        // Today's product-wise sales
        private List<ProductSales> productSales;

        // Tank status
        private List<TankStatus> tankStatuses;

        // Recent invoices (last 10)
        private List<RecentInvoiceItem> recentInvoices;
    }

    @Getter
    @Setter
    public static class DailyRevenue {
        private String date;
        private BigDecimal revenue;
        private long invoiceCount;
        private BigDecimal fuelVolume;
    }

    @Getter
    @Setter
    public static class ProductSales {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal amount;
    }

    @Getter
    @Setter
    public static class TankStatus {
        private Long tankId;
        private String tankName;
        private String productName;
        private double capacity;
        private double currentStock;
        private boolean active;
        private String lastReadingDate;
    }

    @Getter
    @Setter
    public static class RecentInvoiceItem {
        private Long id;
        private String date;
        private String customerName;
        private String billType;
        private BigDecimal amount;
        private String paymentStatus;
    }

    // ==============================
    // Invoice Analytics Dashboard
    // ==============================

    @GetMapping("/invoice-analytics")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public InvoiceAnalytics getInvoiceAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(29);
        LocalDateTime fromDt = startDate.atStartOfDay();
        LocalDateTime toDt = endDate.atTime(LocalTime.MAX);

        InvoiceAnalytics analytics = new InvoiceAnalytics();
        analytics.setFromDate(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        analytics.setToDate(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Summary by billType + paymentStatus
        List<Object[]> summary = invoiceBillRepository.getInvoiceSummary(fromDt, toDt);
        long totalCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long cashCount = 0, creditCount = 0;
        BigDecimal cashAmount = BigDecimal.ZERO, creditAmount = BigDecimal.ZERO;
        long paidCount = 0, unpaidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO, unpaidAmount = BigDecimal.ZERO;

        for (Object[] row : summary) {
            String billType = (String) row[0];
            String paymentStatus = (String) row[1];
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
        List<Object[]> dailyRaw = invoiceBillRepository.getDailyInvoiceStats(fromDt, toDt);
        Map<LocalDate, InvoiceDailyTrend> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            InvoiceDailyTrend t = new InvoiceDailyTrend();
            t.setDate(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dailyMap.put(d, t);
        }
        for (Object[] row : dailyRaw) {
            LocalDate date = (LocalDate) row[0];
            String billType = (String) row[1];
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

        // Payment mode distribution (cash invoices)
        List<Object[]> modeRaw = invoiceBillRepository.getPaymentModeDistribution(fromDt, toDt);
        List<NameCountAmount> paymentModes = new ArrayList<>();
        for (Object[] row : modeRaw) {
            paymentModes.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setPaymentModeDistribution(paymentModes);

        // Top customers (limit 10)
        List<Object[]> custRaw = invoiceBillRepository.getTopCustomersByRevenue(fromDt, toDt);
        List<NameCountAmount> topCustomers = new ArrayList<>();
        for (int i = 0; i < Math.min(10, custRaw.size()); i++) {
            Object[] row = custRaw.get(i);
            topCustomers.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setTopCustomers(topCustomers);

        // Product breakdown
        var productSummaries = invoiceBillRepository.getProductSalesSummary(null, null, null, fromDt, toDt);
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
        List<Object[]> hourlyRaw = invoiceBillRepository.getHourlyDistribution(fromDt, toDt);
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

    // ==============================
    // Payment Analytics Dashboard
    // ==============================

    @GetMapping("/payment-analytics")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public PaymentAnalytics getPaymentAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(29);
        LocalDateTime fromDt = startDate.atStartOfDay();
        LocalDateTime toDt = endDate.atTime(LocalTime.MAX);

        PaymentAnalytics analytics = new PaymentAnalytics();
        analytics.setFromDate(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        analytics.setToDate(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        // KPIs
        BigDecimal totalCollected = paymentRepository.sumPaymentsInDateRange(fromDt, toDt);
        long totalPayments = paymentRepository.countPaymentsInDateRange(fromDt, toDt);
        analytics.setTotalCollected(totalCollected);
        analytics.setTotalPayments(totalPayments);
        analytics.setAvgPaymentAmount(totalPayments > 0
                ? totalCollected.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Credit outstanding
        try {
            CreditManagementService.CreditOverview creditOverview = creditManagementService.getCreditOverview(null);
            analytics.setTotalOutstanding(creditOverview.getTotalOutstanding());
            analytics.setCreditCustomers(creditOverview.getTotalCreditCustomers());
            analytics.setAging0to30(creditOverview.getTotalAging0to30());
            analytics.setAging31to60(creditOverview.getTotalAging31to60());
            analytics.setAging61to90(creditOverview.getTotalAging61to90());
            analytics.setAging90Plus(creditOverview.getTotalAging90Plus());
        } catch (Exception e) {
            analytics.setTotalOutstanding(BigDecimal.ZERO);
            analytics.setAging0to30(BigDecimal.ZERO);
            analytics.setAging31to60(BigDecimal.ZERO);
            analytics.setAging61to90(BigDecimal.ZERO);
            analytics.setAging90Plus(BigDecimal.ZERO);
        }

        // Collection rate: collected / (collected + outstanding)
        BigDecimal denominator = totalCollected.add(analytics.getTotalOutstanding());
        analytics.setCollectionRate(denominator.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Daily trend
        List<Object[]> dailyRaw = paymentRepository.getDailyPaymentStats(fromDt, toDt);
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
        List<Object[]> modeRaw = paymentRepository.getPaymentModeBreakdown(fromDt, toDt);
        List<NameCountAmount> modes = new ArrayList<>();
        for (Object[] row : modeRaw) {
            modes.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setPaymentModeBreakdown(modes);

        // Top paying customers (limit 10)
        List<Object[]> custRaw = paymentRepository.getTopPayingCustomers(fromDt, toDt);
        List<NameCountAmount> topCustomers = new ArrayList<>();
        for (int i = 0; i < Math.min(10, custRaw.size()); i++) {
            Object[] row = custRaw.get(i);
            topCustomers.add(new NameCountAmount((String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        analytics.setTopCustomers(topCustomers);

        return analytics;
    }

    // ==============================
    // Invoice Analytics DTOs
    // ==============================

    @Getter @Setter
    public static class InvoiceAnalytics {
        private String fromDate;
        private String toDate;
        private long totalInvoices;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal avgInvoiceValue = BigDecimal.ZERO;
        private long cashCount;
        private BigDecimal cashAmount = BigDecimal.ZERO;
        private long creditCount;
        private BigDecimal creditAmount = BigDecimal.ZERO;
        private long paidCount;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private long unpaidCount;
        private BigDecimal unpaidAmount = BigDecimal.ZERO;
        private List<InvoiceDailyTrend> dailyTrend;
        private List<NameCountAmount> paymentModeDistribution;
        private List<NameCountAmount> topCustomers;
        private List<ProductBreakdown> productBreakdown;
        private List<HourlyData> hourlyDistribution;
    }

    @Getter @Setter
    public static class InvoiceDailyTrend {
        private String date;
        private long totalCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private long cashCount;
        private BigDecimal cashAmount = BigDecimal.ZERO;
        private long creditCount;
        private BigDecimal creditAmount = BigDecimal.ZERO;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class NameCountAmount {
        private String name;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class ProductBreakdown {
        private String productName;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter @Setter
    public static class HourlyData {
        private int hour;
        private long count;
    }

    // ==============================
    // Payment Analytics DTOs
    // ==============================

    @Getter @Setter
    public static class PaymentAnalytics {
        private String fromDate;
        private String toDate;
        private BigDecimal totalCollected = BigDecimal.ZERO;
        private long totalPayments;
        private BigDecimal avgPaymentAmount = BigDecimal.ZERO;
        private BigDecimal totalOutstanding = BigDecimal.ZERO;
        private long creditCustomers;
        private BigDecimal collectionRate = BigDecimal.ZERO;
        private BigDecimal aging0to30 = BigDecimal.ZERO;
        private BigDecimal aging31to60 = BigDecimal.ZERO;
        private BigDecimal aging61to90 = BigDecimal.ZERO;
        private BigDecimal aging90Plus = BigDecimal.ZERO;
        private List<PaymentDailyTrend> dailyTrend;
        private List<NameCountAmount> paymentModeBreakdown;
        private List<NameCountAmount> topCustomers;
    }

    @Getter @Setter
    public static class PaymentDailyTrend {
        private String date;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
