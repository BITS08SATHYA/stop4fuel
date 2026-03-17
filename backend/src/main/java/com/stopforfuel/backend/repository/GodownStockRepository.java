package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.GodownStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GodownStockRepository extends JpaRepository<GodownStock, Long> {
    Optional<GodownStock> findByProductIdAndScid(Long productId, Long scid);
    List<GodownStock> findByScid(Long scid);

    @Query("SELECT g FROM GodownStock g WHERE g.scid = :scid AND g.currentStock <= g.reorderLevel AND g.reorderLevel > 0")
    List<GodownStock> findLowStockItems(Long scid);
}
