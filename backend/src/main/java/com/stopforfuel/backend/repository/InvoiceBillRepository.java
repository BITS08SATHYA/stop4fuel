package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceBillRepository extends ScidRepository<InvoiceBill> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.id = :id")
    Optional<InvoiceBill> findByIdForUpdate(@Param("id") Long id);
    List<InvoiceBill> findByScid(Long scid);
    @EntityGraph(attributePaths = {"customer", "products", "products.product", "products.nozzle"})
    List<InvoiceBill> findByShiftIdOrderByIdDesc(Long shiftId);
    List<InvoiceBill> findByBillType(BillType billType);

    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib WHERE ib.shiftId = :shiftId AND ib.billType = 'CASH'")
    BigDecimal sumCashBillsByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib WHERE ib.shiftId = :shiftId AND ib.billType = 'CREDIT'")
    BigDecimal sumCreditBillsByShift(@Param("shiftId") Long shiftId);
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    List<InvoiceBill> findByStatementId(Long statementId);

    // Find unpaid credit bills for a customer in a date range that are not yet linked to a statement
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.paymentStatus = 'NOT_PAID' " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBills(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Find unlinked unpaid credit bills filtered by vehicle
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.paymentStatus = 'NOT_PAID' " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "AND ib.vehicle.id = :vehicleId " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByVehicle(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("vehicleId") Long vehicleId);

    // Find unlinked unpaid credit bills containing a specific product
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    @Query("SELECT DISTINCT ib FROM InvoiceBill ib JOIN ib.products ip " +
           "WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.paymentStatus = 'NOT_PAID' " +
           "AND ib.date BETWEEN :fromDate AND :toDate " +
           "AND ip.product.id = :productId " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByProduct(
            @Param("customerId") Long customerId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("productId") Long productId);

    // Find unlinked unpaid credit bills by vehicle AND product
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    @Query("SELECT DISTINCT ib FROM InvoiceBill ib JOIN ib.products ip " +
           "WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.paymentStatus = 'NOT_PAID' " +
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

    // Find specific unlinked unpaid credit bills by IDs (for bill-wise selection)
    @EntityGraph(attributePaths = {"vehicle", "customer", "products", "products.product"})
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.id IN :billIds " +
           "AND ib.billType = 'CREDIT' AND ib.statement IS NULL " +
           "AND ib.paymentStatus = 'NOT_PAID' " +
           "ORDER BY ib.date ASC")
    List<InvoiceBill> findUnlinkedCreditBillsByIds(
            @Param("billIds") List<Long> billIds);

    // Sum of all credit bills for a customer before a date (for opening balance)
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.customer.id = :customerId AND ib.billType = 'CREDIT' AND ib.date < :beforeDate")
    BigDecimal sumCreditBillsByCustomerBefore(
            @Param("customerId") Long customerId,
            @Param("beforeDate") LocalDateTime beforeDate);

    List<InvoiceBill> findByCustomerIdAndPaymentStatus(Long customerId, PaymentStatus paymentStatus);

    List<InvoiceBill> findByBillTypeAndPaymentStatus(BillType billType, PaymentStatus paymentStatus);

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
            Long customerId, BillType billType,
            org.springframework.data.domain.Pageable pageable);

    // Paginated customer invoices — with paymentStatus filter only
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndPaymentStatusOrderByDateDesc(
            Long customerId, PaymentStatus paymentStatus,
            org.springframework.data.domain.Pageable pageable);

    // Paginated customer invoices — with both billType and paymentStatus
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeAndPaymentStatusOrderByDateDesc(
            Long customerId, BillType billType, PaymentStatus paymentStatus,
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
            @Param("billType") BillType billType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range + paymentStatus
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.paymentStatus = :paymentStatus AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndPaymentStatusAndDateRange(
            @Param("customerId") Long customerId,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated with date range + billType + paymentStatus
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = :billType AND ib.paymentStatus = :paymentStatus " +
           "AND ib.date >= :fromDate AND ib.date <= :toDate ORDER BY ib.date DESC")
    org.springframework.data.domain.Page<InvoiceBill> findByCustomerIdAndBillTypeAndPaymentStatusAndDateRange(
            @Param("customerId") Long customerId,
            @Param("billType") BillType billType,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            org.springframework.data.domain.Pageable pageable);

    // Paginated filtered history query (all invoices, not customer-specific)
    // Note: no JOIN FETCH on collections (products) — causes Hibernate to load ALL rows in memory with pagination
    @EntityGraph(attributePaths = {"customer", "vehicle", "customer.customerCategory"})
    @Query(value = "SELECT ib FROM InvoiceBill ib LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v "
         + "LEFT JOIN c.customerCategory cc WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND (:categoryType IS NULL OR cc.categoryType = :categoryType) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "AND (:search = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(ib.billNo) LIKE LOWER(CONCAT('%',:search,'%'))) "
         + "ORDER BY ib.date DESC",
         countQuery = "SELECT COUNT(ib) FROM InvoiceBill ib LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v "
         + "LEFT JOIN c.customerCategory cc WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND (:categoryType IS NULL OR cc.categoryType = :categoryType) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "AND (:search = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%',:search,'%')) "
         + "    OR LOWER(ib.billNo) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<InvoiceBill> findAllFiltered(
            @Param("billType") BillType billType,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("categoryType") String categoryType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("search") String search,
            Pageable pageable);

    // Daily invoice aggregation for dashboard analytics
    @Query("SELECT CAST(ib.date AS LocalDate), ib.billType, COUNT(ib), COALESCE(SUM(ib.netAmount), 0) " +
           "FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY CAST(ib.date AS LocalDate), ib.billType ORDER BY CAST(ib.date AS LocalDate)")
    List<Object[]> getDailyInvoiceStats(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Payment mode distribution for cash invoices
    @Query("SELECT ib.paymentMode, COUNT(ib), COALESCE(SUM(ib.netAmount), 0) " +
           "FROM InvoiceBill ib WHERE ib.billType = 'CASH' AND ib.paymentMode IS NOT NULL " +
           "AND ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY ib.paymentMode")
    List<Object[]> getPaymentModeDistribution(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Top customers by revenue
    @Query("SELECT c.name, COUNT(ib), COALESCE(SUM(ib.netAmount), 0) " +
           "FROM InvoiceBill ib JOIN ib.customer c " +
           "WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY c.id, c.name ORDER BY SUM(ib.netAmount) DESC")
    List<Object[]> getTopCustomersByRevenue(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Hourly distribution
    @Query("SELECT EXTRACT(HOUR FROM ib.date), COUNT(ib) " +
           "FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY EXTRACT(HOUR FROM ib.date) ORDER BY EXTRACT(HOUR FROM ib.date)")
    List<Object[]> getHourlyDistribution(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Aggregate credit outstanding: total unpaid credit amount
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus = 'NOT_PAID'")
    BigDecimal sumUnpaidCreditAmount();

    // Count distinct customers with unpaid credit
    @Query("SELECT COUNT(DISTINCT ib.customer.id) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus = 'NOT_PAID'")
    long countCustomersWithUnpaidCredit();

    // Aging buckets for unpaid credit bills (native query for PostgreSQL date math)
    @Query(value = "SELECT " +
           "COALESCE(SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 <= 30 THEN ib.net_amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 > 30 AND EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 <= 60 THEN ib.net_amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 > 60 AND EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 <= 90 THEN ib.net_amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - ib.bill_date)) / 86400 > 90 THEN ib.net_amount ELSE 0 END), 0) " +
           "FROM invoice_bill ib WHERE ib.bill_type = 'CREDIT' AND ib.payment_status = 'NOT_PAID'",
           nativeQuery = true)
    Object[] getUnpaidCreditAgingBuckets();

    // Invoice count summary
    @Query("SELECT ib.billType, ib.paymentStatus, COUNT(ib), COALESCE(SUM(ib.netAmount), 0) " +
           "FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY ib.billType, ib.paymentStatus")
    List<Object[]> getInvoiceSummary(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Product sales summary aggregation
    @Query("SELECT new com.stopforfuel.backend.dto.ProductSalesSummary(ip.product.id, p.name, SUM(ip.quantity), SUM(ip.amount), SUM(ip.grossAmount), COALESCE(SUM(ip.discountAmount),0)) "
         + "FROM InvoiceProduct ip JOIN ip.product p JOIN ip.invoiceBill ib "
         + "LEFT JOIN ib.customer c LEFT JOIN ib.vehicle v LEFT JOIN c.customerCategory cc WHERE "
         + "(:billType IS NULL OR ib.billType = :billType) "
         + "AND (:paymentStatus IS NULL OR ib.paymentStatus = :paymentStatus) "
         + "AND (:categoryType IS NULL OR cc.categoryType = :categoryType) "
         + "AND ib.date >= :fromDate "
         + "AND ib.date <= :toDate "
         + "GROUP BY ip.product.id, p.name")
    List<ProductSalesSummary> getProductSalesSummary(
            @Param("billType") BillType billType,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("categoryType") String categoryType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Count unpaid bills for a vehicle under a specific customer
    long countByVehicleIdAndCustomerIdAndPaymentStatus(Long vehicleId, Long customerId, PaymentStatus paymentStatus);

    // Dashboard: invoices in date range with products eagerly loaded
    @EntityGraph(attributePaths = {"customer", "products", "products.product"})
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate")
    List<InvoiceBill> findByDateBetween(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: today's revenue
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate")
    BigDecimal sumRevenueByDateRange(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: today's invoice count
    @Query("SELECT COUNT(ib) FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate")
    long countByDateRange(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: today's cash invoice count
    @Query("SELECT COUNT(ib) FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate AND ib.billType = 'CASH'")
    long countCashByDateRange(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: today's credit invoice count
    @Query("SELECT COUNT(ib) FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate AND ib.billType = 'CREDIT'")
    long countCreditByDateRange(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: today's fuel volume
    @Query("SELECT COALESCE(SUM(ip.quantity), 0) FROM InvoiceProduct ip " +
           "JOIN ip.invoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate")
    BigDecimal sumFuelVolumeByDateRange(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: daily revenue for last N days
    @Query("SELECT CAST(ib.date AS LocalDate), COALESCE(SUM(ib.netAmount), 0), COUNT(ib) " +
           "FROM InvoiceBill ib WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY CAST(ib.date AS LocalDate) ORDER BY CAST(ib.date AS LocalDate)")
    List<Object[]> getDailyRevenueSummary(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Dashboard aggregate: product sales for today
    @Query("SELECT p.name, COALESCE(SUM(ip.quantity), 0), COALESCE(SUM(ip.amount), 0) " +
           "FROM InvoiceProduct ip JOIN ip.product p JOIN ip.invoiceBill ib " +
           "WHERE ib.date >= :fromDate AND ib.date <= :toDate " +
           "GROUP BY p.id, p.name")
    List<Object[]> getProductSalesToday(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Credit overview aggregates
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus != 'PAID'")
    BigDecimal sumTotalOutstanding();

    @Query("SELECT COUNT(DISTINCT ib.customer.id) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus != 'PAID'")
    long countCreditCustomersWithOutstanding();

    // Credit aging — separate queries per bucket (safe, no Object[] casting)
    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus != 'PAID' AND ib.date >= :fromDate")
    BigDecimal sumOutstandingAfter(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus != 'PAID' AND ib.date >= :fromDate AND ib.date < :toDate")
    BigDecimal sumOutstandingBetween(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("SELECT COALESCE(SUM(ib.netAmount), 0) FROM InvoiceBill ib " +
           "WHERE ib.billType = 'CREDIT' AND ib.paymentStatus != 'PAID' AND ib.date < :beforeDate")
    BigDecimal sumOutstandingBefore(@Param("beforeDate") LocalDateTime beforeDate);

    // Dashboard: recent invoices (top N by date desc) — full entity
    @EntityGraph(attributePaths = {"customer", "products", "products.product"})
    @Query("SELECT ib FROM InvoiceBill ib WHERE ib.date IS NOT NULL ORDER BY ib.date DESC")
    List<InvoiceBill> findRecentInvoices(Pageable pageable);

    // Dashboard: recent invoices lightweight (no entity graph, just scalars)
    @Query("SELECT ib.id, ib.date, c.name, ib.billType, ib.netAmount, ib.paymentStatus " +
           "FROM InvoiceBill ib LEFT JOIN ib.customer c WHERE ib.date IS NOT NULL ORDER BY ib.date DESC")
    List<Object[]> findRecentInvoicesLight(Pageable pageable);

    // Oldest unpaid credit bill date for a customer (for aging calculation)
    @Query("SELECT MIN(ib.date) FROM InvoiceBill ib WHERE ib.customer.id = :customerId " +
           "AND ib.billType = 'CREDIT' AND ib.paymentStatus = 'NOT_PAID'")
    Optional<LocalDateTime> findOldestUnpaidBillDate(@Param("customerId") Long customerId);

    // Operational advance linked invoices
    List<InvoiceBill> findByOperationalAdvanceId(Long operationalAdvanceId);

    // Unassigned invoices in a shift (available for advance assignment)
    List<InvoiceBill> findByShiftIdAndOperationalAdvanceIsNull(Long shiftId);
}
