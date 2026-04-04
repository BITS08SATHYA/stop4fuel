package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ShiftReportPrintData;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all sales-related calculations for shift closing reports:
 * nozzle meter readings, product-wise sales, cash/credit bill breakdowns,
 * gross/net sales, and sales-related print data.
 */
@Service
@RequiredArgsConstructor
public class ShiftSalesCalculationService {

    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;
    private final ExternalCashInflowRepository inflowRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ProductRepository productRepository;
    private final GodownStockRepository godownStockRepository;
    private final CashierStockRepository cashierStockRepository;

    /**
     * Result holder for sales line-item computation.
     */
    public static class SalesResult {
        private final List<ReportLineItem> lineItems;
        private final BigDecimal cashBillTotal;
        private final BigDecimal creditBillTotal;
        private final List<InvoiceBill> allInvoices;
        private final double testLitres;
        private final BigDecimal testAmount;

        public SalesResult(List<ReportLineItem> lineItems, BigDecimal cashBillTotal,
                           BigDecimal creditBillTotal, List<InvoiceBill> allInvoices,
                           double testLitres, BigDecimal testAmount) {
            this.lineItems = lineItems;
            this.cashBillTotal = cashBillTotal;
            this.creditBillTotal = creditBillTotal;
            this.allInvoices = allInvoices;
            this.testLitres = testLitres;
            this.testAmount = testAmount;
        }

        public List<ReportLineItem> getLineItems() { return lineItems; }
        public BigDecimal getCashBillTotal() { return cashBillTotal; }
        public BigDecimal getCreditBillTotal() { return creditBillTotal; }
        public List<InvoiceBill> getAllInvoices() { return allInvoices; }
        public double getTestLitres() { return testLitres; }
        public BigDecimal getTestAmount() { return testAmount; }
    }

    /**
     * Compute all REVENUE-section line items for the given shift.
     * Returns a SalesResult containing line items, cash/credit totals, and raw invoices.
     */
    @Transactional(readOnly = true)
    public SalesResult computeSalesLineItems(ShiftClosingReport report, Long shiftId, int startSortOrder) {
        List<ReportLineItem> lineItems = new ArrayList<>();
        int sortOrder = startSortOrder;

        // 1a. Cash/Credit bill totals and oil sales from invoices
        List<InvoiceBill> allInvoices = invoiceBillRepository.findByShiftId(shiftId);
        BigDecimal cashBillTotal = BigDecimal.ZERO;
        BigDecimal creditBillTotal = BigDecimal.ZERO;

        Map<String, double[]> oilSales = new LinkedHashMap<>();

        for (InvoiceBill inv : allInvoices) {
            if (com.stopforfuel.backend.enums.BillType.CASH.equals(inv.getBillType())) {
                cashBillTotal = cashBillTotal.add(inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO);
            } else if (com.stopforfuel.backend.enums.BillType.CREDIT.equals(inv.getBillType())) {
                creditBillTotal = creditBillTotal.add(inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO);
            }

            if (inv.getProducts() != null) {
                for (InvoiceProduct ip : inv.getProducts()) {
                    String category = ip.getProduct() != null ? ip.getProduct().getCategory() : "FUEL";
                    if (!"FUEL".equalsIgnoreCase(category)) {
                        String productName = ip.getProduct() != null ? ip.getProduct().getName() : "Unknown";
                        double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                        double amt = ip.getAmount() != null ? ip.getAmount().doubleValue() : 0;
                        double rate = ip.getUnitPrice() != null ? ip.getUnitPrice().doubleValue() : 0;
                        oilSales.merge(productName, new double[]{qty, amt, rate},
                                (old, nw) -> new double[]{old[0] + nw[0], old[1] + nw[1], nw[2]});
                    }
                }
            }
        }

        // 1b. Fuel Sales: compute from nozzle meter readings, rate from Product Catalog
        Map<String, double[]> fuelSales = new LinkedHashMap<>(); // productName -> [netLitres, amount, rate]
        double totalTestLitres = 0;
        double totalTestAmount = 0;
        List<NozzleInventory> nozzleInvs = nozzleInventoryRepository.findByShiftId(shiftId);
        for (NozzleInventory ni : nozzleInvs) {
            if (ni.getNozzle() == null || ni.getNozzle().getTank() == null
                    || ni.getNozzle().getTank().getProduct() == null) continue;
            Product product = ni.getNozzle().getTank().getProduct();
            String productName = product.getName();
            double sales = ni.getSales() != null ? ni.getSales() : 0;
            double test = ni.getTestQuantity() != null ? ni.getTestQuantity() : 0;
            double rate = product.getPrice() != null ? product.getPrice().doubleValue() : 0;
            double netLitres = sales - test;
            double amount = netLitres * rate;
            fuelSales.merge(productName, new double[]{netLitres, amount, rate},
                    (old, nw) -> new double[]{old[0] + nw[0], old[1] + nw[1], nw[2]});
            totalTestLitres += test;
            totalTestAmount += test * rate;
        }

        // Add fuel product lines
        for (Map.Entry<String, double[]> entry : fuelSales.entrySet()) {
            double[] vals = entry.getValue();
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("FUEL_SALES");
            item.setLabel(entry.getKey());
            item.setQuantity(vals[0]);
            item.setRate(BigDecimal.valueOf(vals[2]).setScale(4, RoundingMode.HALF_UP));
            item.setAmount(BigDecimal.valueOf(vals[1]).setScale(4, RoundingMode.HALF_UP));
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // Add test quantity in Revenue (fuel was dispensed through nozzle)
        if (totalTestLitres > 0) {
            ReportLineItem testItem = new ReportLineItem();
            testItem.setReport(report);
            testItem.setSection("REVENUE");
            testItem.setCategory("TEST_QUANTITY");
            testItem.setLabel("Test");
            testItem.setQuantity(totalTestLitres);
            testItem.setAmount(BigDecimal.valueOf(totalTestAmount).setScale(4, RoundingMode.HALF_UP));
            testItem.setSortOrder(++sortOrder);
            lineItems.add(testItem);
        }
        // Test data also passed via SalesResult to be added in ADVANCE section

        // Add oil/lubricant sales lines
        for (Map.Entry<String, double[]> entry : oilSales.entrySet()) {
            double[] vals = entry.getValue();
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("OIL_SALES");
            item.setLabel(entry.getKey());
            item.setQuantity(vals[0]);
            item.setRate(BigDecimal.valueOf(vals[2]).setScale(4, RoundingMode.HALF_UP));
            item.setAmount(BigDecimal.valueOf(vals[1]).setScale(4, RoundingMode.HALF_UP));
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 2. Bill Payments (payments against individual invoices in this shift)
        List<Payment> shiftPayments = paymentRepository.findByShiftIdEager(shiftId);
        BigDecimal billPaymentTotal = BigDecimal.ZERO;
        BigDecimal statementPaymentTotal = BigDecimal.ZERO;

        for (Payment p : shiftPayments) {
            if (p.getInvoiceBill() != null) {
                billPaymentTotal = billPaymentTotal.add(p.getAmount());
            } else if (p.getStatement() != null) {
                statementPaymentTotal = statementPaymentTotal.add(p.getAmount());
            }
        }

        if (billPaymentTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("BILL_PAYMENT");
            item.setLabel("Bill Payments");
            item.setAmount(billPaymentTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 3. Statement Payments
        if (statementPaymentTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("STATEMENT_PAYMENT");
            item.setLabel("Statement Payments");
            item.setAmount(statementPaymentTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        // 4. External Cash Inflows
        List<ExternalCashInflow> inflows = inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId);
        BigDecimal inflowTotal = BigDecimal.ZERO;
        for (ExternalCashInflow inflow : inflows) {
            inflowTotal = inflowTotal.add(inflow.getAmount());
        }
        if (inflowTotal.compareTo(BigDecimal.ZERO) > 0) {
            ReportLineItem item = new ReportLineItem();
            item.setReport(report);
            item.setSection("REVENUE");
            item.setCategory("EXTERNAL_INFLOW");
            item.setLabel("External Cash Inflow");
            item.setAmount(inflowTotal);
            item.setSortOrder(++sortOrder);
            lineItems.add(item);
        }

        return new SalesResult(lineItems, cashBillTotal, creditBillTotal, allInvoices,
                totalTestLitres, BigDecimal.valueOf(totalTestAmount).setScale(4, RoundingMode.HALF_UP));
    }

    /**
     * Compute cash bill breakdown by product and payment mode.
     */
    public List<ReportCashBillBreakdown> computeCashBillBreakdown(ShiftClosingReport report,
                                                                   List<InvoiceBill> allInvoices) {
        Map<String, ReportCashBillBreakdown> productBreakdowns = new LinkedHashMap<>();

        for (InvoiceBill inv : allInvoices) {
            if (!com.stopforfuel.backend.enums.BillType.CASH.equals(inv.getBillType())) continue;

            String paymentMode = inv.getPaymentMode() != null ? inv.getPaymentMode().name() : "CASH";

            if (inv.getProducts() != null) {
                for (InvoiceProduct ip : inv.getProducts()) {
                    String productName = ip.getProduct() != null ? ip.getProduct().getName() : "Unknown";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;

                    ReportCashBillBreakdown bd = productBreakdowns.computeIfAbsent(productName, k -> {
                        ReportCashBillBreakdown b = new ReportCashBillBreakdown();
                        b.setReport(report);
                        b.setProductName(k);
                        return b;
                    });

                    switch (paymentMode) {
                        case "CARD":
                            bd.setCardLitres(bd.getCardLitres() + qty);
                            break;
                        case "CCMS":
                            bd.setCcmsLitres(bd.getCcmsLitres() + qty);
                            break;
                        case "UPI":
                            bd.setUpiLitres(bd.getUpiLitres() + qty);
                            break;
                        case "CHEQUE":
                            bd.setChequeLitres(bd.getChequeLitres() + qty);
                            break;
                        default:
                            bd.setCashLitres(bd.getCashLitres() + qty);
                            break;
                    }
                    bd.setTotalLitres(bd.getCashLitres() + bd.getCardLitres() + bd.getCcmsLitres()
                            + bd.getUpiLitres() + bd.getChequeLitres());
                }
            }
        }

        return new ArrayList<>(productBreakdowns.values());
    }

    // === PRINT DATA: Sales-related sections ===

    /**
     * Populate meter readings into print data.
     */
    @Transactional(readOnly = true)
    public void populateMeterReadings(ShiftReportPrintData data, Long shiftId) {
        List<NozzleInventory> nozzleInvs = nozzleInventoryRepository.findByShiftId(shiftId);
        for (NozzleInventory ni : nozzleInvs) {
            // Skip incomplete readings (no close reading = opening snapshot only)
            if (ni.getCloseMeterReading() == null) continue;
            ShiftReportPrintData.MeterReading mr = new ShiftReportPrintData.MeterReading();
            mr.setPumpName(ni.getNozzle().getPump() != null ? ni.getNozzle().getPump().getName() : "-");
            mr.setNozzleName(ni.getNozzle().getNozzleName());
            mr.setProductName(ni.getNozzle().getTank() != null && ni.getNozzle().getTank().getProduct() != null
                    ? ni.getNozzle().getTank().getProduct().getName() : "-");
            mr.setOpenReading(ni.getOpenMeterReading());
            mr.setCloseReading(ni.getCloseMeterReading());
            mr.setSales(ni.getSales());
            mr.setTestQuantity(ni.getTestQuantity());
            mr.setRate(ni.getNozzle().getTank() != null && ni.getNozzle().getTank().getProduct() != null
                    && ni.getNozzle().getTank().getProduct().getPrice() != null
                    ? ni.getNozzle().getTank().getProduct().getPrice().doubleValue() : ni.getRate());
            mr.setAmount(ni.getAmount());
            data.getMeterReadings().add(mr);
        }
    }

    /**
     * Populate tank readings into print data.
     */
    @Transactional(readOnly = true)
    public void populateTankReadings(ShiftReportPrintData data, Long shiftId) {
        List<TankInventory> tankInvs = tankInventoryRepository.findByShiftId(shiftId);
        for (TankInventory ti : tankInvs) {
            // Skip incomplete readings (no close dip = opening snapshot only)
            if (ti.getCloseDip() == null && ti.getCloseStock() == null) continue;
            ShiftReportPrintData.TankReading tr = new ShiftReportPrintData.TankReading();
            tr.setTankName(ti.getTank().getName());
            tr.setProductName(ti.getTank().getProduct() != null ? ti.getTank().getProduct().getName() : "-");
            tr.setOpenDip(ti.getOpenDip());
            tr.setOpenStock(ti.getOpenStock());
            tr.setIncomeStock(ti.getIncomeStock());
            tr.setTotalStock(ti.getTotalStock());
            tr.setCloseDip(ti.getCloseDip());
            tr.setCloseStock(ti.getCloseStock());
            tr.setSaleStock(ti.getSaleStock());
            data.getTankReadings().add(tr);
        }
    }

    /**
     * Populate sales differences (tank sale vs meter sale by product) into print data.
     */
    @Transactional(readOnly = true)
    public void populateSalesDifferences(ShiftReportPrintData data, Long shiftId) {
        List<TankInventory> tankInvs = tankInventoryRepository.findByShiftId(shiftId);
        List<NozzleInventory> nozzleInvs = nozzleInventoryRepository.findByShiftId(shiftId);

        Map<String, double[]> tankSalesByProduct = new LinkedHashMap<>();
        for (TankInventory ti : tankInvs) {
            String productName = ti.getTank().getProduct() != null ? ti.getTank().getProduct().getName() : "Unknown";
            double sale = ti.getSaleStock() != null ? ti.getSaleStock() : 0;
            tankSalesByProduct.merge(productName, new double[]{sale}, (o, n) -> new double[]{o[0] + n[0]});
        }
        Map<String, double[]> meterSalesByProduct = new LinkedHashMap<>();
        for (NozzleInventory ni : nozzleInvs) {
            String productName = ni.getNozzle().getTank() != null && ni.getNozzle().getTank().getProduct() != null
                    ? ni.getNozzle().getTank().getProduct().getName() : "Unknown";
            double sale = ni.getSales() != null ? ni.getSales() : 0;
            meterSalesByProduct.merge(productName, new double[]{sale}, (o, n) -> new double[]{o[0] + n[0]});
        }
        Set<String> allProducts = new LinkedHashSet<>();
        allProducts.addAll(tankSalesByProduct.keySet());
        allProducts.addAll(meterSalesByProduct.keySet());
        for (String product : allProducts) {
            double tankSale = tankSalesByProduct.containsKey(product) ? tankSalesByProduct.get(product)[0] : 0;
            double meterSale = meterSalesByProduct.containsKey(product) ? meterSalesByProduct.get(product)[0] : 0;
            ShiftReportPrintData.SalesDifference sd = new ShiftReportPrintData.SalesDifference();
            sd.setProductName(product);
            sd.setTankSale(tankSale);
            sd.setMeterSale(meterSale);
            sd.setDifference(tankSale - meterSale);
            data.getSalesDifferences().add(sd);
        }
    }

    /**
     * Populate cash and credit bill details into print data.
     */
    @Transactional(readOnly = true)
    public void populateBillDetails(ShiftReportPrintData data, Long shiftId) {
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);

        // Cash Bill Details
        List<InvoiceBill> cashBills = invoices.stream()
                .filter(inv -> com.stopforfuel.backend.enums.BillType.CASH.equals(inv.getBillType()))
                .collect(Collectors.toList());
        for (InvoiceBill bill : cashBills) {
            ShiftReportPrintData.CashBillDetail cbd = new ShiftReportPrintData.CashBillDetail();
            cbd.setBillNo(bill.getBillNo());
            cbd.setVehicleNo(bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-");
            cbd.setDriverName(bill.getDriverName());
            cbd.setPaymentMode(bill.getPaymentMode() != null ? bill.getPaymentMode().name() : "CASH");
            cbd.setAmount(bill.getNetAmount());
            StringBuilder prodStr = new StringBuilder();
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) {
                    String pName = ip.getProduct() != null ? abbreviateProduct(ip.getProduct().getName()) : "?";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                    if (prodStr.length() > 0) prodStr.append(" ");
                    prodStr.append(pName).append(":").append(String.format("%.0f", qty));
                }
            }
            cbd.setProducts(prodStr.toString());
            data.getCashBillDetails().add(cbd);
        }

        // Credit Bill Details (grouped by customer)
        List<InvoiceBill> creditBills = invoices.stream()
                .filter(inv -> com.stopforfuel.backend.enums.BillType.CREDIT.equals(inv.getBillType()))
                .sorted(Comparator.comparing(inv -> inv.getCustomer() != null ? inv.getCustomer().getName() : ""))
                .collect(Collectors.toList());

        for (InvoiceBill bill : creditBills) {
            ShiftReportPrintData.CreditBillDetail cbd = new ShiftReportPrintData.CreditBillDetail();
            cbd.setCustomerName(bill.getCustomer() != null ? bill.getCustomer().getName() : "-");
            cbd.setBillNo(bill.getBillNo());
            cbd.setVehicleNo(bill.getVehicle() != null ? bill.getVehicle().getVehicleNumber() : "-");
            cbd.setAmount(bill.getNetAmount());

            StringBuilder prodStr = new StringBuilder();
            if (bill.getProducts() != null) {
                for (InvoiceProduct ip : bill.getProducts()) {
                    String pName = ip.getProduct() != null ? abbreviateProduct(ip.getProduct().getName()) : "?";
                    double qty = ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                    if (prodStr.length() > 0) prodStr.append(" ");
                    prodStr.append(pName).append(":").append(String.format("%.0f", qty));
                }
            }
            cbd.setProducts(prodStr.toString());
            data.getCreditBillDetails().add(cbd);
        }

        // Payment Mode Breakdown (cash bill amounts by payment mode)
        Map<String, BigDecimal> modeAmounts = new LinkedHashMap<>();
        Map<String, Integer> modeCounts = new LinkedHashMap<>();
        for (InvoiceBill inv : invoices) {
            if (!com.stopforfuel.backend.enums.BillType.CASH.equals(inv.getBillType())) continue;
            String mode = inv.getPaymentMode() != null ? inv.getPaymentMode().name() : "CASH";
            BigDecimal amt = inv.getNetAmount() != null ? inv.getNetAmount() : BigDecimal.ZERO;
            modeAmounts.merge(mode, amt, BigDecimal::add);
            modeCounts.merge(mode, 1, Integer::sum);
        }
        // Fixed display order
        for (String mode : List.of("CASH", "CARD", "UPI", "CCMS", "CHEQUE", "BANK")) {
            if (modeAmounts.containsKey(mode)) {
                ShiftReportPrintData.PaymentModeBreakdown pmb = new ShiftReportPrintData.PaymentModeBreakdown();
                pmb.setMode(mode);
                pmb.setAmount(modeAmounts.get(mode));
                pmb.setBillCount(modeCounts.getOrDefault(mode, 0));
                data.getPaymentModeBreakdown().add(pmb);
            }
        }
        // Any remaining modes not in the fixed list
        for (Map.Entry<String, BigDecimal> e : modeAmounts.entrySet()) {
            if (!List.of("CASH", "CARD", "UPI", "CCMS", "CHEQUE", "BANK").contains(e.getKey())) {
                ShiftReportPrintData.PaymentModeBreakdown pmb = new ShiftReportPrintData.PaymentModeBreakdown();
                pmb.setMode(e.getKey());
                pmb.setAmount(e.getValue());
                pmb.setBillCount(modeCounts.getOrDefault(e.getKey(), 0));
                data.getPaymentModeBreakdown().add(pmb);
            }
        }
    }

    /**
     * Populate stock summary and stock position into print data.
     */
    @Transactional(readOnly = true)
    public void populateStockData(ShiftReportPrintData data, Long shiftId) {
        List<TankInventory> tankInvs = tankInventoryRepository.findByShiftId(shiftId);
        List<InvoiceBill> invoices = invoiceBillRepository.findByShiftId(shiftId);

        // Stock Summary — only products with sales > 0
        List<ProductInventory> shiftProductInvs = productInventoryRepository.findByShiftId(shiftId);
        Map<Long, ProductInventory> productInvMap = new HashMap<>();
        for (ProductInventory pi : shiftProductInvs) {
            productInvMap.put(pi.getProduct().getId(), pi);
        }

        List<Product> allProductEntities = productRepository.findByActive(true);
        for (Product product : allProductEntities) {
            ShiftReportPrintData.StockSummaryRow row = new ShiftReportPrintData.StockSummaryRow();
            row.setProductName(product.getName());
            row.setRate(product.getPrice());

            if ("FUEL".equalsIgnoreCase(product.getCategory())) {
                double open = 0, receipt = 0, total = 0, sales = 0;
                for (TankInventory ti : tankInvs) {
                    if (ti.getTank().getProduct() != null && ti.getTank().getProduct().getId().equals(product.getId())) {
                        open += ti.getOpenStock() != null ? ti.getOpenStock() : 0;
                        receipt += ti.getIncomeStock() != null ? ti.getIncomeStock() : 0;
                        total += ti.getTotalStock() != null ? ti.getTotalStock() : 0;
                        sales += ti.getSaleStock() != null ? ti.getSaleStock() : 0;
                    }
                }
                if (sales == 0) continue;
                row.setOpenStock(open);
                row.setReceipt(receipt);
                row.setTotalStock(total);
                row.setSales(sales);
            } else {
                ProductInventory pi = productInvMap.get(product.getId());
                if (pi != null) {
                    double sales = pi.getSales() != null ? pi.getSales() : 0;
                    if (sales == 0) continue;
                    row.setOpenStock(pi.getOpenStock() != null ? pi.getOpenStock() : 0);
                    row.setReceipt(pi.getIncomeStock() != null ? pi.getIncomeStock() : 0);
                    row.setTotalStock(pi.getTotalStock() != null ? pi.getTotalStock() : 0);
                    row.setSales(sales);
                } else {
                    double sales = 0;
                    for (InvoiceBill inv : invoices) {
                        if (inv.getProducts() != null) {
                            for (InvoiceProduct ip : inv.getProducts()) {
                                if (ip.getProduct() != null && ip.getProduct().getId().equals(product.getId())) {
                                    sales += ip.getQuantity() != null ? ip.getQuantity().doubleValue() : 0;
                                }
                            }
                        }
                    }
                    if (sales == 0) continue;
                    row.setSales(sales);
                    row.setOpenStock(0.0);
                    row.setReceipt(0.0);
                    row.setTotalStock(0.0);
                }
            }

            BigDecimal salesAmt = product.getPrice() != null && row.getSales() != null
                    ? product.getPrice().multiply(BigDecimal.valueOf(row.getSales()))
                    : BigDecimal.ZERO;
            row.setAmount(salesAmt.setScale(2, RoundingMode.HALF_UP));
            data.getStockSummary().add(row);
        }

        // Stock Position — godown + cashier balances for non-fuel products
        List<GodownStock> godownStocks = godownStockRepository.findByScid(SecurityUtils.getScid());
        Map<Long, GodownStock> godownMap = new HashMap<>();
        for (GodownStock gs : godownStocks) {
            godownMap.put(gs.getProduct().getId(), gs);
        }
        List<CashierStock> cashierStocks = cashierStockRepository.findByScid(SecurityUtils.getScid());
        Map<Long, CashierStock> cashierMap = new HashMap<>();
        for (CashierStock cs : cashierStocks) {
            cashierMap.put(cs.getProduct().getId(), cs);
        }
        for (Product product : allProductEntities) {
            if ("FUEL".equalsIgnoreCase(product.getCategory())) continue;
            GodownStock gs = godownMap.get(product.getId());
            CashierStock cs = cashierMap.get(product.getId());
            double godownQty = (gs != null && gs.getCurrentStock() != null) ? gs.getCurrentStock() : 0.0;
            double cashierQty = (cs != null && cs.getCurrentStock() != null) ? cs.getCurrentStock() : 0.0;
            if (godownQty == 0 && cashierQty == 0) continue;
            ShiftReportPrintData.StockPositionRow posRow = new ShiftReportPrintData.StockPositionRow();
            posRow.setProductName(product.getName());
            posRow.setGodownStock(godownQty);
            posRow.setCashierStock(cashierQty);
            posRow.setTotalStock(godownQty + cashierQty);
            posRow.setLowStock(gs != null && gs.getReorderLevel() != null
                    && gs.getReorderLevel() > 0 && godownQty <= gs.getReorderLevel());
            data.getStockPosition().add(posRow);
        }
    }

    String abbreviateProduct(String name) {
        if (name == null) return "?";
        String upper = name.toUpperCase();
        if (upper.contains("PETROL") || upper.equals("MS")) return "P";
        if (upper.contains("XTRA") || upper.contains("XP") || upper.contains("PREMIUM")) return "XP";
        if (upper.contains("DIESEL") || upper.equals("HSD") || upper.contains("HIGH SPEED")) return "HSD";
        return name.length() > 4 ? name.substring(0, 4) : name;
    }
}
