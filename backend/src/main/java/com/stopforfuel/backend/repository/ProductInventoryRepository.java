package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductInventoryRepository extends ScidRepository<ProductInventory> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM ProductInventory pi WHERE pi.product.id = :productId ORDER BY pi.date DESC, pi.id DESC LIMIT 1")
    ProductInventory findTopByProductIdForUpdate(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pi FROM ProductInventory pi WHERE pi.product.id = :productId AND pi.shiftId = :shiftId ORDER BY pi.id DESC LIMIT 1")
    ProductInventory findByProductIdAndShiftIdForUpdate(@Param("productId") Long productId, @Param("shiftId") Long shiftId);

    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.product WHERE pi.scid = :scid ORDER BY pi.date DESC, pi.id DESC")
    List<ProductInventory> findAllByScidWithProduct(Long scid);

    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.product WHERE pi.date = :date")
    List<ProductInventory> findByDateWithProduct(LocalDate date);

    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.product WHERE pi.product.id = :productId")
    List<ProductInventory> findByProductIdWithProduct(Long productId);

    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.product WHERE pi.scid = :scid AND pi.date BETWEEN :fromDate AND :toDate ORDER BY pi.date DESC, pi.id DESC")
    List<ProductInventory> findByScidAndDateBetween(Long scid, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT pi FROM ProductInventory pi JOIN FETCH pi.product WHERE pi.scid = :scid AND pi.product.id = :productId AND pi.date BETWEEN :fromDate AND :toDate ORDER BY pi.date DESC, pi.id DESC")
    List<ProductInventory> findByScidAndProductIdAndDateBetween(Long scid, Long productId, LocalDate fromDate, LocalDate toDate);

    List<ProductInventory> findByDate(LocalDate date);
    List<ProductInventory> findByProductId(Long productId);
    ProductInventory findTopByProductIdOrderByDateDescIdDesc(Long productId);
    List<ProductInventory> findByShiftId(Long shiftId);
    ProductInventory findTopByShiftIdAndProductIdOrderByIdDesc(Long shiftId, Long productId);
}
