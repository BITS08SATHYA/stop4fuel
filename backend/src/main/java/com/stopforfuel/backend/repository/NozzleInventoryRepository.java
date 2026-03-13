package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.NozzleInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NozzleInventoryRepository extends JpaRepository<NozzleInventory, Long> {
    List<NozzleInventory> findByDate(LocalDate date);
    List<NozzleInventory> findByNozzleId(Long nozzleId);
    NozzleInventory findTopByNozzleIdOrderByDateDescIdDesc(Long nozzleId);
    List<NozzleInventory> findByShiftId(Long shiftId);
}
