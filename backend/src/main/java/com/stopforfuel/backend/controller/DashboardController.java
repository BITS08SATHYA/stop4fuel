package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.service.CreditManagementService;
import com.stopforfuel.backend.service.ShiftService;
import com.stopforfuel.backend.service.ShiftTransactionService;
import com.stopforfuel.backend.entity.Shift;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DashboardController {

    private final InvoiceBillRepository invoiceBillRepository;
    private final TankRepository tankRepository;
    private final PumpRepository pumpRepository;
    private final NozzleRepository nozzleRepository;
    private final ShiftService shiftService;
    private final ShiftTransactionService shiftTransactionService;
    private final CreditManagementService creditManagementService;

    @GetMapping("/stats")
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

        // --- Credit stats ---
        try {
            CreditManagementService.CreditOverview creditOverview = creditManagementService.getCreditOverview();
            stats.setTotalOutstanding(creditOverview.getTotalOutstanding());
            stats.setTotalCreditCustomers(creditOverview.getTotalCreditCustomers());
        } catch (Exception e) {
            stats.setTotalOutstanding(BigDecimal.ZERO);
            stats.setTotalCreditCustomers(0);
        }

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

        // Credit stats
        private BigDecimal totalOutstanding;
        private long totalCreditCustomers;

        // Recent invoices (last 10)
        private List<RecentInvoiceItem> recentInvoices;
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
}
