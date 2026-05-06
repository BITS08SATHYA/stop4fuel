package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.NozzleInventoryRepository;
import com.stopforfuel.backend.repository.PurchaseInvoiceRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pulls the four datasets needed for the auditor's VAT/GST report and delegates
 * to the PDF / Excel generators. Read-only.
 */
@Service
@RequiredArgsConstructor
public class VatReportService {

    private final CompanyRepository companyRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final VatReportPdfGenerator pdfGenerator;
    private final VatReportExcelService excelService;

    @Transactional(readOnly = true)
    public byte[] generatePdf(LocalDate fromDate, LocalDate toDate) {
        return pdfGenerator.generate(buildData(fromDate, toDate));
    }

    @Transactional(readOnly = true)
    public byte[] generateExcel(LocalDate fromDate, LocalDate toDate) {
        return excelService.generate(buildData(fromDate, toDate));
    }

    private VatReportData buildData(LocalDate fromDate, LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        Company company = companyRepository.findByScid(scid).stream().findFirst().orElse(null);

        VatReportData d = new VatReportData();
        d.fromDate = fromDate;
        d.toDate = toDate;
        d.company = company;

        d.purchaseInvoices = purchaseInvoiceRepository.findByDateRangeWithDetails(scid, fromDate, toDate);

        // Daily fuel sales per product, from nozzle readings (canonical revenue source).
        // Group by (date, productId) → totals; rate = product.price (current catalog).
        List<NozzleInventory> nozzleRows = nozzleInventoryRepository.findByScidAndDateBetween(scid, fromDate, toDate);
        Map<Long, FuelProductDaily> byProduct = new LinkedHashMap<>();
        for (NozzleInventory ni : nozzleRows) {
            if (ni.getNozzle() == null || ni.getNozzle().getTank() == null
                    || ni.getNozzle().getTank().getProduct() == null) continue;
            Product p = ni.getNozzle().getTank().getProduct();
            // Only fuel products (skip non-FUEL even if a nozzle was misconfigured)
            if (!"FUEL".equalsIgnoreCase(p.getCategory())) continue;
            FuelProductDaily fpd = byProduct.computeIfAbsent(p.getId(), k -> {
                FuelProductDaily f = new FuelProductDaily();
                f.product = p;
                f.dailyTotals = new TreeMap<>();
                return f;
            });
            DailyFuelRow row = fpd.dailyTotals.computeIfAbsent(ni.getDate(), k -> new DailyFuelRow());
            if (ni.getSales() != null) row.litres = row.litres.add(BigDecimal.valueOf(ni.getSales()));
            if (ni.getTestQuantity() != null) row.test = row.test.add(BigDecimal.valueOf(ni.getTestQuantity()));
        }
        // Compute net & amount per row
        for (FuelProductDaily fpd : byProduct.values()) {
            BigDecimal rate = fpd.product.getPrice() != null ? fpd.product.getPrice() : BigDecimal.ZERO;
            for (DailyFuelRow row : fpd.dailyTotals.values()) {
                row.netSale = row.litres.subtract(row.test);
                row.rate = rate;
                row.amount = row.netSale.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }
        }
        d.fuelDailyByProduct = byProduct;

        // Daily lubricant sales — sum non-FUEL InvoiceProduct.amount grouped by InvoiceBill.date::date.
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        List<InvoiceBill> bills = invoiceBillRepository.findByDateBetween(from, to, scid);
        Map<LocalDate, BigDecimal> lubeByDay = new TreeMap<>();
        BigDecimal lubeTotal = BigDecimal.ZERO;
        for (InvoiceBill b : bills) {
            if (b.getProducts() == null || b.getDate() == null) continue;
            LocalDate day = b.getDate().toLocalDate();
            for (InvoiceProduct ip : b.getProducts()) {
                if (ip.getProduct() == null) continue;
                if ("FUEL".equalsIgnoreCase(ip.getProduct().getCategory())) continue;
                BigDecimal amt = ip.getAmount() != null ? ip.getAmount() : BigDecimal.ZERO;
                lubeByDay.merge(day, amt, BigDecimal::add);
                lubeTotal = lubeTotal.add(amt);
            }
        }
        d.lubricantDailySales = lubeByDay;
        d.lubricantTotal = lubeTotal.setScale(2, RoundingMode.HALF_UP);

        // GST math (lubricants are GST-inclusive at 18%).
        // tax_excluded = sales * 100 / 118; vat_total = sales - tax_excluded; SGST = CGST = vat_total / 2
        BigDecimal taxExcluded = lubeTotal.multiply(new BigDecimal("100"))
                .divide(new BigDecimal("118"), 2, RoundingMode.HALF_UP);
        BigDecimal vatTotal = lubeTotal.subtract(taxExcluded).setScale(2, RoundingMode.HALF_UP);
        BigDecimal half = vatTotal.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        d.taxIncludedSales = lubeTotal;
        d.taxExcludedSales = taxExcluded;
        d.netVat18 = vatTotal;
        d.sgst9 = half;
        d.cgst9 = vatTotal.subtract(half); // residual goes here so SGST + CGST = vatTotal exactly

        return d;
    }

    // ===================== DTOs =====================

    public static class VatReportData {
        public LocalDate fromDate;
        public LocalDate toDate;
        public Company company;
        public List<PurchaseInvoice> purchaseInvoices;
        public Map<Long, FuelProductDaily> fuelDailyByProduct;
        public Map<LocalDate, BigDecimal> lubricantDailySales;
        public BigDecimal lubricantTotal;
        public BigDecimal taxIncludedSales;
        public BigDecimal taxExcludedSales;
        public BigDecimal netVat18;
        public BigDecimal sgst9;
        public BigDecimal cgst9;
    }

    public static class FuelProductDaily {
        public Product product;
        public Map<LocalDate, DailyFuelRow> dailyTotals;
    }

    public static class DailyFuelRow {
        public BigDecimal litres = BigDecimal.ZERO;
        public BigDecimal test = BigDecimal.ZERO;
        public BigDecimal netSale = BigDecimal.ZERO;
        public BigDecimal rate = BigDecimal.ZERO;
        public BigDecimal amount = BigDecimal.ZERO;
    }
}
