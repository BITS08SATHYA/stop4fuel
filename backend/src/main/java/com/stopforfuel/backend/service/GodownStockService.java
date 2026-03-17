package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.repository.GodownStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GodownStockService {

    private final GodownStockRepository repository;

    public List<GodownStock> getAll() {
        return repository.findByScid(1L);
    }

    public GodownStock getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("GodownStock not found with id: " + id));
    }

    public GodownStock getByProduct(Long productId) {
        return repository.findByProductIdAndScid(productId, 1L).orElse(null);
    }

    public List<GodownStock> getLowStockItems() {
        return repository.findLowStockItems(1L);
    }

    public GodownStock save(GodownStock stock) {
        if (stock.getScid() == null) stock.setScid(1L);
        return repository.save(stock);
    }

    public GodownStock update(Long id, GodownStock details) {
        GodownStock existing = getById(id);
        existing.setProduct(details.getProduct());
        existing.setCurrentStock(details.getCurrentStock());
        existing.setReorderLevel(details.getReorderLevel());
        existing.setMaxStock(details.getMaxStock());
        existing.setLocation(details.getLocation());
        existing.setLastRestockDate(details.getLastRestockDate());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
