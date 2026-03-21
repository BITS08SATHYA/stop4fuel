package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CashierStockService {

    private final CashierStockRepository repository;

    public List<CashierStock> getAll() {
        return repository.findByScid(SecurityUtils.getScid());
    }

    public CashierStock getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("CashierStock not found with id: " + id));
    }

    public CashierStock getByProduct(Long productId) {
        return repository.findByProductIdAndScid(productId, SecurityUtils.getScid()).orElse(null);
    }

    public CashierStock save(CashierStock stock) {
        if (stock.getScid() == null) stock.setScid(SecurityUtils.getScid());
        return repository.save(stock);
    }

    public CashierStock update(Long id, CashierStock details) {
        CashierStock existing = getById(id);
        existing.setProduct(details.getProduct());
        existing.setCurrentStock(details.getCurrentStock());
        existing.setMaxCapacity(details.getMaxCapacity());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
