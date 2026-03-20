package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.dto.ShiftReportPrintData.*;
import com.stopforfuel.backend.entity.ReportLineItem;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftReportPdfGeneratorTest {

    private final ShiftReportPdfGenerator generator = new ShiftReportPdfGenerator();

    @Test
    void generate_withFullData_returnsPdfBytes() {
        ShiftReportPrintData data = buildFullData();
        ShiftClosingReport report = buildReport();

        byte[] result = generator.generate(data, report);

        assertNotNull(result);
        assertTrue(result.length > 0);
        assertEquals('%', (char) result[0]);
        assertEquals('P', (char) result[1]);
        assertEquals('D', (char) result[2]);
        assertEquals('F', (char) result[3]);
    }

    @Test
    void generate_withEmptyData_returnsPdfBytes() {
        ShiftReportPrintData data = new ShiftReportPrintData();
        data.setCompanyName("Test Station");
        data.setEmployeeName("Test Employee");
        data.setShiftId(1L);
        data.setShiftStart(LocalDateTime.of(2026, 3, 20, 6, 0));
        data.setShiftEnd(LocalDateTime.of(2026, 3, 20, 14, 0));

        ShiftClosingReport report = new ShiftClosingReport();
        report.setLineItems(new ArrayList<>());

        byte[] result = generator.generate(data, report);

        assertNotNull(result);
        assertTrue(result.length > 0);
        assertEquals('%', (char) result[0]);
        assertEquals('P', (char) result[1]);
    }

    @Test
    void generate_withNullFields_doesNotThrow() {
        ShiftReportPrintData data = new ShiftReportPrintData();
        data.setCompanyName("Test Station");
        data.setEmployeeName("Test Employee");
        data.setShiftId(1L);
        data.setShiftStart(null);
        data.setShiftEnd(null);
        data.setReportStatus(null);

        ShiftClosingReport report = new ShiftClosingReport();
        report.setLineItems(new ArrayList<>());

        byte[] result = assertDoesNotThrow(() -> generator.generate(data, report));

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    private ShiftReportPrintData buildFullData() {
        ShiftReportPrintData data = new ShiftReportPrintData();
        data.setCompanyName("Test Fuel Station");
        data.setEmployeeName("John Doe");
        data.setShiftId(100L);
        data.setShiftStart(LocalDateTime.of(2026, 3, 20, 6, 0));
        data.setShiftEnd(LocalDateTime.of(2026, 3, 20, 14, 0));
        data.setReportStatus("FINALIZED");

        // Meter readings
        MeterReading mr = new MeterReading();
        mr.setPumpName("Pump 1");
        mr.setNozzleName("Nozzle 1");
        mr.setProductName("Diesel");
        mr.setOpenReading(10000.0);
        mr.setCloseReading(10500.0);
        mr.setSales(500.0);
        data.getMeterReadings().add(mr);

        // Tank readings
        TankReading tr = new TankReading();
        tr.setTankName("Tank 1");
        tr.setProductName("Diesel");
        tr.setOpenDip("150cm");
        tr.setOpenStock(5000.0);
        tr.setIncomeStock(0.0);
        tr.setTotalStock(5000.0);
        tr.setCloseDip("140cm");
        tr.setCloseStock(4500.0);
        tr.setSaleStock(500.0);
        data.getTankReadings().add(tr);

        // Sales differences
        SalesDifference sd = new SalesDifference();
        sd.setProductName("Diesel");
        sd.setTankSale(500.0);
        sd.setMeterSale(500.0);
        sd.setDifference(0.0);
        data.getSalesDifferences().add(sd);

        // Credit bill details
        CreditBillDetail cbd = new CreditBillDetail();
        cbd.setCustomerName("Test Customer");
        cbd.setBillNo("CR26/1");
        cbd.setVehicleNo("TN01AB1234");
        cbd.setProducts("Diesel: 100");
        cbd.setAmount(new BigDecimal("8950.00"));
        data.getCreditBillDetails().add(cbd);

        // Stock summary
        StockSummaryRow ssr = new StockSummaryRow();
        ssr.setProductName("Diesel");
        ssr.setOpenStock(5000.0);
        ssr.setReceipt(0.0);
        ssr.setTotalStock(5000.0);
        ssr.setSales(500.0);
        ssr.setRate(new BigDecimal("89.50"));
        ssr.setAmount(new BigDecimal("44750.00"));
        data.getStockSummary().add(ssr);

        // Stock position
        StockPositionRow spr = new StockPositionRow();
        spr.setProductName("Diesel");
        spr.setGodownStock(3000.0);
        spr.setCashierStock(1500.0);
        spr.setTotalStock(4500.0);
        spr.setLowStock(false);
        data.getStockPosition().add(spr);

        // Advance entries
        AdvanceEntryDetail aed = new AdvanceEntryDetail();
        aed.setType("CARD");
        aed.setDescription("Card payment collection");
        aed.setAmount(new BigDecimal("5000.00"));
        data.getAdvanceEntries().add(aed);

        // Payment entries
        PaymentEntryDetail ped = new PaymentEntryDetail();
        ped.setType("BILL");
        ped.setCustomerName("Test Customer");
        ped.setReference("CR26/1");
        ped.setPaymentMode("CASH");
        ped.setAmount(new BigDecimal("3000.00"));
        data.getPaymentEntries().add(ped);

        return data;
    }

    private ShiftClosingReport buildReport() {
        ShiftClosingReport report = new ShiftClosingReport();

        ReportLineItem item1 = new ReportLineItem();
        item1.setSection("REVENUE");
        item1.setLabel("Fuel Sales");
        item1.setAmount(new BigDecimal("44750.00"));
        item1.setSortOrder(1);
        item1.setReport(report);

        ReportLineItem item2 = new ReportLineItem();
        item2.setSection("ADVANCE");
        item2.setLabel("Card Collection");
        item2.setAmount(new BigDecimal("5000.00"));
        item2.setSortOrder(2);
        item2.setReport(report);

        report.setLineItems(new ArrayList<>(List.of(item1, item2)));
        return report;
    }
}
