package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private final ProductInventoryRepository repository;
    private final ProductRepository productRepository;
    private final ShiftService shiftService;

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
        if (inventory.getShiftId() == null) {
            Shift activeShift = shiftService.getActiveShift();
            if (activeShift != null) {
                inventory.setShiftId(activeShift.getId());
            }
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

    /**
     * Auto-create ProductInventory records for ALL active products when a shift opens.
     * OpenStock carries forward from the previous record's closeStock.
     */
    public void autoCreateForShift(Shift shift) {
        LocalDate today = LocalDate.now();
        List<Product> activeProducts = productRepository.findByActive(true);

        // Find existing records for this shift to ensure idempotency
        List<ProductInventory> existingRecords = repository.findByShiftId(shift.getId());
        Set<Long> existingProductIds = existingRecords.stream()
                .map(pi -> pi.getProduct().getId())
                .collect(Collectors.toSet());

        for (Product product : activeProducts) {
            if (existingProductIds.contains(product.getId())) {
                continue; // Already created for this shift
            }

            ProductInventory prev = repository.findTopByProductIdOrderByDateDescIdDesc(product.getId());
            double openStock = (prev != null && prev.getCloseStock() != null) ? prev.getCloseStock() : 0.0;

            ProductInventory inv = new ProductInventory();
            inv.setDate(today);
            inv.setProduct(product);
            inv.setOpenStock(openStock);
            inv.setIncomeStock(0.0);
            inv.setTotalStock(openStock);
            inv.setCloseStock(openStock);
            inv.setSales(0.0);
            inv.setRate(product.getPrice());
            inv.setAmount(BigDecimal.ZERO);
            inv.setShiftId(shift.getId());
            inv.setScid(shift.getScid());
            repository.save(inv);
        }
    }

    /**
     * Finalize rate and amount on all ProductInventory records when a shift closes.
     * Sales were already updated by deductInventory during the shift.
     */
    public void finalizeForShift(Long shiftId) {
        List<ProductInventory> records = repository.findByShiftId(shiftId);
        for (ProductInventory record : records) {
            BigDecimal rate = record.getProduct().getPrice();
            record.setRate(rate);
            double sales = record.getSales() != null ? record.getSales() : 0.0;
            record.setAmount(rate != null ? rate.multiply(BigDecimal.valueOf(sales)) : BigDecimal.ZERO);
            repository.save(record);
        }
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
