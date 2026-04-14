package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.DashboardService;
import com.stopforfuel.backend.service.EmployeeDashboardService;
import com.stopforfuel.config.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final EmployeeDashboardService employeeDashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public DashboardStats getStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/invoice-analytics")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public InvoiceAnalytics getInvoiceAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.getInvoiceAnalytics(from, to);
    }

    @GetMapping("/payment-analytics")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public PaymentAnalytics getPaymentAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.getPaymentAnalytics(from, to);
    }

    @GetMapping("/cashier")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public CashierDashboard getCashierDashboard() {
        return dashboardService.getCashierDashboard();
    }

    @GetMapping("/employee")
    public EmployeeDashboardService.EmployeeDashboardData getEmployeeDashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return employeeDashboardService.getDashboard(userId);
    }

    @GetMapping("/system-health")
    @PreAuthorize("hasPermission(null, 'DASHBOARD_VIEW')")
    public SystemHealth getSystemHealth() {
        return dashboardService.getSystemHealth();
    }

    // --- DTOs ---

    @Getter @Setter
    public static class DashboardStats {
        private BigDecimal todayRevenue;
        private BigDecimal todayFuelVolume;
        private long todayInvoiceCount;
        private long todayCashInvoices;
        private long todayCreditInvoices;
        private Long activeShiftId;
        private String activeShiftStartTime;
        private BigDecimal shiftCash;
        private BigDecimal shiftUpi;
        private BigDecimal shiftCard;
        private BigDecimal shiftCheque;
        private BigDecimal shiftCcms;
        private BigDecimal shiftBankTransfer;
        private BigDecimal shiftExpense;
        private BigDecimal shiftTotal;
        private BigDecimal shiftNet;
        private long totalTanks;
        private long activeTanks;
        private long totalPumps;
        private long activePumps;
        private long totalNozzles;
        private long activeNozzles;
        private BigDecimal totalOutstanding;
        private long totalCreditCustomers;
        private BigDecimal creditAging0to30;
        private BigDecimal creditAging31to60;
        private BigDecimal creditAging61to90;
        private BigDecimal creditAging90Plus;
        private List<DailyRevenue> dailyRevenue;
        private List<ProductSales> productSales;
        private List<ProductSales> lastShiftProductSales;
        private Long lastShiftId;
        private long totalStatements;
        private long paidStatements;
        private long unpaidStatements;
        private List<ProductSales> mtdSales;
        private List<ProductPurchase> mtdPurchases;
        private long mtdCreditCount;
        private BigDecimal mtdCreditAmount;
        private long mtdPaymentCount;
        private BigDecimal mtdPaymentAmount;
        private List<TankStatus> tankStatuses;
        private List<RecentInvoiceItem> recentInvoices;
    }

    @Getter @Setter
    public static class DailyRevenue {
        private String date;
        private BigDecimal revenue;
        private long invoiceCount;
        private BigDecimal fuelVolume;
    }

    @Getter @Setter
    public static class ProductSales {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal amount;
    }

    @Getter @Setter
    public static class ProductPurchase {
        private String productName;
        private Double quantity;
    }

    @Getter @Setter
    public static class TankStatus {
        private Long tankId;
        private String tankName;
        private String productName;
        private double capacity;
        private double currentStock;
        private Double thresholdStock;
        private BigDecimal productPrice;
        private boolean active;
        private String lastReadingDate;
    }

    @Getter @Setter
    public static class RecentInvoiceItem {
        private Long id;
        private String date;
        private String customerName;
        private String billType;
        private BigDecimal amount;
        private String paymentStatus;
    }

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

    @Getter @Setter @NoArgsConstructor
    public static class CashierDashboard {
        private boolean hasActiveShift;
        private Long shiftId;
        private String shiftStatus;
        private String startTime;
        private String endTime;
        private String attendantName;
        private BigDecimal cashBillTotal = BigDecimal.ZERO;
        private BigDecimal creditBillTotal = BigDecimal.ZERO;
        private int totalInvoiceCount;
        private int cashInvoiceCount;
        private int creditInvoiceCount;
        private Map<String, BigDecimal> eAdvanceTotals = new HashMap<>();
        private BigDecimal billPaymentTotal = BigDecimal.ZERO;
        private BigDecimal statementPaymentTotal = BigDecimal.ZERO;
        private BigDecimal expenseTotal = BigDecimal.ZERO;
        private BigDecimal operationalAdvanceTotal = BigDecimal.ZERO;
        private int operationalAdvanceCount;
        private BigDecimal incentiveTotal = BigDecimal.ZERO;
        private int incentiveCount;
        private BigDecimal cashInHand = BigDecimal.ZERO;
        private List<CashierInvoiceItem> recentInvoices = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class CashierInvoiceItem {
        private Long id;
        private String billNo;
        private String billType;
        private String paymentMode;
        private BigDecimal netAmount;
        private String date;
        private String customerName;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SystemHealth {
        private long totalCustomers;
        private long activeCustomers;
        private long blockedCustomers;
        private long inactiveCustomers;
        private long totalVehicles;
        private long totalEmployees;
        private long activeEmployees;
        private long totalUsers;
        private long activeShiftCount;
        private long todayAttendanceCount;
        private long totalProducts;
    }
}
