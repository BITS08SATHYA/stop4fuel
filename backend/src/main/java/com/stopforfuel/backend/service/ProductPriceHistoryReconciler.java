package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductPriceHistory;
import com.stopforfuel.backend.repository.ProductPriceHistoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Cold-start safety net for the price-history table.
 *
 * On boot, for every product whose latest ProductPriceHistory.price no longer
 * matches Product.price, insert a snapshot row for today capturing the current
 * Product.price. This catches drift introduced by any path that bypasses
 * {@link ProductPriceHistoryService#recordPriceChange} (direct SQL updates,
 * migrations, admin tools, or pre-feature historical edits).
 *
 * Without this, the resolver in {@link ProductPriceHistoryService#priceAsOf}
 * would silently pick the latest stale snapshot for any asOf >= that date,
 * because the fallback to Product.price only fires when zero rows exist.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductPriceHistoryReconciler implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now();
        int healed = 0;
        for (Product p : productRepository.findAll()) {
            if (p.getPrice() == null) continue;
            Optional<ProductPriceHistory> latestOpt =
                    priceHistoryRepository.findTopByProductIdOrderByEffectiveDateDesc(p.getId());
            if (latestOpt.isEmpty()) continue;
            ProductPriceHistory latest = latestOpt.get();
            if (latest.getPrice() != null && latest.getPrice().compareTo(p.getPrice()) == 0) continue;

            ProductPriceHistory row = priceHistoryRepository
                    .findByProductIdAndEffectiveDate(p.getId(), today)
                    .orElseGet(() -> {
                        ProductPriceHistory r = new ProductPriceHistory();
                        r.setProduct(p);
                        r.setEffectiveDate(today);
                        return r;
                    });
            if (row.getPrice() != null && row.getPrice().compareTo(p.getPrice()) == 0) continue;
            row.setPrice(p.getPrice());
            priceHistoryRepository.save(row);
            healed++;
            log.warn("ProductPriceHistory startup-reconcile: product '{}' (id={}) latest snapshot {} on {} did not match Product.price {} — wrote snapshot for {}",
                    p.getName(), p.getId(), latest.getPrice(), latest.getEffectiveDate(), p.getPrice(), today);
        }
        if (healed > 0) {
            log.warn("ProductPriceHistory startup-reconcile: healed {} product(s)", healed);
        }
    }
}
