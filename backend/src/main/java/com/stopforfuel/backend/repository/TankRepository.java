package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Tank;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TankRepository extends ScidRepository<Tank> {
    List<Tank> findByProductId(Long productId);
    List<Tank> findByActive(boolean active);
    long countByActive(boolean active);
    long countByActiveAndScid(boolean active, Long scid);
    List<Tank> findByActiveAndScid(boolean active, Long scid);
    List<Tank> findByProductIdAndScid(Long productId, Long scid);
}

