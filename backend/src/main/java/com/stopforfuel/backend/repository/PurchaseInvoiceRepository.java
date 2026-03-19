package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    List<PurchaseInvoice> findByScidOrderByInvoiceDateDesc(Long scid);
    List<PurchaseInvoice> findByStatusAndScid(String status, Long scid);
    List<PurchaseInvoice> findBySupplierIdAndScid(Long supplierId, Long scid);
    List<PurchaseInvoice> findByInvoiceTypeAndScid(String invoiceType, Long scid);
}
