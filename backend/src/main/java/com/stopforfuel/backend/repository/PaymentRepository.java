package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByScid(Long scid);

    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findByStatementId(Long statementId);

    List<Payment> findByInvoiceBillId(Long invoiceBillId);

    List<Payment> findByCustomerIdAndPaymentDateBetween(Long customerId, LocalDateTime from, LocalDateTime to);

    List<Payment> findByShiftId(Long shiftId);

    // Paginated versions
    Page<Payment> findAllBy(Pageable pageable);

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
}
