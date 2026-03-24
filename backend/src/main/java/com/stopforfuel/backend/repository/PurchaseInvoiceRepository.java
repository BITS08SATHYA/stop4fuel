package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseInvoiceRepository extends ScidRepository<PurchaseInvoice> {

    @Query("SELECT DISTINCT pi FROM PurchaseInvoice pi LEFT JOIN FETCH pi.supplier LEFT JOIN FETCH pi.items i LEFT JOIN FETCH i.product WHERE pi.scid = :scid ORDER BY pi.invoiceDate DESC")
    List<PurchaseInvoice> findAllWithDetails(Long scid);

    @Query("SELECT DISTINCT pi FROM PurchaseInvoice pi LEFT JOIN FETCH pi.supplier LEFT JOIN FETCH pi.items i LEFT JOIN FETCH i.product WHERE pi.status = :status AND pi.scid = :scid ORDER BY pi.invoiceDate DESC")
    List<PurchaseInvoice> findByStatusWithDetails(String status, Long scid);

    @Query("SELECT DISTINCT pi FROM PurchaseInvoice pi LEFT JOIN FETCH pi.supplier LEFT JOIN FETCH pi.items i LEFT JOIN FETCH i.product WHERE pi.supplier.id = :supplierId AND pi.scid = :scid ORDER BY pi.invoiceDate DESC")
    List<PurchaseInvoice> findBySupplierWithDetails(Long supplierId, Long scid);

    @Query("SELECT DISTINCT pi FROM PurchaseInvoice pi LEFT JOIN FETCH pi.supplier LEFT JOIN FETCH pi.items i LEFT JOIN FETCH i.product WHERE pi.invoiceType = :invoiceType AND pi.scid = :scid ORDER BY pi.invoiceDate DESC")
    List<PurchaseInvoice> findByTypeWithDetails(String invoiceType, Long scid);

    @Query("SELECT DISTINCT pi FROM PurchaseInvoice pi LEFT JOIN FETCH pi.supplier LEFT JOIN FETCH pi.items i LEFT JOIN FETCH i.product WHERE pi.scid = :scid AND pi.invoiceDate BETWEEN :fromDate AND :toDate ORDER BY pi.invoiceDate DESC")
    List<PurchaseInvoice> findByDateRangeWithDetails(Long scid, LocalDate fromDate, LocalDate toDate);

    // Keep originals for non-serialization use
    List<PurchaseInvoice> findByScidOrderByInvoiceDateDesc(Long scid);
    List<PurchaseInvoice> findByStatusAndScid(String status, Long scid);
    List<PurchaseInvoice> findBySupplierIdAndScid(Long supplierId, Long scid);
    List<PurchaseInvoice> findByInvoiceTypeAndScid(String invoiceType, Long scid);
}
