package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductPriceHistory;
import com.stopforfuel.backend.repository.ProductPriceHistoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
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
}
