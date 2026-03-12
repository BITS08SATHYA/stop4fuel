package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private final ProductInventoryRepository repository;

    public List<ProductInventory> getAll() {
        return repository.findAll();
    }

    public List<ProductInventory> getByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<ProductInventory> getByProductId(Long productId) {
        return repository.findByProductId(productId);
    }

    public ProductInventory getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProductInventory not found with id: " + id));
    }

    public ProductInventory save(ProductInventory inventory) {
        if (inventory.getScid() == null) {
            inventory.setScid(1L);
        }
        calculateFields(inventory);
        return repository.save(inventory);
    }

    public ProductInventory update(Long id, ProductInventory details) {
        ProductInventory existing = getById(id);
        existing.setDate(details.getDate());
        existing.setProduct(details.getProduct());
        existing.setOpenStock(details.getOpenStock());
        existing.setIncomeStock(details.getIncomeStock());
        existing.setCloseStock(details.getCloseStock());
        calculateFields(existing);
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void calculateFields(ProductInventory inventory) {
        double open = inventory.getOpenStock() != null ? inventory.getOpenStock() : 0.0;
        double income = inventory.getIncomeStock() != null ? inventory.getIncomeStock() : 0.0;
        double total = open + income;
        inventory.setTotalStock(total);

        if (inventory.getCloseStock() != null) {
            inventory.setSales(total - inventory.getCloseStock());
        }
    }
}
