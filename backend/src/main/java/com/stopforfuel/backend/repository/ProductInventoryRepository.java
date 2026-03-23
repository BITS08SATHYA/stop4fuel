package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ProductInventory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductInventoryRepository extends ScidRepository<ProductInventory> {
    List<ProductInventory> findByDate(LocalDate date);
    List<ProductInventory> findByProductId(Long productId);
    ProductInventory findTopByProductIdOrderByDateDescIdDesc(Long productId);
    List<ProductInventory> findByShiftId(Long shiftId);
    ProductInventory findByShiftIdAndProductId(Long shiftId, Long productId);
}
