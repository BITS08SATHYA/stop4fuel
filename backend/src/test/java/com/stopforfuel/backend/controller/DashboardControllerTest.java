package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.controller.DashboardController.*;
import com.stopforfuel.backend.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DashboardController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stopforfuel\\.config\\..*"))
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    // ===========================
    // /api/dashboard/stats
    // ===========================

    @Test
    void getStats_returnsAllFields() throws Exception {
        DashboardStats stats = new DashboardStats();
        stats.setTodayRevenue(new BigDecimal("5000"));
        stats.setTodayFuelVolume(new BigDecimal("200"));
        stats.setTodayInvoiceCount(1);
        stats.setTodayCashInvoices(1);
        stats.setTodayCreditInvoices(0);
        stats.setTotalTanks(2);
        stats.setActiveTanks(1);
        stats.setTotalPumps(4);
        stats.setActivePumps(0);
        stats.setTotalNozzles(8);
        stats.setActiveNozzles(0);
        stats.setTotalOutstanding(new BigDecimal("25000"));
        stats.setTotalCreditCustomers(5);
        stats.setCreditAging0to30(new BigDecimal("10000"));
        stats.setCreditAging31to60(new BigDecimal("8000"));
        stats.setCreditAging61to90(new BigDecimal("5000"));
        stats.setCreditAging90Plus(new BigDecimal("2000"));
        stats.setDailyRevenue(List.of(
                createDailyRevenue("2026-03-25"), createDailyRevenue("2026-03-26"),
                createDailyRevenue("2026-03-27"), createDailyRevenue("2026-03-28"),
                createDailyRevenue("2026-03-29"), createDailyRevenue("2026-03-30"),
                createDailyRevenue("2026-03-31")));
        stats.setProductSales(List.of());
        stats.setTankStatuses(List.of());
        stats.setRecentInvoices(List.of());
        when(dashboardService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayRevenue").value(5000))
                .andExpect(jsonPath("$.todayInvoiceCount").value(1))
                .andExpect(jsonPath("$.totalTanks").value(2))
                .andExpect(jsonPath("$.activeTanks").value(1))
                .andExpect(jsonPath("$.totalPumps").value(4))
                .andExpect(jsonPath("$.totalNozzles").value(8))
                .andExpect(jsonPath("$.totalOutstanding").value(25000))
                .andExpect(jsonPath("$.totalCreditCustomers").value(5))
                .andExpect(jsonPath("$.creditAging0to30").value(10000))
                .andExpect(jsonPath("$.creditAging90Plus").value(2000))
                .andExpect(jsonPath("$.dailyRevenue").isArray())
                .andExpect(jsonPath("$.dailyRevenue.length()").value(7))
                .andExpect(jsonPath("$.productSales").isArray())
                .andExpect(jsonPath("$.tankStatuses").isArray())
                .andExpect(jsonPath("$.recentInvoices").isArray());
    }

    @Test
    void getStats_withActiveShift_returnsShiftStats() throws Exception {
        DashboardStats stats = new DashboardStats();
        stats.setActiveShiftId(10L);
        stats.setActiveShiftStartTime("2026-03-31T08:00:00");
        stats.setShiftCash(new BigDecimal("3000"));
        stats.setShiftUpi(new BigDecimal("2000"));
        stats.setShiftCard(new BigDecimal("1000"));
        stats.setShiftExpense(new BigDecimal("500"));
        stats.setShiftTotal(new BigDecimal("6000"));
        stats.setShiftNet(new BigDecimal("5500"));
        stats.setTodayRevenue(BigDecimal.ZERO);
        stats.setTodayFuelVolume(BigDecimal.ZERO);
        stats.setTotalOutstanding(BigDecimal.ZERO);
        stats.setCreditAging0to30(BigDecimal.ZERO);
        stats.setCreditAging31to60(BigDecimal.ZERO);
        stats.setCreditAging61to90(BigDecimal.ZERO);
        stats.setCreditAging90Plus(BigDecimal.ZERO);
        stats.setDailyRevenue(List.of());
        stats.setProductSales(List.of());
        stats.setTankStatuses(List.of());
        stats.setRecentInvoices(List.of());
        when(dashboardService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeShiftId").value(10))
                .andExpect(jsonPath("$.shiftCash").value(3000))
                .andExpect(jsonPath("$.shiftUpi").value(2000))
                .andExpect(jsonPath("$.shiftCard").value(1000))
                .andExpect(jsonPath("$.shiftExpense").value(500))
                .andExpect(jsonPath("$.shiftTotal").value(6000))
                .andExpect(jsonPath("$.shiftNet").value(5500));
    }

    @Test
    void getStats_noInvoices_returnsZeros() throws Exception {
        DashboardStats stats = new DashboardStats();
        stats.setTodayRevenue(BigDecimal.ZERO);
        stats.setTodayFuelVolume(BigDecimal.ZERO);
        stats.setTodayInvoiceCount(0);
        stats.setTotalOutstanding(BigDecimal.ZERO);
        stats.setCreditAging0to30(BigDecimal.ZERO);
        stats.setCreditAging31to60(BigDecimal.ZERO);
        stats.setCreditAging61to90(BigDecimal.ZERO);
        stats.setCreditAging90Plus(BigDecimal.ZERO);
        stats.setDailyRevenue(List.of());
        stats.setProductSales(List.of());
        stats.setTankStatuses(List.of());
        stats.setRecentInvoices(List.of());
        when(dashboardService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayRevenue").value(0))
                .andExpect(jsonPath("$.todayFuelVolume").value(0))
                .andExpect(jsonPath("$.todayInvoiceCount").value(0))
                .andExpect(jsonPath("$.activeShiftId").isEmpty())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.recentInvoices").isEmpty());
    }

    // ===========================
    // /api/dashboard/invoice-analytics
    // ===========================

    @Test
    void getInvoiceAnalytics_defaultRange_returnsAnalytics() throws Exception {
        InvoiceAnalytics analytics = new InvoiceAnalytics();
        analytics.setTotalInvoices(5);
        analytics.setTotalRevenue(new BigDecimal("25000"));
        analytics.setAvgInvoiceValue(new BigDecimal("5000"));
        analytics.setCashCount(5);
        analytics.setCashAmount(new BigDecimal("25000"));
        analytics.setPaidCount(5);
        analytics.setPaidAmount(new BigDecimal("25000"));
        analytics.setDailyTrend(List.of());
        analytics.setPaymentModeDistribution(List.of(new NameCountAmount("Cash", 3, new BigDecimal("15000"))));
        analytics.setTopCustomers(List.of(new NameCountAmount("Customer A", 2, new BigDecimal("10000"))));
        analytics.setProductBreakdown(List.of());

        List<HourlyData> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            HourlyData hd = new HourlyData();
            hd.setHour(h);
            hd.setCount(h == 10 ? 5 : 0);
            hourly.add(hd);
        }
        analytics.setHourlyDistribution(hourly);

        when(dashboardService.getInvoiceAnalytics(isNull(), isNull())).thenReturn(analytics);

        mockMvc.perform(get("/api/dashboard/invoice-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoices").value(5))
                .andExpect(jsonPath("$.totalRevenue").value(25000))
                .andExpect(jsonPath("$.cashCount").value(5))
                .andExpect(jsonPath("$.cashAmount").value(25000))
                .andExpect(jsonPath("$.paidCount").value(5))
                .andExpect(jsonPath("$.paidAmount").value(25000))
                .andExpect(jsonPath("$.avgInvoiceValue").value(5000))
                .andExpect(jsonPath("$.paymentModeDistribution[0].name").value("Cash"))
                .andExpect(jsonPath("$.topCustomers[0].name").value("Customer A"))
                .andExpect(jsonPath("$.hourlyDistribution").isArray())
                .andExpect(jsonPath("$.hourlyDistribution.length()").value(24));
    }

    @Test
    void getInvoiceAnalytics_customDateRange() throws Exception {
        InvoiceAnalytics analytics = new InvoiceAnalytics();
        analytics.setFromDate("2026-01-01");
        analytics.setToDate("2026-01-07");
        analytics.setDailyTrend(List.of(
                createInvoiceDailyTrend("2026-01-01"), createInvoiceDailyTrend("2026-01-02"),
                createInvoiceDailyTrend("2026-01-03"), createInvoiceDailyTrend("2026-01-04"),
                createInvoiceDailyTrend("2026-01-05"), createInvoiceDailyTrend("2026-01-06"),
                createInvoiceDailyTrend("2026-01-07")));
        analytics.setPaymentModeDistribution(List.of());
        analytics.setTopCustomers(List.of());
        analytics.setProductBreakdown(List.of());
        analytics.setHourlyDistribution(List.of());
        when(dashboardService.getInvoiceAnalytics(any(), any())).thenReturn(analytics);

        mockMvc.perform(get("/api/dashboard/invoice-analytics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromDate").value("2026-01-01"))
                .andExpect(jsonPath("$.toDate").value("2026-01-07"))
                .andExpect(jsonPath("$.dailyTrend.length()").value(7))
                .andExpect(jsonPath("$.totalInvoices").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0));
    }

    // ===========================
    // /api/dashboard/payment-analytics
    // ===========================

    @Test
    void getPaymentAnalytics_defaultRange_returnsAnalytics() throws Exception {
        PaymentAnalytics analytics = new PaymentAnalytics();
        analytics.setTotalCollected(new BigDecimal("100000"));
        analytics.setTotalPayments(20);
        analytics.setAvgPaymentAmount(new BigDecimal("5000"));
        analytics.setTotalOutstanding(new BigDecimal("50000"));
        analytics.setCreditCustomers(10);
        analytics.setCollectionRate(new BigDecimal("66.7"));
        analytics.setAging0to30(new BigDecimal("20000"));
        analytics.setAging31to60(new BigDecimal("15000"));
        analytics.setAging61to90(new BigDecimal("10000"));
        analytics.setAging90Plus(new BigDecimal("5000"));
        analytics.setDailyTrend(List.of());
        analytics.setPaymentModeBreakdown(List.of(
                new NameCountAmount("CASH", 10, new BigDecimal("60000")),
                new NameCountAmount("UPI", 8, new BigDecimal("35000"))));
        analytics.setTopCustomers(List.of(new NameCountAmount("Big Corp", 5, new BigDecimal("40000"))));
        when(dashboardService.getPaymentAnalytics(isNull(), isNull())).thenReturn(analytics);

        mockMvc.perform(get("/api/dashboard/payment-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(100000))
                .andExpect(jsonPath("$.totalPayments").value(20))
                .andExpect(jsonPath("$.avgPaymentAmount").value(5000))
                .andExpect(jsonPath("$.totalOutstanding").value(50000))
                .andExpect(jsonPath("$.creditCustomers").value(10))
                .andExpect(jsonPath("$.collectionRate").value(66.7))
                .andExpect(jsonPath("$.aging0to30").value(20000))
                .andExpect(jsonPath("$.aging90Plus").value(5000))
                .andExpect(jsonPath("$.paymentModeBreakdown[0].name").value("CASH"))
                .andExpect(jsonPath("$.paymentModeBreakdown[1].name").value("UPI"))
                .andExpect(jsonPath("$.topCustomers[0].name").value("Big Corp"));
    }

    @Test
    void getPaymentAnalytics_customDateRange() throws Exception {
        PaymentAnalytics analytics = new PaymentAnalytics();
        analytics.setFromDate("2026-02-01");
        analytics.setToDate("2026-02-07");
        analytics.setDailyTrend(List.of(
                createPaymentDailyTrend("2026-02-01"), createPaymentDailyTrend("2026-02-02"),
                createPaymentDailyTrend("2026-02-03"), createPaymentDailyTrend("2026-02-04"),
                createPaymentDailyTrend("2026-02-05"), createPaymentDailyTrend("2026-02-06"),
                createPaymentDailyTrend("2026-02-07")));
        analytics.setPaymentModeBreakdown(List.of());
        analytics.setTopCustomers(List.of());
        when(dashboardService.getPaymentAnalytics(any(), any())).thenReturn(analytics);

        mockMvc.perform(get("/api/dashboard/payment-analytics")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromDate").value("2026-02-01"))
                .andExpect(jsonPath("$.toDate").value("2026-02-07"))
                .andExpect(jsonPath("$.dailyTrend.length()").value(7))
                .andExpect(jsonPath("$.totalCollected").value(0))
                .andExpect(jsonPath("$.totalPayments").value(0))
                .andExpect(jsonPath("$.collectionRate").value(0));
    }

    // --- Helpers ---

    private DailyRevenue createDailyRevenue(String date) {
        DailyRevenue dr = new DailyRevenue();
        dr.setDate(date);
        dr.setRevenue(BigDecimal.ZERO);
        dr.setFuelVolume(BigDecimal.ZERO);
        return dr;
    }

    private InvoiceDailyTrend createInvoiceDailyTrend(String date) {
        InvoiceDailyTrend t = new InvoiceDailyTrend();
        t.setDate(date);
        return t;
    }

    private PaymentDailyTrend createPaymentDailyTrend(String date) {
        PaymentDailyTrend t = new PaymentDailyTrend();
        t.setDate(date);
        return t;
    }
}
