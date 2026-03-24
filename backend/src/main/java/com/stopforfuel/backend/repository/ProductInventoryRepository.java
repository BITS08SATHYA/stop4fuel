package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ProductInventory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductInventoryRepository extends ScidRepository<ProductInventory> {

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
    ProductInventory findByShiftIdAndProductId(Long shiftId, Long productId);
}
