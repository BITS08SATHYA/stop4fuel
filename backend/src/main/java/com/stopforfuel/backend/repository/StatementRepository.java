package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Query("SELECT s FROM Statement s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:customerId IS NULL OR s.customer.id = :customerId)")
    Page<Statement> findWithFilters(
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("status") String status,
            Pageable pageable);

    Optional<Statement> findByStatementNo(String statementNo);

    List<Statement> findByCustomerIdAndStatementDateBetween(Long customerId, LocalDate from, LocalDate to);
}
