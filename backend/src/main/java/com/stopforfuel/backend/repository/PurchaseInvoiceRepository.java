package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseInvoiceRepository extends ScidRepository<PurchaseInvoice> {
    List<PurchaseInvoice> findByScidOrderByInvoiceDateDesc(Long scid);
    List<PurchaseInvoice> findByStatusAndScid(String status, Long scid);
    List<PurchaseInvoice> findBySupplierIdAndScid(Long supplierId, Long scid);
    List<PurchaseInvoice> findByInvoiceTypeAndScid(String invoiceType, Long scid);
}
