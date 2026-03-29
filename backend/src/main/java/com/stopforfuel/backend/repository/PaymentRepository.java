package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends ScidRepository<Payment> {

    List<Payment> findByScid(Long scid);

    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findByStatementId(Long statementId);

    List<Payment> findByInvoiceBillId(Long invoiceBillId);

    List<Payment> findByCustomerIdAndPaymentDateBetween(Long customerId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT p FROM Payment p JOIN FETCH p.customer JOIN FETCH p.paymentMode " +
           "LEFT JOIN FETCH p.statement LEFT JOIN FETCH p.invoiceBill LEFT JOIN FETCH p.receivedBy " +
           "WHERE p.shiftId = :shiftId ORDER BY p.paymentDate DESC")
    List<Payment> findByShiftIdEager(@Param("shiftId") Long shiftId);

    List<Payment> findByShiftId(Long shiftId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.shiftId = :shiftId AND p.invoiceBill IS NOT NULL")
    BigDecimal sumBillPaymentsByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.shiftId = :shiftId AND p.statement IS NOT NULL")
    BigDecimal sumStatementPaymentsByShift(@Param("shiftId") Long shiftId);

    // Paginated versions
    Page<Payment> findAllBy(Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN FETCH p.customer c JOIN FETCH p.paymentMode " +
           "LEFT JOIN FETCH p.statement LEFT JOIN FETCH p.invoiceBill LEFT JOIN FETCH p.receivedBy " +
           "LEFT JOIN c.customerCategory cc " +
           "WHERE (:categoryType IS NULL OR cc.categoryType = :categoryType)")
    Page<Payment> findWithCategoryFilter(@Param("categoryType") String categoryType, Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN FETCH p.customer c JOIN FETCH p.paymentMode " +
           "LEFT JOIN FETCH p.statement LEFT JOIN FETCH p.invoiceBill LEFT JOIN FETCH p.receivedBy " +
           "LEFT JOIN c.customerCategory cc " +
           "WHERE (:categoryType IS NULL OR cc.categoryType = :categoryType) " +
           "AND (:paidAgainst IS NULL OR " +
           "  (:paidAgainst = 'BILL' AND p.invoiceBill IS NOT NULL) OR " +
           "  (:paidAgainst = 'STATEMENT' AND p.statement IS NOT NULL)) " +
           "AND (:fromDate IS NULL OR p.paymentDate >= :fromDate) " +
           "AND (:toDate IS NULL OR p.paymentDate <= :toDate)")
    Page<Payment> findWithFilters(
            @Param("categoryType") String categoryType,
            @Param("paidAgainst") String paidAgainst,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    // Fetch all payments eagerly (for the default no-filter case)
    @Query("SELECT p FROM Payment p JOIN FETCH p.customer JOIN FETCH p.paymentMode " +
           "LEFT JOIN FETCH p.statement LEFT JOIN FETCH p.invoiceBill LEFT JOIN FETCH p.receivedBy")
    Page<Payment> findAllEager(Pageable pageable);

    // For export (no pagination)
    @Query("SELECT p FROM Payment p JOIN FETCH p.customer c JOIN FETCH p.paymentMode " +
           "LEFT JOIN FETCH p.statement LEFT JOIN FETCH p.invoiceBill LEFT JOIN FETCH p.receivedBy " +
           "LEFT JOIN c.customerCategory cc " +
           "WHERE (:categoryType IS NULL OR cc.categoryType = :categoryType) " +
           "AND (:paidAgainst IS NULL OR " +
           "  (:paidAgainst = 'BILL' AND p.invoiceBill IS NOT NULL) OR " +
           "  (:paidAgainst = 'STATEMENT' AND p.statement IS NOT NULL)) " +
           "AND (:fromDate IS NULL OR p.paymentDate >= :fromDate) " +
           "AND (:toDate IS NULL OR p.paymentDate <= :toDate) " +
           "ORDER BY p.paymentDate DESC")
    List<Payment> findAllForExport(
            @Param("categoryType") String categoryType,
            @Param("paidAgainst") String paidAgainst,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Payment> findByStatementId(Long statementId, Pageable pageable);

    Page<Payment> findByShiftId(Long shiftId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.statement.id = :statementId")
    BigDecimal sumPaymentsByStatementId(@Param("statementId") Long statementId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.invoiceBill.id = :invoiceBillId")
    BigDecimal sumPaymentsByInvoiceBillId(@Param("invoiceBillId") Long invoiceBillId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.customer.id = :customerId AND p.paymentDate < :beforeDate")
    BigDecimal sumPaymentsByCustomerBefore(@Param("customerId") Long customerId, @Param("beforeDate") LocalDateTime beforeDate);

    // Sum all payments by a customer (total ever paid)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.customer.id = :customerId")
    BigDecimal sumAllPaymentsByCustomer(@Param("customerId") Long customerId);

    // Daily payment aggregation for dashboard analytics
    @Query("SELECT CAST(p.paymentDate AS LocalDate), COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p WHERE p.paymentDate >= :fromDate AND p.paymentDate <= :toDate " +
           "GROUP BY CAST(p.paymentDate AS LocalDate) ORDER BY CAST(p.paymentDate AS LocalDate)")
    List<Object[]> getDailyPaymentStats(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Payment mode breakdown
    @Query("SELECT pm.modeName, COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p JOIN p.paymentMode pm " +
           "WHERE p.paymentDate >= :fromDate AND p.paymentDate <= :toDate " +
           "GROUP BY pm.modeName ORDER BY SUM(p.amount) DESC")
    List<Object[]> getPaymentModeBreakdown(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Top paying customers
    @Query("SELECT c.name, COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p JOIN p.customer c " +
           "WHERE p.paymentDate >= :fromDate AND p.paymentDate <= :toDate " +
           "GROUP BY c.id, c.name ORDER BY SUM(p.amount) DESC")
    List<Object[]> getTopPayingCustomers(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Total collected in date range
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentDate >= :fromDate AND p.paymentDate <= :toDate")
    BigDecimal sumPaymentsInDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    // Count payments in date range
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.paymentDate >= :fromDate AND p.paymentDate <= :toDate")
    long countPaymentsInDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);
}
