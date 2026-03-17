package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.InvoiceBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceBillRepository extends JpaRepository<InvoiceBill, Long> {
    List<InvoiceBill> findByScid(Long scid);
    List<InvoiceBill> findByShiftId(Long shiftId);
    List<InvoiceBill> findByBillType(String billType);
    List<InvoiceBill> findByStatementId(Long statementId);

    // Find credit bills for a customer in a date range that are not yet linked to a statement
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBills(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Find unlinked credit bills filtered by vehicle
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "AND ib.vehicle.id = :vehicleId " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByVehicle(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("vehicleId") Long vehicleId);

    // Find unlinked credit bills containing a specific product
    @Query("SELECT DISTINCT ib FROM InvoiceBill ib JOIN ib.products ip " +
           "WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "AND ip.product.id = :productId " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByProduct(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("productId") Long productId);

    // Find unlinked credit bills by vehicle AND product
    @Query("SELECT DISTINCT ib FROM InvoiceBill ib JOIN ib.products ip " +
           "WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "AND ib.vehicle.id = :vehicleId " +
           "AND ip.product.id = :productId " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByVehicleAndProduct(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("vehicleId") Long vehicleId,
            @Param("productId") Long productId);

    // Find specific unlinked credit bills by IDs (for bill-wise selection)
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.id IN :billIds " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByIds(
            @Param("billIds") List<Long> billIds);

    // Sum of all credit bills for a customer before a date (for opening balance)
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.customer.id = :customerId AND ib.billType = 'CREDIT' AND ib.date < :beforeDate")
    BigDecimal sumCreditBillsByCustomerBefore(
            @Param("customerId") Long customerId,
            @Param("beforeDate") LocalDateTime beforeDate);

    List<InvoiceBill> findByCustomerIdAndPaymentStatus(Long customerId, String paymentStatus);

    List<InvoiceBill> findByBillTypeAndPaymentStatus(String billType, String paymentStatus);

    // Sum of all credit bills for a customer (total credit ever billed)
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.customer.id = :customerId AND ib.billType = 'CREDIT'")
    BigDecimal sumAllCreditBillsByCustomer(@Param("customerId") Long customerId);

    // Check if customer has any unpaid credit bill older than a given date
    @Query("SELECT CASE WHEN COUNT(ib) > 0 THEN true ELSE false END FROM InvoiceBill ib " +
           "WHERE ib.customer.id = :customerId AND ib.billType = 'CREDIT' " +
           "AND ib.paymentStatus = 'NOT_PAID' AND ib.date < :beforeDate")
    boolean existsUnpaidCreditBillBefore(
            @Param("customerId") Long customerId,
            @Param("beforeDate") LocalDateTime beforeDate);

    // Paginated customer invoices — basic (no filters)
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdOrderByDateDesc(
            Long customerId,
            org.springframework.data.domain.Pageable pageable);

    // Paginated customer invoices — with billType filter only
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeOrderByDateDesc(
            Long customerId, String billType,
            org.springframework.data.domain.Pageable pageable);

    // Paginated customer invoices — with paymentStatus filter only
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndPaymentStatusOrderByDateDesc(
            Long customerId, String paymentStatus,
            org.springframework.data.domain.Pageable pageable);

    // Paginated customer invoices — with both billType and paymentStatus
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeAndPaymentStatusOrderByDateDesc(
            Long customerId, String billType, String paymentStatus,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndDateRange(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range + billType
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = :billType AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeAndDateRange(
            @Param("customerId") Long customerId,
            @Param("billType") String billType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range + paymentStatus
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.paymentStatus = :paymentStatus AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndPaymentStatusAndDateRange(
            @Param("customerId") Long customerId,
            @Param("paymentStatus") String paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range + billType + paymentStatus
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = :billType AND ib.paymentStatus = :paymentStatus " +
           "AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeAndPaymentStatusAndDateRange(
            @Param("customerId") Long customerId,
            @Param("billType") String billType,
            @Param("paymentStatus") String paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated filtered history query (all invoices, not customer-specific)
    @EntityGraph(attributePaths = {"products", "products.product", "products.nozzle", "customer", "vehicle"})
    @Query(value = "SELECT ib FROM InvoiceBill ib LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(ib.billNo) LIKE LOWER(CONCAT('%',:search,'%'))) "
         + "ORDER BY ib.date DESC",
         countQuery = "SELECT COUNT(ib) FROM InvoiceBill ib LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(ib.billNo) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<InvoiceBill> findAllFiltered(
            @Param("billType") String billType,
            @Param("paymentStatus") String paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("search") String search,
            Pageable pageable);

    // Product sales summary aggregation
    @Query("SELECT new com.stopforfuel.backend.dto.ProductSalesSummary(ip.product.id, p.name, SUM(ip.quantity), SUM(ip.amount), SUM(ip.grossAmount), COALESCE(SUM(ip.discountAmount),0)) "
         + "FROM InvoiceProduct ip JOIN ip.product p JOIN ip.invoiceBill ib "
         + "LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "GROUP BY ip.product.id, p.name")
    List<ProductSalesSummary> getProductSalesSummary(
            @Param("billType") String billType,
            @Param("paymentStatus") String paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);
}
