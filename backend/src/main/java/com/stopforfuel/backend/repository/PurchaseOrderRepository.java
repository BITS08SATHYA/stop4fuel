package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.enums.PurchaseOrderStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends ScidRepository<PurchaseOrder> {
    List<PurchaseOrder> findByScidOrderByOrderDateDesc(Long scid);
    List<PurchaseOrder> findByStatusAndScid(PurchaseOrderStatus status, Long scid);
    List<PurchaseOrder> findBySupplierIdAndScid(Long supplierId, Long scid);
}
