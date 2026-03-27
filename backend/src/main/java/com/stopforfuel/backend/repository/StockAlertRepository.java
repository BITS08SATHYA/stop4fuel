package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.StockAlert;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockAlertRepository extends ScidRepository<StockAlert> {
    List<StockAlert> findByActiveAndScid(boolean active, Long scid);
    List<StockAlert> findByTankIdAndActiveAndScid(Long tankId, boolean active, Long scid);
    Optional<StockAlert> findFirstByTankIdAndActiveAndScid(Long tankId, boolean active, Long scid);
}
