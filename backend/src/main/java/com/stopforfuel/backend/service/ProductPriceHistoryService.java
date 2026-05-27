package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductPriceHistory;
import com.stopforfuel.backend.repository.ProductPriceHistoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductPriceHistoryService {

    private final ProductPriceHistoryRepository repository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductPriceHistory> getByProduct(Long productId) {
        return repository.findByProductIdOrderByEffectiveDateDesc(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductPriceHistory> getByDateRange(LocalDate from, LocalDate to) {
        return repository.findByEffectiveDateBetweenOrderByEffectiveDateDesc(from, to);
    }

    @Transactional(readOnly = true)
    public List<ProductPriceHistory> getByProductAndDateRange(Long productId, LocalDate from, LocalDate to) {
        return repository.findByProductIdAndEffectiveDateBetweenOrderByEffectiveDateDesc(productId, from, to);
    }

    @Transactional
    public ProductPriceHistory create(ProductPriceHistory entry) {
        return repository.save(entry);
    }

    @Transactional
    public ProductPriceHistory update(Long id, ProductPriceHistory details) {
        ProductPriceHistory entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Price history entry not found: " + id));
        entry.setEffectiveDate(details.getEffectiveDate());
        entry.setProduct(details.getProduct());
        entry.setPrice(details.getPrice());
        return repository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Resolve the product price effective on {@code asOf}.
     * Picks the most recent history row with effectiveDate ≤ asOf;
     * falls back to {@code fallback} (typically Product.price) if no history exists yet.
     */
    @Transactional(readOnly = true)
    public BigDecimal priceAsOf(Long productId, LocalDate asOf, BigDecimal fallback) {
        if (productId == null || asOf == null) return fallback;
        return repository
                .findTopByProductIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(productId, asOf)
                .map(ProductPriceHistory::getPrice)
                .orElse(fallback);
    }

    /**
     * Auto-snapshot a price change: upsert a ProductPriceHistory row for today.
     * Same-day repeated edits overwrite the existing row (kept clean by the
     * (product_id, effective_date) unique constraint).
     *
     * Also self-heals snapshot-table drift: if the latest snapshot's price no
     * longer matches {@code previousPrice} (the value being replaced), some prior
     * change was missed — backfill an anchor row at (latest.effectiveDate + 1)
     * capturing previousPrice so shifts in the gap resolve to the right rate.
     */
    @Transactional
    public void recordPriceChange(Product product, BigDecimal previousPrice, BigDecimal newPrice) {
        if (product == null || product.getId() == null || newPrice == null) return;
        LocalDate today = LocalDate.now();

        if (previousPrice != null) {
            Optional<ProductPriceHistory> latestOpt =
                    repository.findTopByProductIdOrderByEffectiveDateDesc(product.getId());
            if (latestOpt.isPresent()) {
                ProductPriceHistory latest = latestOpt.get();
                if (latest.getPrice() != null
                        && latest.getPrice().compareTo(previousPrice) != 0
                        && latest.getEffectiveDate() != null
                        && latest.getEffectiveDate().isBefore(today)) {
                    LocalDate anchorDate = latest.getEffectiveDate().plusDays(1);
                    if (!anchorDate.isAfter(today.minusDays(1))) {
                        upsertSnapshot(product, anchorDate, previousPrice, "drift-backfill");
                    }
                }
            }
        }

        upsertSnapshot(product, today, newPrice, null);
    }

    /** Backwards-compatible overload — used by callers that don't know the previous price. */
    @Transactional
    public void recordPriceChange(Product product, BigDecimal newPrice) {
        recordPriceChange(product, product != null ? product.getPrice() : null, newPrice);
    }

    private void upsertSnapshot(Product product, LocalDate date, BigDecimal price, String reason) {
        ProductPriceHistory row = repository
                .findByProductIdAndEffectiveDate(product.getId(), date)
                .orElseGet(() -> {
                    ProductPriceHistory r = new ProductPriceHistory();
                    r.setProduct(product);
                    r.setEffectiveDate(date);
                    return r;
                });
        if (row.getPrice() != null && row.getPrice().compareTo(price) == 0) return;
        row.setPrice(price);
        repository.save(row);
        if (reason != null) {
            log.warn("ProductPriceHistory {}: product={} ({}), date={}, price={}",
                    reason, product.getId(), product.getName(), date, price);
        }
    }
}
