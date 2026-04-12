package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.IncentivePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncentivePaymentRepository extends ScidRepository<IncentivePayment> {

    @Override
    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    List<IncentivePayment> findAllByScid(Long scid);

    @Override
    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    Optional<IncentivePayment> findByIdAndScid(Long id, Long scid);

    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    List<IncentivePayment> findByShiftIdOrderByIdDesc(Long shiftId);

    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    List<IncentivePayment> findByCustomerIdAndScidOrderByPaymentDateDesc(Long customerId, Long scid);

    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    List<IncentivePayment> findAllByScidOrderByPaymentDateDesc(Long scid);

    @EntityGraph(attributePaths = {"customer", "invoiceBill", "invoiceBill.customer", "statement"})
    @Query("SELECT ip FROM IncentivePayment ip WHERE ip.scid = :scid AND ip.paymentDate BETWEEN :from AND :to ORDER BY ip.paymentDate DESC")
    List<IncentivePayment> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM IncentivePayment ip WHERE ip.shiftId = :shiftId")
    BigDecimal sumByShift(@Param("shiftId") Long shiftId);

    List<IncentivePayment> findByInvoiceBillId(Long invoiceBillId);
}
