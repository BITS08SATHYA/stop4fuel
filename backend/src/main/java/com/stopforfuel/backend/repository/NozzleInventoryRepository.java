package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.NozzleInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NozzleInventoryRepository extends ScidRepository<NozzleInventory> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ni FROM NozzleInventory ni WHERE ni.nozzle.id = :nozzleId ORDER BY ni.date DESC, ni.id DESC LIMIT 1")
    NozzleInventory findTopByNozzleIdForUpdate(@Param("nozzleId") Long nozzleId);
    List<NozzleInventory> findByDateAndScid(LocalDate date, Long scid);
    List<NozzleInventory> findByNozzleIdAndScid(Long nozzleId, Long scid);
    NozzleInventory findTopByNozzleIdAndScidOrderByDateDescIdDesc(Long nozzleId, Long scid);
    List<NozzleInventory> findByShiftId(Long shiftId);
    List<NozzleInventory> findByShiftIdAndNozzleId(Long shiftId, Long nozzleId);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findAllByScidWithNozzle(Long scid);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndDateBetween(Long scid, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND ni.nozzle.id = :nozzleId AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndNozzleIdAndDateBetween(Long scid, Long nozzleId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT ni FROM NozzleInventory ni JOIN FETCH ni.nozzle n JOIN FETCH n.pump JOIN FETCH n.tank t JOIN FETCH t.product WHERE ni.scid = :scid AND t.product.id = :productId AND ni.date BETWEEN :fromDate AND :toDate ORDER BY ni.date DESC, ni.id DESC")
    List<NozzleInventory> findByScidAndProductIdAndDateBetween(Long scid, Long productId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT t.product.name, COALESCE(SUM(ni.sales), 0) FROM NozzleInventory ni JOIN ni.nozzle n JOIN n.tank t WHERE ni.scid = :scid AND ni.date BETWEEN :fromDate AND :toDate GROUP BY t.product.name")
    List<Object[]> sumSalesByProductAndDateRange(@Param("scid") Long scid, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    // Per-product fuel sales for a shift — meter-based (close - open), includes products even if no invoice line
    @Query("SELECT t.product.id, t.product.name, t.product.price, COALESCE(SUM(ni.sales), 0) " +
           "FROM NozzleInventory ni JOIN ni.nozzle n JOIN n.tank t " +
           "WHERE ni.shiftId = :shiftId " +
           "GROUP BY t.product.id, t.product.name, t.product.price")
    List<Object[]> sumFuelSalesByShift(@Param("shiftId") Long shiftId);
}
