package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Pump;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PumpRepository extends ScidRepository<Pump> {
    List<Pump> findByActive(boolean active);
    long countByActive(boolean active);
    long countByActiveAndScid(boolean active, Long scid);
    List<Pump> findByActiveAndScid(boolean active, Long scid);
}
