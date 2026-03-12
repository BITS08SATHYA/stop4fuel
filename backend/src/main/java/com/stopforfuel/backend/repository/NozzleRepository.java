package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Nozzle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NozzleRepository extends JpaRepository<Nozzle, Long> {
    List<Nozzle> findByTankId(Long tankId);
    List<Nozzle> findByPumpId(Long pumpId);
    List<Nozzle> findByActive(boolean active);
}
