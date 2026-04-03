package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Nozzle;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NozzleRepository extends ScidRepository<Nozzle> {
    List<Nozzle> findByTankId(Long tankId);
    List<Nozzle> findByPumpId(Long pumpId);
    List<Nozzle> findByActive(boolean active);
    long countByActive(boolean active);
    List<Nozzle> findByActiveAndScid(boolean active, Long scid);
    List<Nozzle> findByTankIdAndScid(Long tankId, Long scid);
    List<Nozzle> findByPumpIdAndScid(Long pumpId, Long scid);
}
