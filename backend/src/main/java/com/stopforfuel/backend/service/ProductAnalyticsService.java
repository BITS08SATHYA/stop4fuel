package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ProductAnalyticsDTO;
import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.InvoiceProductRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductAnalyticsService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    /** Flag a product for purchase when stock covers fewer than this many days of sales. */
    private static final double REORDER_ALERT_DAYS = 14;
    /** Suggested order tops stock back up to this many days of sales. */
    private static final double RESTOCK_TARGET_DAYS = 30;
    /** No sales for this many days marks a product as STALE (slow mover). */
    private static final long STALE_AFTER_DAYS = 30;

    private final ProductRepository productRepository;
    private final GodownStockRepository godownStockRepository;
    private final CashierStockRepository cashierStockRepository;
    private final InvoiceProductRepository invoiceProductRepository;

    @Transactional(readOnly = true)
    public ProductAnalyticsDTO getProductAnalytics(LocalDate from, LocalDate to) {
        Long scid = SecurityUtils.getScid();
        LocalDate toDate = to != null ? to : LocalDate.now(APP_ZONE);
        LocalDate fromDate = from != null ? from : toDate.minusDays(29);
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must be on or before toDate");
        }
        int rangeDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        List<Product> products = productRepository
                .findByCategoryNotIgnoreCaseAndActiveAndScid("FUEL", true, scid);

        Map<Long, GodownStock> godownByProduct = new HashMap<>();
        for (GodownStock gs : godownStockRepository.findByScid(scid)) {
            if (gs.getProduct() != null) godownByProduct.put(gs.getProduct().getId(), gs);
        }
        Map<Long, CashierStock> cashierByProduct = new HashMap<>();
        for (CashierStock cs : cashierStockRepository.findByScid(scid)) {
            if (cs.getProduct() != null) cashierByProduct.put(cs.getProduct().getId(), cs);
        }

        Map<Long, BigDecimal[]> salesByProduct = new HashMap<>();
        for (Object[] row : invoiceProductRepository.getSalesTotalsByProduct(
                scid, fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX))) {
            salesByProduct.put((Long) row[0],
                    new BigDecimal[]{toBigDecimal(row[1]), toBigDecimal(row[2])});
        }
        Map<Long, LocalDate> lastSaleByProduct = new HashMap<>();
        for (Object[] row : invoiceProductRepository.getLastSaleDateByProduct(scid)) {
            lastSaleByProduct.put((Long) row[0], ((LocalDateTime) row[1]).toLocalDate());
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        BigDecimal totalStockValue = BigDecimal.ZERO;
        BigDecimal totalSoldQty = BigDecimal.ZERO;
        BigDecimal totalSoldAmount = BigDecimal.ZERO;
        int belowReorder = 0;
        int outOfStock = 0;

        java.util.ArrayList<ProductAnalyticsDTO.Row> rows = new java.util.ArrayList<>();
        for (Product p : products) {
            GodownStock gs = godownByProduct.get(p.getId());
            CashierStock cs = cashierByProduct.get(p.getId());
            double godown = gs != null && gs.getCurrentStock() != null ? gs.getCurrentStock() : 0;
            double cashier = cs != null && cs.getCurrentStock() != null ? cs.getCurrentStock() : 0;
            double totalStock = godown + cashier;
            Double reorderLevel = gs != null ? gs.getReorderLevel() : null;
            Double maxStock = gs != null ? gs.getMaxStock() : null;

            BigDecimal[] sales = salesByProduct.getOrDefault(p.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal soldQty = sales[0];
            BigDecimal soldAmount = sales[1];
            BigDecimal avgDaily = soldQty.divide(BigDecimal.valueOf(rangeDays), 4, RoundingMode.HALF_UP);

            Double daysOfStock = null;
            Double suggestedOrderQty = null;
            if (avgDaily.compareTo(BigDecimal.ZERO) > 0) {
                daysOfStock = round1(totalStock / avgDaily.doubleValue());
                if (daysOfStock < REORDER_ALERT_DAYS) {
                    suggestedOrderQty = Math.ceil(avgDaily.doubleValue() * RESTOCK_TARGET_DAYS - totalStock);
                    if (suggestedOrderQty <= 0) suggestedOrderQty = null;
                }
            }

            LocalDate lastSale = lastSaleByProduct.get(p.getId());
            String status;
            if (totalStock <= 0) {
                status = "OUT";
                outOfStock++;
            } else if (reorderLevel != null && totalStock <= reorderLevel) {
                status = "LOW";
                belowReorder++;
            } else if (daysOfStock != null && daysOfStock < REORDER_ALERT_DAYS) {
                status = "LOW";
                belowReorder++;
            } else if (lastSale == null || ChronoUnit.DAYS.between(lastSale, today) > STALE_AFTER_DAYS) {
                status = "STALE";
            } else {
                status = "OK";
            }

            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            BigDecimal stockValue = price.multiply(BigDecimal.valueOf(totalStock));
            totalStockValue = totalStockValue.add(stockValue);
            totalSoldQty = totalSoldQty.add(soldQty);
            totalSoldAmount = totalSoldAmount.add(soldAmount);

            rows.add(ProductAnalyticsDTO.Row.builder()
                    .productId(p.getId())
                    .name(p.getName())
                    .brand(p.getBrand())
                    .category(p.getCategory())
                    .unit(p.getUnit())
                    .price(p.getPrice())
                    .godownStock(godown)
                    .cashierStock(cashier)
                    .totalStock(totalStock)
                    .reorderLevel(reorderLevel)
                    .maxStock(maxStock)
                    .stockValue(stockValue)
                    .soldQuantity(soldQty)
                    .soldAmount(soldAmount)
                    .avgDailyQuantity(avgDaily)
                    .daysOfStock(daysOfStock)
                    .suggestedOrderQty(suggestedOrderQty)
                    .lastSaleDate(lastSale != null ? lastSale.toString() : null)
                    .stockStatus(status)
                    .build());
        }

        // Most urgent first: OUT, then LOW by days-of-stock, then rest by sold amount
        rows.sort(Comparator
                .comparingInt((ProductAnalyticsDTO.Row r) -> switch (r.getStockStatus()) {
                    case "OUT" -> 0;
                    case "LOW" -> 1;
                    default -> 2;
                })
                .thenComparing(r -> r.getDaysOfStock() != null ? r.getDaysOfStock() : Double.MAX_VALUE)
                .thenComparing(r -> r.getSoldAmount() != null ? r.getSoldAmount().negate() : BigDecimal.ZERO));

        return ProductAnalyticsDTO.builder()
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .rangeDays(rangeDays)
                .totalProducts(products.size())
                .belowReorderCount(belowReorder)
                .outOfStockCount(outOfStock)
                .totalStockValue(totalStockValue)
                .totalSoldQuantity(totalSoldQty)
                .totalSoldAmount(totalSoldAmount)
                .products(rows)
                .build();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
