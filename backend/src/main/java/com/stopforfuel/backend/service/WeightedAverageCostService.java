package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.PurchaseInvoiceRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted-Average Cost engine. Drives COGS for the bunk audit P&L.
 *
 * Formula on purchase:
 *   newWac = (prevStock * prevWac + qty * unitPrice) / (prevStock + qty)
 * On sale:
 *   wacStock decrements by sold qty; wacCost stays the same until next purchase.
 *
 * Stock base may drift from the true physical count (dip/godown/cashier) because
 * the running counter isn't reconciled against actual inventory — that is accepted:
 * the audit endpoint `POST /api/admin/wac/recompute` replays history to reset.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeightedAverageCostService {

    private static final int COST_SCALE = 4;

    private final ProductRepository productRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;

    /**
     * Blend a new purchase lot into the product's WAC. Persists the product.
     * Safe to call with zero quantity (no-op).
     */
    @Transactional
    public void applyPurchase(Product product, double quantity, BigDecimal unitPrice) {
        if (product == null || quantity <= 0 || unitPrice == null
                || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        double prevStock = product.getWacStock() != null ? product.getWacStock() : 0.0;
        BigDecimal prevWac = product.getWacCost() != null ? product.getWacCost() : BigDecimal.ZERO;

        double newStock = prevStock + quantity;
        BigDecimal prevValue = prevWac.multiply(BigDecimal.valueOf(Math.max(prevStock, 0)));
        BigDecimal newValue = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal totalValue = prevValue.add(newValue);
        BigDecimal newWac = totalValue.divide(BigDecimal.valueOf(newStock), COST_SCALE, RoundingMode.HALF_UP);

        product.setWacCost(newWac);
        product.setWacStock(newStock);
        productRepository.save(product);
    }

    /**
     * Decrement running stock base on sale. wacCost is unchanged.
     * Safe to call with zero quantity (no-op).
     */
    @Transactional
    public void applySale(Product product, double quantity) {
        if (product == null || quantity <= 0) return;
        double prevStock = product.getWacStock() != null ? product.getWacStock() : 0.0;
        double newStock = prevStock - quantity;
        product.setWacStock(newStock);
        productRepository.save(product);
    }

    /**
     * Read current WAC for costing a sale. Returns ZERO if not yet initialised.
     */
    @Transactional(readOnly = true)
    public BigDecimal currentCost(Product product) {
        if (product == null || product.getWacCost() == null) return BigDecimal.ZERO;
        return product.getWacCost();
    }

    /**
     * Full backfill: for every product of the current scid, reset wacCost/wacStock and
     * replay every historical purchase in chronological order. Sales are NOT replayed
     * (we don't have them in one table) — so wacStock will represent total purchased,
     * not net on-hand. Users should run this once for cost baseline, then rely on live
     * applyPurchase/applySale hooks going forward.
     *
     * @return map of productId → resulting wacCost
     */
    @Transactional
    public Map<Long, BigDecimal> recomputeFromHistory() {
        Long scid = SecurityUtils.getScid();

        // reset every product under this tenant
        List<Product> products = productRepository.findByActiveAndScid(true, scid);
        for (Product p : products) {
            p.setWacCost(null);
            p.setWacStock(null);
        }
        productRepository.saveAll(products);

        // iterate purchase invoices in chronological order (oldest first)
        List<PurchaseInvoice> invoices = purchaseInvoiceRepository.findAllWithDetails(scid);
        invoices.sort(Comparator.comparing(PurchaseInvoice::getInvoiceDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        for (PurchaseInvoice inv : invoices) {
            if (inv.getItems() == null) continue;
            for (PurchaseInvoiceItem item : inv.getItems()) {
                if (item.getProduct() == null || item.getQuantity() == null || item.getUnitPrice() == null) continue;
                applyPurchase(item.getProduct(), item.getQuantity(), item.getUnitPrice());
            }
        }

        Map<Long, BigDecimal> result = new HashMap<>();
        for (Product p : productRepository.findByActiveAndScid(true, scid)) {
            result.put(p.getId(), p.getWacCost() != null ? p.getWacCost() : BigDecimal.ZERO);
        }
        log.info("WAC recompute complete for scid={} — {} products touched", scid, result.size());
        return result;
    }
}
