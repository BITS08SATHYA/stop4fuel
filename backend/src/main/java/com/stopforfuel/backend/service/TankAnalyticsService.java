package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.TankAnalyticsDTO;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.NozzleInventoryRepository;
import com.stopforfuel.backend.repository.TankInventoryRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TankAnalyticsService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    /** Flag ORDER_SOON when the threshold-hit date is within this horizon. */
    private static final double ORDER_SOON_DAYS = 7;

    private final TankRepository tankRepository;
    private final TankInventoryRepository tankInventoryRepository;
    private final NozzleInventoryRepository nozzleInventoryRepository;

    @Transactional(readOnly = true)
    public TankAnalyticsDTO getTankAnalytics(LocalDate from, LocalDate to, int leadTimeDays, double tankerLoadLiters) {
        Long scid = SecurityUtils.getScid();
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate toDate = to != null ? to : today;
        LocalDate fromDate = from != null ? from : toDate.minusDays(29);
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must be on or before toDate");
        }
        if (leadTimeDays < 0 || leadTimeDays > 30) {
            throw new BusinessException("leadTimeDays must be between 0 and 30");
        }
        if (tankerLoadLiters < 0) {
            throw new BusinessException("tankerLoadLiters cannot be negative");
        }
        int rangeDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        List<Tank> tanks = tankRepository.findByActiveAndScid(true, scid);

        // Liters sold per tank in range (nozzle meter based — canonical sales source)
        Map<Long, Double> soldByTank = new HashMap<>();
        for (Object[] row : nozzleInventoryRepository.sumDailySalesByTank(scid, fromDate, toDate)) {
            Long tankId = (Long) row[1];
            double liters = row[2] != null ? ((Number) row[2]).doubleValue() : 0;
            soldByTank.merge(tankId, liters, Double::sum);
        }

        // Daily liters per fuel product (chart series)
        List<TankAnalyticsDTO.DailyPoint> dailyProductSales = new ArrayList<>();
        for (Object[] row : nozzleInventoryRepository.sumDailySalesByProduct(scid, fromDate, toDate)) {
            dailyProductSales.add(TankAnalyticsDTO.DailyPoint.builder()
                    .date(row[0].toString())
                    .product((String) row[1])
                    .liters(row[2] != null ? ((Number) row[2]).doubleValue() : 0)
                    .build());
        }

        // Daily opening stock per tank + deliveries, and monthly purchase totals
        List<TankInventory> tankInvRows = tankInventoryRepository.findByScidAndDateBetween(scid, fromDate, toDate);
        List<TankAnalyticsDTO.DailyStockPoint> dailyTankStock = new ArrayList<>();
        Map<String, Double> monthlyPurchases = new TreeMap<>();
        Map<Long, LocalDate> lastReadingByTank = new HashMap<>();
        double totalDelivered = 0;
        for (TankInventory ti : tankInvRows) {
            if (ti.getTank() == null || ti.getDate() == null) continue;
            double delivered = ti.getIncomeStock() != null ? ti.getIncomeStock() : 0;
            dailyTankStock.add(TankAnalyticsDTO.DailyStockPoint.builder()
                    .date(ti.getDate().toString())
                    .tank(ti.getTank().getName())
                    .openStock(ti.getOpenStock())
                    .delivered(delivered)
                    .build());
            if (delivered > 0) {
                monthlyPurchases.merge(ti.getDate().toString().substring(0, 7), delivered, Double::sum);
                totalDelivered += delivered;
            }
            lastReadingByTank.merge(ti.getTank().getId(), ti.getDate(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }

        // Per-tank projections
        List<TankAnalyticsDTO.TankRow> tankRows = new ArrayList<>();
        double totalStock = 0;
        double totalCapacity = 0;
        double totalAvgDaily = 0;
        LocalDate nextEmpty = null;
        for (Tank tank : tanks) {
            double stock = tank.getAvailableStock() != null ? tank.getAvailableStock() : 0;
            double capacity = tank.getCapacity() != null ? tank.getCapacity() : 0;
            double threshold = tank.getThresholdStock() != null ? tank.getThresholdStock() : 0;
            double avgDaily = soldByTank.getOrDefault(tank.getId(), 0.0) / rangeDays;

            Double daysToEmpty = null;
            Double daysToThreshold = null;
            LocalDate projectedEmpty = null;
            LocalDate thresholdHit = null;
            LocalDate orderDate = null;
            Double suggestedOrder = null;
            String status = "STAGNANT";
            if (avgDaily > 0) {
                daysToEmpty = round1(stock / avgDaily);
                daysToThreshold = round1(Math.max(stock - threshold, 0) / avgDaily);
                projectedEmpty = today.plusDays((long) Math.floor(daysToEmpty));
                thresholdHit = today.plusDays((long) Math.floor(daysToThreshold));
                orderDate = thresholdHit.minusDays(leadTimeDays);
                if (orderDate.isBefore(today)) orderDate = today;

                // Stock when the tanker arrives (order placed on orderDate, delivered leadTimeDays later)
                double stockAtDelivery = Math.max(
                        stock - avgDaily * (ChronoUnit.DAYS.between(today, orderDate) + leadTimeDays), 0);
                double headroom = Math.max(capacity - stockAtDelivery, 0);
                if (tankerLoadLiters > 0 && headroom >= tankerLoadLiters) {
                    suggestedOrder = Math.floor(headroom / tankerLoadLiters) * tankerLoadLiters;
                } else if (headroom > 0) {
                    suggestedOrder = Math.floor(headroom / 1000) * 1000;
                }
                if (suggestedOrder != null && suggestedOrder <= 0) suggestedOrder = null;

                if (daysToThreshold <= leadTimeDays) {
                    status = "ORDER_NOW";
                } else if (daysToThreshold <= leadTimeDays + ORDER_SOON_DAYS) {
                    status = "ORDER_SOON";
                } else {
                    status = "OK";
                }
                if (nextEmpty == null || projectedEmpty.isBefore(nextEmpty)) nextEmpty = projectedEmpty;
            }

            totalStock += stock;
            totalCapacity += capacity;
            totalAvgDaily += avgDaily;
            LocalDate lastReading = lastReadingByTank.get(tank.getId());

            tankRows.add(TankAnalyticsDTO.TankRow.builder()
                    .tankId(tank.getId())
                    .name(tank.getName())
                    .productName(tank.getProduct() != null ? tank.getProduct().getName() : null)
                    .capacity(capacity)
                    .currentStock(stock)
                    .thresholdStock(threshold)
                    .fillPercent(capacity > 0 ? round1(stock / capacity * 100) : null)
                    .avgDailySales(round1(avgDaily))
                    .daysToEmpty(daysToEmpty)
                    .daysToThreshold(daysToThreshold)
                    .projectedEmptyDate(projectedEmpty != null ? projectedEmpty.toString() : null)
                    .thresholdHitDate(thresholdHit != null ? thresholdHit.toString() : null)
                    .recommendedOrderDate(orderDate != null ? orderDate.toString() : null)
                    .suggestedOrderLiters(suggestedOrder)
                    .lastReadingDate(lastReading != null ? lastReading.toString() : null)
                    .status(status)
                    .build());
        }

        // Most urgent first
        Map<String, Integer> statusOrder = Map.of("ORDER_NOW", 0, "ORDER_SOON", 1, "OK", 2, "STAGNANT", 3);
        tankRows.sort((a, b) -> {
            int cmp = Integer.compare(statusOrder.getOrDefault(a.getStatus(), 9),
                    statusOrder.getOrDefault(b.getStatus(), 9));
            if (cmp != 0) return cmp;
            double da = a.getDaysToThreshold() != null ? a.getDaysToThreshold() : Double.MAX_VALUE;
            double db = b.getDaysToThreshold() != null ? b.getDaysToThreshold() : Double.MAX_VALUE;
            return Double.compare(da, db);
        });

        List<TankAnalyticsDTO.MonthlyPoint> monthly = new ArrayList<>();
        for (Map.Entry<String, Double> e : new LinkedHashMap<>(monthlyPurchases).entrySet()) {
            monthly.add(TankAnalyticsDTO.MonthlyPoint.builder().month(e.getKey()).liters(e.getValue()).build());
        }

        return TankAnalyticsDTO.builder()
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .rangeDays(rangeDays)
                .leadTimeDays(leadTimeDays)
                .tankerLoadLiters(tankerLoadLiters)
                .totalStock(totalStock)
                .totalCapacity(totalCapacity)
                .totalAvgDailySales(round1(totalAvgDaily))
                .totalDeliveredInRange(totalDelivered)
                .nextEmptyDate(nextEmpty != null ? nextEmpty.toString() : null)
                .tanks(tankRows)
                .dailyProductSales(dailyProductSales)
                .dailyTankStock(dailyTankStock)
                .monthlyPurchases(monthly)
                .build();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
