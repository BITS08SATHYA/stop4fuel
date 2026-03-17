package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByScidOrderByOrderDateDesc(Long scid);
    List<PurchaseOrder> findByStatusAndScid(String status, Long scid);
    List<PurchaseOrder> findBySupplierIdAndScid(Long supplierId, Long scid);
}
