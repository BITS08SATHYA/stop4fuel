package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CashierStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashierStockRepository extends ScidRepository<CashierStock> {
    Optional<CashierStock> findByProductIdAndScid(Long productId, Long scid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CashierStock c WHERE c.product.id = :productId AND c.scid = :scid")
    Optional<CashierStock> findByProductIdAndScidForUpdate(@Param("productId") Long productId, @Param("scid") Long scid);
    List<CashierStock> findByScid(Long scid);
}
