package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.NozzleInventory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NozzleInventoryRepository extends ScidRepository<NozzleInventory> {
    List<NozzleInventory> findByDate(LocalDate date);
    List<NozzleInventory> findByNozzleId(Long nozzleId);
    NozzleInventory findTopByNozzleIdOrderByDateDescIdDesc(Long nozzleId);
    List<NozzleInventory> findByShiftId(Long shiftId);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findAllByScidWithNozzle(Long scid);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndDateBetween(Long scid, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND ni.nozzle.id = :nozzleId AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndNozzleIdAndDateBetween(Long scid, Long nozzleId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND t.product.id = :productId AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndProductIdAndDateBetween(Long scid, Long productId, LocalDate fromDate, LocalDate toDate);
}
