package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {
    List<ProductInventory> findByDate(LocalDate date);
    List<ProductInventory> findByProductId(Long productId);
    ProductInventory findTopByProductIdOrderByDateDescIdDesc(Long productId);
    List<ProductInventory> findByShiftId(Long shiftId);
    ProductInventory findByShiftIdAndProductId(Long shiftId, Long productId);
}
