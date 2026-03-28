package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.GodownStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GodownStockRepository extends ScidRepository<GodownStock> {
    Optional<GodownStock> findByProductIdAndScid(Long productId, Long scid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GodownStock g WHERE g.product.id = :productId AND g.scid = :scid")
    Optional<GodownStock> findByProductIdAndScidForUpdate(@Param("productId") Long productId, @Param("scid") Long scid);
    List<GodownStock> findByScid(Long scid);

    @Query("SELECT g FROM GodownStock g WHERE g.scid = :scid AND g.currentStock <= g.reorderLevel AND g.reorderLevel > 0")
    List<GodownStock> findLowStockItems(Long scid);
}
