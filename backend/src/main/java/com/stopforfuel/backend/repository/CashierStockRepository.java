package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CashierStock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashierStockRepository extends ScidRepository<CashierStock> {
    Optional<CashierStock> findByProductIdAndScid(Long productId, Long scid);
    List<CashierStock> findByScid(Long scid);
}
