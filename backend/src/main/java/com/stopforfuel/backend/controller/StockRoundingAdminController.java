package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.util.UnitUtils;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One-shot admin backfill: rounds existing fractional stock values for products
 * whose unit is whole-count (Pieces, Box, Packets, ...). Run once after the
 * round-on-save change deploys to clean up legacy 301.99 / 302.99 drift.
 */
@RestController
@RequestMapping("/api/admin/stock-rounding")
@RequiredArgsConstructor
@Slf4j
public class StockRoundingAdminController {

    private final ProductInventoryRepository productInventoryRepository;
    private final GodownStockRepository godownStockRepository;
    private final CashierStockRepository cashierStockRepository;

    @PostMapping("/round-whole-count")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> roundWholeCount() {
        Long scid = SecurityUtils.getScid();
        Map<String, Object> result = new HashMap<>();

        int piRows = 0;
        List<ProductInventory> pis = productInventoryRepository.findAllByScidWithProduct(scid);
        for (ProductInventory pi : pis) {
            String unit = pi.getProduct() != null ? pi.getProduct().getUnit() : null;
            if (!UnitUtils.isWholeCount(unit)) continue;

            Double openR = UnitUtils.roundIfWholeCount(unit, pi.getOpenStock());
            Double incomeR = UnitUtils.roundIfWholeCount(unit, pi.getIncomeStock());
            Double totalR = UnitUtils.roundIfWholeCount(unit, pi.getTotalStock());
            Double closeR = UnitUtils.roundIfWholeCount(unit, pi.getCloseStock());
            Double salesR = UnitUtils.roundIfWholeCount(unit, pi.getSales());

            if (!Objects.equals(openR, pi.getOpenStock())
                    || !Objects.equals(incomeR, pi.getIncomeStock())
                    || !Objects.equals(totalR, pi.getTotalStock())
                    || !Objects.equals(closeR, pi.getCloseStock())
                    || !Objects.equals(salesR, pi.getSales())) {
                log.info("Rounding ProductInventory#{} ({}): open {}->{}, income {}->{}, total {}->{}, close {}->{}, sales {}->{}",
                        pi.getId(), pi.getProduct().getName(),
                        pi.getOpenStock(), openR, pi.getIncomeStock(), incomeR,
                        pi.getTotalStock(), totalR, pi.getCloseStock(), closeR,
                        pi.getSales(), salesR);
                pi.setOpenStock(openR);
                pi.setIncomeStock(incomeR);
                pi.setTotalStock(totalR);
                pi.setCloseStock(closeR);
                pi.setSales(salesR);
                productInventoryRepository.save(pi);
                piRows++;
            }
        }

        int gsRows = 0;
        for (GodownStock gs : godownStockRepository.findByScid(scid)) {
            String unit = gs.getProduct() != null ? gs.getProduct().getUnit() : null;
            if (!UnitUtils.isWholeCount(unit)) continue;
            Double curR = UnitUtils.roundIfWholeCount(unit, gs.getCurrentStock());
            Double reorderR = UnitUtils.roundIfWholeCount(unit, gs.getReorderLevel());
            Double maxR = UnitUtils.roundIfWholeCount(unit, gs.getMaxStock());
            if (!Objects.equals(curR, gs.getCurrentStock())
                    || !Objects.equals(reorderR, gs.getReorderLevel())
                    || !Objects.equals(maxR, gs.getMaxStock())) {
                log.info("Rounding GodownStock#{} ({}): current {}->{}, reorder {}->{}, max {}->{}",
                        gs.getId(), gs.getProduct().getName(),
                        gs.getCurrentStock(), curR, gs.getReorderLevel(), reorderR, gs.getMaxStock(), maxR);
                gs.setCurrentStock(curR);
                gs.setReorderLevel(reorderR);
                gs.setMaxStock(maxR);
                godownStockRepository.save(gs);
                gsRows++;
            }
        }

        int csRows = 0;
        for (CashierStock cs : cashierStockRepository.findByScid(scid)) {
            String unit = cs.getProduct() != null ? cs.getProduct().getUnit() : null;
            if (!UnitUtils.isWholeCount(unit)) continue;
            Double curR = UnitUtils.roundIfWholeCount(unit, cs.getCurrentStock());
            Double maxR = UnitUtils.roundIfWholeCount(unit, cs.getMaxCapacity());
            if (!Objects.equals(curR, cs.getCurrentStock()) || !Objects.equals(maxR, cs.getMaxCapacity())) {
                log.info("Rounding CashierStock#{} ({}): current {}->{}, max {}->{}",
                        cs.getId(), cs.getProduct().getName(),
                        cs.getCurrentStock(), curR, cs.getMaxCapacity(), maxR);
                cs.setCurrentStock(curR);
                cs.setMaxCapacity(maxR);
                cashierStockRepository.save(cs);
                csRows++;
            }
        }

        result.put("scid", scid);
        result.put("productInventoryRoundedRows", piRows);
        result.put("godownStockRoundedRows", gsRows);
        result.put("cashierStockRoundedRows", csRows);
        return ResponseEntity.ok(result);
    }

    /**
     * One-shot cleanup: delete legacy {@link ProductInventory} rows for FUEL products.
     * Fuel is tracked via TankInventory; these rows exist only because earlier versions of
     * {@code autoCreateForShift} created one per active product regardless of category.
     * Legacy rows produced the "Diesel closing -1188.36" artifact on the Product Stock page.
     */
    @PostMapping("/purge-fuel-product-inventory")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> purgeFuelProductInventory() {
        Long scid = SecurityUtils.getScid();
        List<ProductInventory> all = productInventoryRepository.findAllByScidWithProduct(scid);
        int deleted = 0;
        for (ProductInventory pi : all) {
            if (pi.getProduct() == null) continue;
            if (!"FUEL".equalsIgnoreCase(pi.getProduct().getCategory())) continue;
            log.info("Deleting legacy fuel ProductInventory#{} ({}, date {}, open {}, close {})",
                    pi.getId(), pi.getProduct().getName(), pi.getDate(),
                    pi.getOpenStock(), pi.getCloseStock());
            productInventoryRepository.delete(pi);
            deleted++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("scid", scid);
        result.put("deletedFuelProductInventoryRows", deleted);
        return ResponseEntity.ok(result);
    }
}
