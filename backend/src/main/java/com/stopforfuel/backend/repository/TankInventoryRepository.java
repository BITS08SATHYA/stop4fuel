package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.TankInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TankInventoryRepository extends ScidRepository<TankInventory> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ti FROM TankInventory ti WHERE ti.tank.id = :tankId ORDER BY ti.date DESC, ti.id DESC LIMIT 1")
    TankInventory findTopByTankIdForUpdate(@Param("tankId") Long tankId);
    @Query("SELECT ti FROM TankInventory ti JOIN FETCH ti.tank t JOIN FETCH t.product WHERE ti.scid = :scid")
    List<TankInventory> findAllByScidWithTank(Long scid);

    @Query("SELECT ti FROM TankInventory ti JOIN FETCH ti.tank t JOIN FETCH t.product WHERE ti.date = :date")
    List<TankInventory> findByDateWithTank(LocalDate date);

    @Query("SELECT ti FROM TankInventory ti JOIN FETCH ti.tank t JOIN FETCH t.product WHERE ti.tank.id = :tankId")
    List<TankInventory> findByTankIdWithTank(Long tankId);

    List<TankInventory> findByDate(LocalDate date);
    List<TankInventory> findByTankId(Long tankId);
    TankInventory findTopByTankIdOrderByDateDescIdDesc(Long tankId);
    List<TankInventory> findByShiftId(Long shiftId);
    TankInventory findByShiftIdAndTankId(Long shiftId, Long tankId);

    @Query("SELECT ti FROM TankInventory ti JOIN FETCH ti.tank t JOIN FETCH t.product WHERE ti.scid = :scid AND ti.date BETWEEN :fromDate AND :toDate ORDER BY ti.date DESC, ti.id DESC")
    List<TankInventory> findByScidAndDateBetween(Long scid, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT ti FROM TankInventory ti JOIN FETCH ti.tank t JOIN FETCH t.product WHERE ti.scid = :scid AND ti.tank.id = :tankId AND ti.date BETWEEN :fromDate AND :toDate ORDER BY ti.date DESC, ti.id DESC")
    List<TankInventory> findByScidAndTankIdAndDateBetween(Long scid, Long tankId, LocalDate fromDate, LocalDate toDate);
}
