package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.IncentivePayment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface IncentivePaymentRepository extends ScidRepository<IncentivePayment> {
    List<IncentivePayment> findByShiftIdOrderByPaymentDateDesc(Long shiftId);
    List<IncentivePayment> findByCustomerIdOrderByPaymentDateDesc(Long customerId);
    List<IncentivePayment> findAllByOrderByPaymentDateDesc();

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM IncentivePayment ip WHERE ip.shiftId = :shiftId")
    BigDecimal sumByShift(@Param("shiftId") Long shiftId);
}
