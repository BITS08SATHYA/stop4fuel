package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.InvoiceProduct;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.enums.BillType;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the auditor's "Daily Sales Register" — five independently downloadable
 * segments (Diesel / Petrol / Xtra Premium / Lubricants / Purchase). Each fuel
 * segment is a per-day block: meter-derived total sale, the per-credit-customer
 * breakdown, and cash = total − credit. Read-only.
 *
 * Fuel volumes come from NozzleInventory (the canonical revenue source, same as
 * VatReportService). Per-day rate = the most-common InvoiceProduct.unitPrice
 * actually transacted for that product that day (the rate the cashier billed),
 * falling back to Product.price then 0.
 * TODO: ProductPriceHistory (effectiveDate, product, price) exists but is unused
 *       — wire it here for historically-exact per-day rates.
 */
@Service
@RequiredArgsConstructor
public class DailySalesRegisterService {

    public static final String DIESEL = "DIESEL";
    public static final String PETROL = "PETROL";
    public static final String XTRA_PREMIUM = "XTRA PREMIUM";

    private final CompanyRepository companyRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final DailySalesRegisterPdfGenerator pdfGenerator;
    private final DailySalesRegisterExcelService excelService;

    @Transactional(readOnly = true)
    public byte[] generateFuelPdf(String section, LocalDate from, LocalDate to) {
        return pdfGenerator.fuel(buildData(from, to), section);
    }

    @Transactional(readOnly = true)
    public byte[] generateFuelExcel(String section, LocalDate from, LocalDate to) {
        return excelService.fuel(buildData(from, to), section);
    }

    @Transactional(readOnly = true)
    public byte[] generateLubricantPdf(LocalDate from, LocalDate to) {
        return pdfGenerator.lubricant(buildData(from, to));
    }

    @Transactional(readOnly = true)
    public byte[] generateLubricantExcel(LocalDate from, LocalDate to) {
        return excelService.lubricant(buildData(from, to));
    }

    @Transactional(readOnly = true)
    public byte[] generatePurchasePdf(LocalDate from, LocalDate to) {
        return pdfGenerator.purchase(buildData(from, to));
    }

    @Transactional(readOnly = true)
    public byte[] generatePurchaseExcel(LocalDate from, LocalDate to) {
        return excelService.purchase(buildData(from, to));
    }

    // ===================== Data build =====================

    private DailySalesRegisterData buildData(LocalDate fromDate, LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        DailySalesRegisterData d = new DailySalesRegisterData();
        d.fromDate = fromDate;
        d.toDate = toDate;
        d.company = companyRepository.findByScid(scid).stream().findFirst().orElse(null);

        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        List<InvoiceBill> bills = invoiceBillRepository.findByDateBetween(from, to, scid);
        List<NozzleInventory> nozzleRows = nozzleInventoryRepository.findByScidAndDateBetween(scid, fromDate, toDate);

        // --- meter net litres per (section, date) ---
        Map<String, Map<LocalDate, BigDecimal>> meter = new LinkedHashMap<>();
        Map<String, Product> productBySection = new LinkedHashMap<>();
        for (NozzleInventory ni : nozzleRows) {
            if (ni.getNozzle() == null || ni.getNozzle().getTank() == null
                    || ni.getNozzle().getTank().getProduct() == null || ni.getDate() == null) continue;
            Product p = ni.getNozzle().getTank().getProduct();
            if (!"FUEL".equalsIgnoreCase(p.getCategory())) continue;
            String section = FuelClassifier.classify(p).section();
            if (section == null) continue;
            productBySection.putIfAbsent(section, p);
            double sales = ni.getSales() != null ? ni.getSales() : 0d;
            double test = ni.getTestQuantity() != null ? ni.getTestQuantity() : 0d;
            meter.computeIfAbsent(section, k -> new TreeMap<>())
                    .merge(ni.getDate(), BigDecimal.valueOf(sales - test), BigDecimal::add);
        }

        // --- credit fuel per (section, date, customer) + price frequency per (section, date) ---
        Map<String, Map<LocalDate, Map<String, BigDecimal[]>>> credit = new LinkedHashMap<>();
        Map<String, Map<LocalDate, Map<BigDecimal, Integer>>> priceFreq = new LinkedHashMap<>();
        for (InvoiceBill b : bills) {
            if (b.getProducts() == null || b.getDate() == null) continue;
            LocalDate day = b.getDate().toLocalDate();
            boolean isCredit = BillType.CREDIT.equals(b.getBillType());
            String custName = customerName(b);
            for (InvoiceProduct ip : b.getProducts()) {
                Product p = ip.getProduct();
                if (p == null || !"FUEL".equalsIgnoreCase(p.getCategory())) continue;
                String section = FuelClassifier.classify(p).section();
                if (section == null) continue;
                productBySection.putIfAbsent(section, p);
                BigDecimal qty = nz(ip.getQuantity());
                BigDecimal amt = nz(ip.getAmount());
                if (ip.getUnitPrice() != null && ip.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                    priceFreq.computeIfAbsent(section, k -> new LinkedHashMap<>())
                            .computeIfAbsent(day, k -> new LinkedHashMap<>())
                            .merge(ip.getUnitPrice(), 1, Integer::sum);
                }
                if (isCredit) {
                    BigDecimal[] cell = credit
                            .computeIfAbsent(section, k -> new LinkedHashMap<>())
                            .computeIfAbsent(day, k -> new LinkedHashMap<>())
                            .computeIfAbsent(custName, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    cell[0] = cell[0].add(qty);
                    cell[1] = cell[1].add(amt);
                }
            }
        }

        d.fuelSections = new LinkedHashMap<>();
        for (String section : List.of(DIESEL, PETROL, XTRA_PREMIUM)) {
            d.fuelSections.put(section, buildFuelSection(section,
                    meter.getOrDefault(section, Map.of()),
                    credit.getOrDefault(section, Map.of()),
                    priceFreq.getOrDefault(section, Map.of()),
                    productBySection.get(section)));
        }

        d.lubricant = buildLubricant(bills, fromDate, toDate);
        d.purchaseRows = buildPurchase(scid, fromDate, toDate);
        return d;
    }

    private FuelSection buildFuelSection(String section,
                                         Map<LocalDate, BigDecimal> meter,
                                         Map<LocalDate, Map<String, BigDecimal[]>> credit,
                                         Map<LocalDate, Map<BigDecimal, Integer>> priceFreq,
                                         Product fallbackProduct) {
        FuelSection fs = new FuelSection();
        fs.section = section;
        fs.productName = section;
        fs.days = new ArrayList<>();

        // Every date with either a meter reading or a credit sale gets a block.
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.addAll(meter.keySet());
        dates.addAll(credit.keySet());

        int sno = 1;
        for (LocalDate date : dates) {
            FuelDayBlock blk = new FuelDayBlock();
            blk.sno = sno++;
            blk.date = date;
            blk.productName = section;

            BigDecimal rate = modalRate(priceFreq.get(date), fallbackProduct);
            blk.productRate = rate;

            blk.creditRows = new ArrayList<>();
            BigDecimal creditLitres = BigDecimal.ZERO;
            BigDecimal creditAmt = BigDecimal.ZERO;
            Map<String, BigDecimal[]> custMap = credit.get(date);
            if (custMap != null) {
                for (Map.Entry<String, BigDecimal[]> e : custMap.entrySet()) {
                    CreditCustomerRow r = new CreditCustomerRow();
                    r.customerName = e.getKey();
                    r.creditLiters = e.getValue()[0];
                    r.creditLiterAmount = e.getValue()[1];
                    r.rate = rate;
                    r.productName = section;
                    blk.creditRows.add(r);
                    creditLitres = creditLitres.add(r.creditLiters);
                    creditAmt = creditAmt.add(r.creditLiterAmount);
                }
            }
            blk.creditLitersSubtotal = creditLitres;
            blk.creditAmountSubtotal = creditAmt;

            BigDecimal meterNet = meter.get(date);
            // No meter reading that day → fall back to the invoiced credit volume (cash = 0).
            blk.totalSalesLiters = meterNet != null ? meterNet : creditLitres;
            blk.totalSalesAmount = blk.totalSalesLiters.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            BigDecimal cashLitres = blk.totalSalesLiters.subtract(creditLitres);
            // Over-invoiced (credit qty exceeds net meter, e.g. test/shift-boundary) → clamp to 0.
            if (cashLitres.compareTo(BigDecimal.ZERO) < 0) cashLitres = BigDecimal.ZERO;
            blk.cashSalesLiters = cashLitres;
            blk.cashSalesAmount = cashLitres.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            fs.days.add(blk);
        }
        return fs;
    }

    private LubricantSection buildLubricant(List<InvoiceBill> bills, LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, BigDecimal> totalByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> creditByDay = new TreeMap<>();
        Map<LocalDate, List<CreditCustomerRow>> creditRowsByDay = new TreeMap<>();
        for (InvoiceBill b : bills) {
            if (b.getProducts() == null || b.getDate() == null) continue;
            LocalDate day = b.getDate().toLocalDate();
            boolean isCredit = BillType.CREDIT.equals(b.getBillType());
            String custName = customerName(b);
            for (InvoiceProduct ip : b.getProducts()) {
                Product p = ip.getProduct();
                if (p == null || "FUEL".equalsIgnoreCase(p.getCategory())) continue;
                BigDecimal amt = nz(ip.getAmount());
                totalByDay.merge(day, amt, BigDecimal::add);
                if (isCredit) {
                    CreditCustomerRow r = new CreditCustomerRow();
                    r.customerName = custName;
                    r.creditLiters = nz(ip.getQuantity());
                    r.rate = nz(ip.getUnitPrice());
                    r.creditLiterAmount = amt;
                    r.productName = p.getName() != null ? p.getName() : "";
                    creditRowsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(r);
                    creditByDay.merge(day, amt, BigDecimal::add);
                }
            }
        }

        LubricantSection ls = new LubricantSection();
        ls.days = new ArrayList<>();
        int sno = 1;
        // A row for every day in the period — auditors expect the calendar to be complete.
        for (LocalDate date : (Iterable<LocalDate>) fromDate.datesUntil(toDate.plusDays(1))::iterator) {
            LubeDayBlock blk = new LubeDayBlock();
            blk.sno = sno++;
            blk.date = date;
            blk.totalSalesAmount = totalByDay.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            blk.creditRows = creditRowsByDay.getOrDefault(date, new ArrayList<>());
            BigDecimal creditAmt = creditByDay.getOrDefault(date, BigDecimal.ZERO);
            blk.creditAmountSubtotal = creditAmt.setScale(2, RoundingMode.HALF_UP);
            BigDecimal cash = blk.totalSalesAmount.subtract(creditAmt);
            if (cash.compareTo(BigDecimal.ZERO) < 0) cash = BigDecimal.ZERO;
            blk.cashSalesAmount = cash.setScale(2, RoundingMode.HALF_UP);
            ls.days.add(blk);
        }
        return ls;
    }

    private List<PurchaseRow> buildPurchase(Long scid, LocalDate fromDate, LocalDate toDate) {
        List<PurchaseInvoice> invoices =
                purchaseInvoiceRepository.findByDateRangeWithDetails(scid, fromDate, toDate);
        List<PurchaseRow> rows = new ArrayList<>();
        for (PurchaseInvoice pi : invoices) {
            if (pi.getItems() == null) continue;
            String gstin = pi.getSupplier() != null ? pi.getSupplier().getGstNumber() : null;
            String party = pi.getSupplier() != null ? pi.getSupplier().getName() : "";
            for (PurchaseInvoiceItem it : pi.getItems()) {
                Product p = it.getProduct();
                PurchaseRow r = new PurchaseRow();
                r.invoiceDate = pi.getInvoiceDate();
                r.invoiceNo = InvoiceCellFormatter.formatInvoiceCell(pi);
                r.ptyName = party != null ? party : "";
                r.vchType = "Auto Purchase";
                r.gstin = gstin != null ? gstin : "";
                r.stateOfSupply = GstStateCodes.stateName(gstin);
                r.productName = p != null && p.getName() != null ? p.getName() : "";
                r.hsnCode = p != null && p.getHsnCode() != null ? p.getHsnCode() : "";
                r.uom = p != null && p.getUnit() != null ? p.getUnit() : "";
                r.qty = it.getQuantity() != null ? BigDecimal.valueOf(it.getQuantity()) : BigDecimal.ZERO;
                r.taxPer = nz(it.getTaxPercent());
                r.taxable = it.getBasicAmount() != null
                        ? it.getBasicAmount()
                        : nz(it.getBasicPrice()).multiply(r.qty).setScale(2, RoundingMode.HALF_UP);
                // Intra-state assumption: split GST in half (SGST + CGST).
                r.sgstPer = r.taxPer.divide(new BigDecimal("2"), 3, RoundingMode.HALF_UP);
                r.cgstPer = r.sgstPer;
                BigDecimal taxAmt = nz(it.getTaxAmount());
                r.sgstAmt = taxAmt.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                r.cgstAmt = taxAmt.subtract(r.sgstAmt);
                r.total = nz(it.getBasicAmount()).add(taxAmt).add(nz(it.getAdditionalTaxAmount()));
                rows.add(r);
            }
        }
        // Repo returns invoiceDate DESC; the register reads oldest-first.
        rows.sort((a, b) -> {
            int c = compareNullableDate(a.invoiceDate, b.invoiceDate);
            return c != 0 ? c : safe(a.invoiceNo).compareTo(safe(b.invoiceNo));
        });
        return rows;
    }

    // ===================== Helpers =====================

    private static BigDecimal modalRate(Map<BigDecimal, Integer> freq, Product fallback) {
        if (freq != null && !freq.isEmpty()) {
            BigDecimal best = null;
            int bestCount = -1;
            for (Map.Entry<BigDecimal, Integer> e : freq.entrySet()) { // LinkedHashMap → first-seen wins ties
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    best = e.getKey();
                }
            }
            if (best != null) return best;
        }
        if (fallback != null && fallback.getPrice() != null) return fallback.getPrice();
        return BigDecimal.ZERO;
    }

    private static String customerName(InvoiceBill b) {
        if (b.getCustomer() != null && b.getCustomer().getName() != null) return b.getCustomer().getName();
        if (b.getSignatoryName() != null && !b.getSignatoryName().isBlank()) return b.getSignatoryName();
        return "-";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static int compareNullableDate(LocalDate a, LocalDate b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    // ===================== DTOs =====================

    public static class DailySalesRegisterData {
        public LocalDate fromDate;
        public LocalDate toDate;
        public Company company;
        public Map<String, FuelSection> fuelSections;
        public LubricantSection lubricant;
        public List<PurchaseRow> purchaseRows;
    }

    public static class FuelSection {
        public String section;
        public String productName;
        public List<FuelDayBlock> days;
    }

    public static class FuelDayBlock {
        public int sno;
        public LocalDate date;
        public BigDecimal totalSalesLiters = BigDecimal.ZERO;
        public BigDecimal productRate = BigDecimal.ZERO;
        public BigDecimal totalSalesAmount = BigDecimal.ZERO;
        public String productName;
        public BigDecimal cashSalesLiters = BigDecimal.ZERO;
        public BigDecimal cashSalesAmount = BigDecimal.ZERO;
        public List<CreditCustomerRow> creditRows = new ArrayList<>();
        public BigDecimal creditLitersSubtotal = BigDecimal.ZERO;
        public BigDecimal creditAmountSubtotal = BigDecimal.ZERO;
    }

    public static class CreditCustomerRow {
        public String customerName;
        public BigDecimal creditLiters = BigDecimal.ZERO;
        public BigDecimal rate = BigDecimal.ZERO;
        public BigDecimal creditLiterAmount = BigDecimal.ZERO;
        public String productName;
    }

    public static class LubricantSection {
        public List<LubeDayBlock> days;
    }

    public static class LubeDayBlock {
        public int sno;
        public LocalDate date;
        public BigDecimal totalSalesAmount = BigDecimal.ZERO;
        public List<CreditCustomerRow> creditRows = new ArrayList<>();
        public BigDecimal creditAmountSubtotal = BigDecimal.ZERO;
        public BigDecimal cashSalesAmount = BigDecimal.ZERO;
    }

    public static class PurchaseRow {
        public LocalDate invoiceDate;
        public String invoiceNo;
        public String ptyName;
        public String vchType;
        public String gstin;
        public String stateOfSupply;
        public String productName;
        public String hsnCode;
        public BigDecimal qty = BigDecimal.ZERO;
        public String uom;
        public BigDecimal taxPer = BigDecimal.ZERO;
        public BigDecimal taxable = BigDecimal.ZERO;
        public BigDecimal sgstPer = BigDecimal.ZERO;
        public BigDecimal sgstAmt = BigDecimal.ZERO;
        public BigDecimal cgstPer = BigDecimal.ZERO;
        public BigDecimal cgstAmt = BigDecimal.ZERO;
        public BigDecimal total = BigDecimal.ZERO;
    }
}
