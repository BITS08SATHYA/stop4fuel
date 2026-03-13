package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.TankInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TankInventoryRepository extends JpaRepository<TankInventory, Long> {
    List<TankInventory> findByDate(LocalDate date);
    List<TankInventory> findByTankId(Long tankId);
    TankInventory findTopByTankIdOrderByDateDescIdDesc(Long tankId);
    List<TankInventory> findByShiftId(Long shiftId);
}
