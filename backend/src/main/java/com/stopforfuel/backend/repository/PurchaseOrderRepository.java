package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseOrderRepository extends ScidRepository<PurchaseOrder> {
    List<PurchaseOrder> findByScidOrderByOrderDateDesc(Long scid);
    List<PurchaseOrder> findByStatusAndScid(PurchaseOrderStatus status, Long scid);
    List<PurchaseOrder> findBySupplierIdAndScid(Long supplierId, Long scid);

    @Query("SELECT p.name, COALESCE(SUM(poi.receivedQty), 0) " +
           "FROM PurchaseOrderItem poi JOIN poi.product p JOIN poi.purchaseOrder po " +
           "WHERE po.orderDate >= :fromDate AND po.orderDate <= :toDate " +
           "AND po.scid = :scid AND po.status IN ('RECEIVED', 'PARTIALLY_RECEIVED') " +
           "GROUP BY p.id, p.name")
    List<Object[]> getMtdPurchaseByProduct(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("scid") Long scid);
}
