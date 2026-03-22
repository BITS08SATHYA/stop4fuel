package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Statement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByScid(Long scid);

    List<Statement> findByCustomerIdAndStatus(Long customerId, String status);

    List<Statement> findByCustomerId(Long customerId);

    List<Statement> findByStatus(String status);

    // Paginated versions
    Page<Statement> findAllBy(Pageable pageable);

    Page<Statement> findByCustomerId(Long customerId, Pageable pageable);

    Page<Statement> findByStatus(String status, Pageable pageable);

    Page<Statement> findByCustomerIdAndStatus(Long customerId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT s FROM Statement s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:customerCategory IS NULL OR s.customer.customerCategory = :customerCategory) AND " +
           "(CAST(:fromDate AS date) IS NULL OR s.fromDate >= :fromDate) AND " +
           "(CAST(:toDate AS date) IS NULL OR s.toDate <= :toDate)")
    Page<Statement> findWithFilters(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("customerCategory") String customerCategory,
            @org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") LocalDate toDate,
            Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT s FROM Statement s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:customerCategory IS NULL OR s.customer.customerCategory = :customerCategory) AND " +
           "(CAST(:fromDate AS date) IS NULL OR s.fromDate >= :fromDate) AND " +
           "(CAST(:toDate AS date) IS NULL OR s.toDate <= :toDate) AND " +
           "(LOWER(s.customer.name) LIKE LOWER(CONCAT('%', :search, '%')) OR s.statementNo LIKE CONCAT('%', :search, '%'))")
    Page<Statement> findWithFiltersAndSearch(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("customerCategory") String customerCategory,
            @org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") LocalDate toDate,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable);

    Optional<Statement> findByStatementNo(String statementNo);

    List<Statement> findByCustomerIdAndStatementDateBetween(Long customerId, LocalDate from, LocalDate to);

    // Stats aggregation queries
    @Query("SELECT COUNT(s) FROM Statement s WHERE s.status = 'PAID'")
    long countPaid();

    @Query("SELECT COALESCE(SUM(s.balanceAmount), 0) FROM Statement s WHERE s.status = 'NOT_PAID'")
    BigDecimal sumUnpaidBalance();

    @Query("SELECT COALESCE(SUM(s.netAmount), 0) FROM Statement s")
    BigDecimal sumNetAmount();

    @Query("SELECT COALESCE(SUM(s.receivedAmount), 0) FROM Statement s")
    BigDecimal sumReceivedAmount();

    @Query("SELECT COALESCE(AVG(s.netAmount), 0) FROM Statement s")
    BigDecimal avgNetAmount();

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end")
    long countByStatementDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end);

    @Query("SELECT COUNT(s) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end AND s.status = 'PAID'")
    long countPaidByStatementDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(s.netAmount), 0) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end")
    BigDecimal sumNetAmountByDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(s.receivedAmount), 0) FROM Statement s WHERE s.statementDate >= :start AND s.statementDate < :end")
    BigDecimal sumReceivedAmountByDateRange(
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end);
}
