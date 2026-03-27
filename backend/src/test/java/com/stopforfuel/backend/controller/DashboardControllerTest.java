package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.backend.service.CreditManagementService;
import com.stopforfuel.backend.service.EAdvanceService;
import com.stopforfuel.backend.service.ExpenseService;
import com.stopforfuel.backend.service.ShiftService;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private InvoiceBillRepository invoiceBillRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private TankRepository tankRepository;

    @MockBean
    private PumpRepository pumpRepository;

    @MockBean
    private NozzleRepository nozzleRepository;

    @MockBean
    private TankInventoryRepository tankInventoryRepository;

    @MockBean
    private ShiftService shiftService;

    @MockBean
    private EAdvanceService eAdvanceService;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private CreditManagementService creditManagementService;

    private InvoiceBill testInvoice;
    private Tank testTank;

    @BeforeEach
    void setUp() {
        testInvoice = new InvoiceBill();
        testInvoice.setId(1L);
        testInvoice.setBillType("CASH");
        testInvoice.setNetAmount(new BigDecimal("5000"));
        testInvoice.setDate(LocalDateTime.now());
        testInvoice.setPaymentStatus("PAID");

        testTank = new Tank();
        testTank.setId(1L);
        testTank.setName("Tank 1");
        testTank.setCapacity(10000.0);
        testTank.setActive(true);
    }

    // ===========================
    // /api/dashboard/stats
    // ===========================

    @Test
    void getStats_returnsAllFields() throws Exception {
        when(invoiceBillRepository.findAll()).thenReturn(List.of(testInvoice));
        when(tankRepository.count()).thenReturn(2L);
        when(tankRepository.findByActive(true)).thenReturn(List.of(testTank));
        when(pumpRepository.count()).thenReturn(4L);
        when(pumpRepository.findByActive(true)).thenReturn(List.of());
        when(nozzleRepository.count()).thenReturn(8L);
        when(nozzleRepository.findByActive(true)).thenReturn(List.of());
        when(shiftService.getActiveShift()).thenReturn(null);
        when(tankRepository.findAll()).thenReturn(List.of(testTank));
        when(tankInventoryRepository.findTopByTankIdOrderByDateDescIdDesc(1L)).thenReturn(null);

        CreditManagementService.CreditOverview overview = new CreditManagementService.CreditOverview();
        overview.setTotalOutstanding(new BigDecimal("25000"));
        overview.setTotalCreditCustomers(5);
        overview.setTotalAging0to30(new BigDecimal("10000"));
        overview.setTotalAging31to60(new BigDecimal("8000"));
        overview.setTotalAging61to90(new BigDecimal("5000"));
        overview.setTotalAging90Plus(new BigDecimal("2000"));
        when(creditManagementService.getCreditOverview()).thenReturn(overview);

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
        Shift activeShift = new Shift();
        activeShift.setId(10L);
        activeShift.setStartTime(LocalDateTime.now());
        when(shiftService.getActiveShift()).thenReturn(activeShift);

        when(invoiceBillRepository.sumCashBillsByShift(10L)).thenReturn(new BigDecimal("3000"));
        Map<String, BigDecimal> eAdvSummary = Map.of(
            "card", new BigDecimal("1000"),
            "upi", new BigDecimal("2000"),
            "cheque", BigDecimal.ZERO,
            "ccms", BigDecimal.ZERO,
            "bank_transfer", BigDecimal.ZERO,
            "total", new BigDecimal("3000")
        );
        when(eAdvanceService.getShiftSummary(10L)).thenReturn(eAdvSummary);
        when(expenseService.sumByShift(10L)).thenReturn(new BigDecimal("500"));

        when(invoiceBillRepository.findAll()).thenReturn(List.of());
        when(tankRepository.count()).thenReturn(0L);
        when(tankRepository.findByActive(true)).thenReturn(List.of());
        when(pumpRepository.count()).thenReturn(0L);
        when(pumpRepository.findByActive(true)).thenReturn(List.of());
        when(nozzleRepository.count()).thenReturn(0L);
        when(nozzleRepository.findByActive(true)).thenReturn(List.of());
        when(tankRepository.findAll()).thenReturn(List.of());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("error"));

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
        when(invoiceBillRepository.findAll()).thenReturn(List.of());
        when(tankRepository.count()).thenReturn(0L);
        when(tankRepository.findByActive(true)).thenReturn(List.of());
        when(pumpRepository.count()).thenReturn(0L);
        when(pumpRepository.findByActive(true)).thenReturn(List.of());
        when(nozzleRepository.count()).thenReturn(0L);
        when(nozzleRepository.findByActive(true)).thenReturn(List.of());
        when(shiftService.getActiveShift()).thenReturn(null);
        when(tankRepository.findAll()).thenReturn(List.of());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("error"));

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayRevenue").value(0))
                .andExpect(jsonPath("$.todayFuelVolume").value(0))
                .andExpect(jsonPath("$.todayInvoiceCount").value(0))
                .andExpect(jsonPath("$.activeShiftId").isEmpty())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.recentInvoices").isEmpty());
    }

    @Test
    void getStats_creditOverviewThrows_returnsZeros() throws Exception {
        when(invoiceBillRepository.findAll()).thenReturn(List.of());
        when(tankRepository.count()).thenReturn(0L);
        when(tankRepository.findByActive(true)).thenReturn(List.of());
        when(pumpRepository.count()).thenReturn(0L);
        when(pumpRepository.findByActive(true)).thenReturn(List.of());
        when(nozzleRepository.count()).thenReturn(0L);
        when(nozzleRepository.findByActive(true)).thenReturn(List.of());
        when(shiftService.getActiveShift()).thenReturn(null);
        when(tankRepository.findAll()).thenReturn(List.of());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("DB down"));

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.creditAging0to30").value(0))
                .andExpect(jsonPath("$.creditAging31to60").value(0))
                .andExpect(jsonPath("$.creditAging61to90").value(0))
                .andExpect(jsonPath("$.creditAging90Plus").value(0));
    }

    @Test
    void getStats_cashAndCreditInvoices_countsSeparately() throws Exception {
        InvoiceBill creditInvoice = new InvoiceBill();
        creditInvoice.setId(2L);
        creditInvoice.setBillType("CREDIT");
        creditInvoice.setNetAmount(new BigDecimal("3000"));
        creditInvoice.setDate(LocalDateTime.now());
        creditInvoice.setPaymentStatus("UNPAID");

        when(invoiceBillRepository.findAll()).thenReturn(List.of(testInvoice, creditInvoice));
        when(tankRepository.count()).thenReturn(0L);
        when(tankRepository.findByActive(true)).thenReturn(List.of());
        when(pumpRepository.count()).thenReturn(0L);
        when(pumpRepository.findByActive(true)).thenReturn(List.of());
        when(nozzleRepository.count()).thenReturn(0L);
        when(nozzleRepository.findByActive(true)).thenReturn(List.of());
        when(shiftService.getActiveShift()).thenReturn(null);
        when(tankRepository.findAll()).thenReturn(List.of());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("error"));

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayRevenue").value(8000))
                .andExpect(jsonPath("$.todayInvoiceCount").value(2))
                .andExpect(jsonPath("$.todayCashInvoices").value(1))
                .andExpect(jsonPath("$.todayCreditInvoices").value(1));
    }

    // ===========================
    // /api/dashboard/invoice-analytics
    // ===========================

    @Test
    void getInvoiceAnalytics_defaultRange_returnsAnalytics() throws Exception {
        List<Object[]> summaryRows = new ArrayList<>();
        summaryRows.add(new Object[]{"CASH", "PAID", 5L, new BigDecimal("25000")});
        when(invoiceBillRepository.getInvoiceSummary(any(), any())).thenReturn(summaryRows);
        when(invoiceBillRepository.getDailyInvoiceStats(any(), any())).thenReturn(new ArrayList<>());
        List<Object[]> modeRows = new ArrayList<>();
        modeRows.add(new Object[]{"Cash", 3L, new BigDecimal("15000")});
        when(invoiceBillRepository.getPaymentModeDistribution(any(), any())).thenReturn(modeRows);
        List<Object[]> custRows = new ArrayList<>();
        custRows.add(new Object[]{"Customer A", 2L, new BigDecimal("10000")});
        when(invoiceBillRepository.getTopCustomersByRevenue(any(), any())).thenReturn(custRows);
        when(invoiceBillRepository.getProductSalesSummary(isNull(), isNull(), any(), any())).thenReturn(new ArrayList<>());
        List<Object[]> hourlyRows = new ArrayList<>();
        hourlyRows.add(new Object[]{10, 5L});
        when(invoiceBillRepository.getHourlyDistribution(any(), any())).thenReturn(hourlyRows);

        mockMvc.perform(get("/api/dashboard/invoice-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoices").value(5))
                .andExpect(jsonPath("$.totalRevenue").value(25000))
                .andExpect(jsonPath("$.cashCount").value(5))
                .andExpect(jsonPath("$.cashAmount").value(25000))
                .andExpect(jsonPath("$.paidCount").value(5))
                .andExpect(jsonPath("$.paidAmount").value(25000))
                .andExpect(jsonPath("$.avgInvoiceValue").value(5000))
                .andExpect(jsonPath("$.dailyTrend").isArray())
                .andExpect(jsonPath("$.paymentModeDistribution[0].name").value("Cash"))
                .andExpect(jsonPath("$.topCustomers[0].name").value("Customer A"))
                .andExpect(jsonPath("$.hourlyDistribution").isArray())
                .andExpect(jsonPath("$.hourlyDistribution.length()").value(24));
    }

    @Test
    void getInvoiceAnalytics_customDateRange() throws Exception {
        when(invoiceBillRepository.getInvoiceSummary(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getDailyInvoiceStats(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getPaymentModeDistribution(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getTopCustomersByRevenue(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getProductSalesSummary(isNull(), isNull(), any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getHourlyDistribution(any(), any())).thenReturn(new ArrayList<>());

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

    @Test
    void getInvoiceAnalytics_cashAndCredit_splitCorrectly() throws Exception {
        List<Object[]> splitSummary = new ArrayList<>();
        splitSummary.add(new Object[]{"CASH", "PAID", 10L, new BigDecimal("50000")});
        splitSummary.add(new Object[]{"CREDIT", "UNPAID", 5L, new BigDecimal("30000")});
        when(invoiceBillRepository.getInvoiceSummary(any(), any())).thenReturn(splitSummary);
        when(invoiceBillRepository.getDailyInvoiceStats(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getPaymentModeDistribution(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getTopCustomersByRevenue(any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getProductSalesSummary(isNull(), isNull(), any(), any())).thenReturn(new ArrayList<>());
        when(invoiceBillRepository.getHourlyDistribution(any(), any())).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/dashboard/invoice-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoices").value(15))
                .andExpect(jsonPath("$.totalRevenue").value(80000))
                .andExpect(jsonPath("$.cashCount").value(10))
                .andExpect(jsonPath("$.cashAmount").value(50000))
                .andExpect(jsonPath("$.creditCount").value(5))
                .andExpect(jsonPath("$.creditAmount").value(30000))
                .andExpect(jsonPath("$.paidCount").value(10))
                .andExpect(jsonPath("$.unpaidCount").value(5));
    }

    // ===========================
    // /api/dashboard/payment-analytics
    // ===========================

    @Test
    void getPaymentAnalytics_defaultRange_returnsAnalytics() throws Exception {
        when(paymentRepository.sumPaymentsInDateRange(any(), any())).thenReturn(new BigDecimal("100000"));
        when(paymentRepository.countPaymentsInDateRange(any(), any())).thenReturn(20L);
        when(paymentRepository.getDailyPaymentStats(any(), any())).thenReturn(new ArrayList<>());
        List<Object[]> payModes = new ArrayList<>();
        payModes.add(new Object[]{"CASH", 10L, new BigDecimal("60000")});
        payModes.add(new Object[]{"UPI", 8L, new BigDecimal("35000")});
        when(paymentRepository.getPaymentModeBreakdown(any(), any())).thenReturn(payModes);
        List<Object[]> topPaying = new ArrayList<>();
        topPaying.add(new Object[]{"Big Corp", 5L, new BigDecimal("40000")});
        when(paymentRepository.getTopPayingCustomers(any(), any())).thenReturn(topPaying);

        CreditManagementService.CreditOverview overview = new CreditManagementService.CreditOverview();
        overview.setTotalOutstanding(new BigDecimal("50000"));
        overview.setTotalCreditCustomers(10);
        overview.setTotalAging0to30(new BigDecimal("20000"));
        overview.setTotalAging31to60(new BigDecimal("15000"));
        overview.setTotalAging61to90(new BigDecimal("10000"));
        overview.setTotalAging90Plus(new BigDecimal("5000"));
        when(creditManagementService.getCreditOverview()).thenReturn(overview);

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
                .andExpect(jsonPath("$.dailyTrend").isArray())
                .andExpect(jsonPath("$.paymentModeBreakdown[0].name").value("CASH"))
                .andExpect(jsonPath("$.paymentModeBreakdown[1].name").value("UPI"))
                .andExpect(jsonPath("$.topCustomers[0].name").value("Big Corp"));
    }

    @Test
    void getPaymentAnalytics_customDateRange() throws Exception {
        when(paymentRepository.sumPaymentsInDateRange(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countPaymentsInDateRange(any(), any())).thenReturn(0L);
        when(paymentRepository.getDailyPaymentStats(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getPaymentModeBreakdown(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getTopPayingCustomers(any(), any())).thenReturn(new ArrayList<>());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("error"));

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

    @Test
    void getPaymentAnalytics_noPayments_zeroCollectionRate() throws Exception {
        when(paymentRepository.sumPaymentsInDateRange(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countPaymentsInDateRange(any(), any())).thenReturn(0L);
        when(paymentRepository.getDailyPaymentStats(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getPaymentModeBreakdown(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getTopPayingCustomers(any(), any())).thenReturn(new ArrayList<>());

        CreditManagementService.CreditOverview overview = new CreditManagementService.CreditOverview();
        overview.setTotalOutstanding(BigDecimal.ZERO);
        overview.setTotalCreditCustomers(0);
        overview.setTotalAging0to30(BigDecimal.ZERO);
        overview.setTotalAging31to60(BigDecimal.ZERO);
        overview.setTotalAging61to90(BigDecimal.ZERO);
        overview.setTotalAging90Plus(BigDecimal.ZERO);
        when(creditManagementService.getCreditOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/dashboard/payment-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(0))
                .andExpect(jsonPath("$.avgPaymentAmount").value(0))
                .andExpect(jsonPath("$.collectionRate").value(0));
    }

    @Test
    void getPaymentAnalytics_creditOverviewThrows_returnsZeros() throws Exception {
        when(paymentRepository.sumPaymentsInDateRange(any(), any())).thenReturn(new BigDecimal("10000"));
        when(paymentRepository.countPaymentsInDateRange(any(), any())).thenReturn(5L);
        when(paymentRepository.getDailyPaymentStats(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getPaymentModeBreakdown(any(), any())).thenReturn(new ArrayList<>());
        when(paymentRepository.getTopPayingCustomers(any(), any())).thenReturn(new ArrayList<>());
        when(creditManagementService.getCreditOverview()).thenThrow(new RuntimeException("error"));

        mockMvc.perform(get("/api/dashboard/payment-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.aging0to30").value(0))
                .andExpect(jsonPath("$.aging90Plus").value(0));
    }
}
