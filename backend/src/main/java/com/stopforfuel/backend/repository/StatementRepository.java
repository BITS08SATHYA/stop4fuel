package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Statement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatementRepository extends ScidRepository<Statement> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Statement s WHERE s.id = :id")
    Optional<Statement> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Statement> findByScid(Long scid);

    List<Statement> findByCustomerIdAndStatus(Long customerId, String status);

    List<Statement> findByCustomerId(Long customerId);

    List<Statement> findByStatusAndScid(String status, Long scid);

    // Paginated versions
    Page<Statement> findAllByScid(Long scid, Pageable pageable);

    Page<Statement> findByCustomerId(Long customerId, Pageable pageable);

    Page<Statement> findByStatus(String status, Pageable pageable);

    Page<Statement> findByCustomerIdAndStatus(Long customerId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT s FROM Statement s LEFT JOIN s.customer.customerCategory cc WHERE s.scid = :scid AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:categoryType IS NULL OR cc.categoryType = :categoryType) AND " +
           "(CAST(:fromDate AS date) IS NULL OR s.statementDate >= :fromDate) AND " +
           "(CAST(:toDate AS date) IS NULL OR s.statementDate <= :toDate)")
    Page<Statement> findWithFilters(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("categoryType") String categoryType,
            @org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") LocalDate toDate,
            @org.springframework.data.repository.query.Param("scid") Long scid,
            Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT s FROM Statement s LEFT JOIN s.customer.customerCategory cc WHERE s.scid = :scid AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:categoryType IS NULL OR cc.categoryType = :categoryType) AND " +
           "(CAST(:fromDate AS date) IS NULL OR s.statementDate >= :fromDate) AND " +
           "(CAST(:toDate AS date) IS NULL OR s.statementDate <= :toDate) AND " +
           "(LOWER(s.customer.name) LIKE LOWER(CONCAT('%', :search, '%')) OR s.statementNo LIKE CONCAT('%', :search, '%'))")
    Page<Statement> findWithFiltersAndSearch(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("categoryType") String categoryType,
            @org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") LocalDate toDate,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("scid") Long scid,
            Pageable pageable);

    @Query("SELECT s FROM Statement s WHERE s.customer.id = :customerId AND s.status <> 'DRAFT' ORDER BY s.statementDate DESC")
    List<Statement> findRecentByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    Optional<Statement> findByStatementNo(String statementNo);

    boolean existsByCustomerIdAndFromDateAndToDateAndScid(Long customerId, LocalDate fromDate, LocalDate toDate, Long scid);

    List<Statement> findByCustomerIdAndStatementDateBetween(Long customerId, LocalDate from, LocalDate to);

    @Query("SELECT s FROM Statement s JOIN FETCH s.customer WHERE s.scid = :scid AND s.statementDate BETWEEN :fromDate AND :toDate ORDER BY s.statementDate DESC, s.id DESC")
    List<Statement> findByDateRangeAndScid(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("scid") Long scid);

    @Query("SELECT s FROM Statement s JOIN FETCH s.customer WHERE s.scid = :scid AND s.statementDate BETWEEN :fromDate AND :toDate AND s.status = :status ORDER BY s.statementDate DESC, s.id DESC")
    List<Statement> findByDateRangeAndStatusAndScid(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, @Param("status") String status, @Param("scid") Long scid);

    // Stats aggregation queries
    @Query("SELECT COUNT(s) FROM Statement s WHERE s.status = 'PAID' AND s.scid = :scid")
    long countPaid(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.status = 'NOT_PAID' AND s.scid = :scid")
    long countUnpaid(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.scid = :scid")
    long countAll(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(s.balanceAmount), 0) FROM Statement s WHERE s.status = 'NOT_PAID' AND s.scid = :scid")
    BigDecimal sumUnpaidBalance(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(s.netAmount), 0) FROM Statement s WHERE s.scid = :scid")
    BigDecimal sumNetAmount(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(s.receivedAmount), 0) FROM Statement s WHERE s.scid = :scid")
    BigDecimal sumReceivedAmount(@org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(AVG(s.netAmount), 0) FROM Statement s WHERE s.scid = :scid")
    BigDecimal avgNetAmount(@org.springframework.data.repository.query.Param("scid") Long scid);

    // Oldest outstanding statement date for a customer (anything not fully paid / not a draft)
    @Query("SELECT MIN(s.statementDate) FROM Statement s WHERE s.customer.id = :customerId " +
           "AND s.status <> 'PAID' AND s.status <> 'DRAFT'")
    LocalDate findOldestUnpaidStatementDate(@Param("customerId") Long customerId);

    // Count outstanding statements for a customer
    @Query("SELECT COUNT(s) FROM Statement s WHERE s.customer.id = :customerId " +
           "AND s.status <> 'PAID' AND s.status <> 'DRAFT'")
    long countUnpaidStatements(@Param("customerId") Long customerId);

    boolean existsByCustomerId(Long customerId);

    // Outstanding statements (unpaid or partial) with balance < :maxBalance, used by
    // Outstanding Explorer. Ordered by smallest balance first.
    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT s FROM Statement s WHERE s.scid = :scid AND s.status <> 'PAID' AND s.status <> 'DRAFT' "
            + "AND s.statementDate >= :fromDate AND s.statementDate <= :toDate "
            + "AND (:search = '' OR LOWER(s.customer.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "    OR LOWER(s.statementNo) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "AND s.balanceAmount < :maxBalance "
            + "ORDER BY s.balanceAmount ASC, s.statementDate DESC")
    Page<Statement> findOutstanding(
            @org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") LocalDate toDate,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("maxBalance") BigDecimal maxBalance,
            @org.springframework.data.repository.query.Param("scid") Long scid,
            Pageable pageable);

    // Sum outstanding statement balance for a customer
    @Query("SELECT COALESCE(SUM(s.balanceAmount), 0) FROM Statement s WHERE s.customer.id = :customerId " +
           "AND s.status <> 'PAID' AND s.status <> 'DRAFT'")
    BigDecimal sumUnpaidStatementBalance(@Param("customerId") Long customerId);

    // All unpaid statements across all customers, customer fetched
    @Query("SELECT s FROM Statement s JOIN FETCH s.customer c WHERE s.status <> 'PAID' AND s.status <> 'DRAFT' " +
           "ORDER BY c.name ASC, s.statementDate ASC, s.id ASC")
    List<Statement> findAllUnpaidWithCustomer();

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end AND s.scid = :scid")
    long countByStatementDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end,
            @org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end AND s.status = 'PAID' AND s.scid = :scid")
    long countPaidByStatementDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end,
            @org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(s.netAmount), 0) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end AND s.scid = :scid")
    BigDecimal sumNetAmountByDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end,
            @org.springframework.data.repository.query.Param("scid") Long scid);

    @Query("SELECT COALESCE(SUM(s.receivedAmount), 0) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end AND s.scid = :scid")
    BigDecimal sumReceivedAmountByDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end,
            @org.springframework.data.repository.query.Param("scid") Long scid);
}
