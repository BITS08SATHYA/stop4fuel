package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.IncentivePayment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncentivePaymentRepository extends ScidRepository<IncentivePayment> {
    List<IncentivePayment> findByShiftIdOrderByIdDesc(Long shiftId);
    List<IncentivePayment> findByCustomerIdOrderByPaymentDateDesc(Long customerId);
    List<IncentivePayment> findAllByOrderByPaymentDateDesc();

    @Query("SELECT ip FROM IncentivePayment ip WHERE ip.scid = :scid AND ip.paymentDate BETWEEN :from AND :to ORDER BY ip.paymentDate DESC")
    List<IncentivePayment> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM IncentivePayment ip WHERE ip.shiftId = :shiftId")
    BigDecimal sumByShift(@Param("shiftId") Long shiftId);
}
