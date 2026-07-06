package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.IncentivePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Query("SELECT ip FROM IncentivePayment ip WHERE ip.scid = :scid AND ip.paymentDate BETWEEN :from AND :to ORDER BY ip.paymentDate ASC, ip.id ASC")
    List<IncentivePayment> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(ip.amount), 0) FROM IncentivePayment ip WHERE ip.shiftId = :shiftId")
    BigDecimal sumByShift(@Param("shiftId") Long shiftId);

    List<IncentivePayment> findByInvoiceBillId(Long invoiceBillId);

    // Day-wise totals by the shift's BUSINESS day (shift start date), so the day-wise
    // report agrees with the shift reports. payment_date is stamped now() at bill time,
    // and shifts cross midnight — after-midnight incentives belong to the previous
    // day's shift, not the next calendar day. Shiftless rows fall back to payment_date.
    @Query("""
        SELECT COALESCE(CAST(s.startTime AS LocalDate), CAST(ip.paymentDate AS LocalDate)), SUM(ip.amount)
        FROM IncentivePayment ip LEFT JOIN Shift s ON s.id = ip.shiftId
        WHERE ip.scid = :scid
          AND COALESCE(CAST(s.startTime AS LocalDate), CAST(ip.paymentDate AS LocalDate)) BETWEEN :from AND :to
        GROUP BY COALESCE(CAST(s.startTime AS LocalDate), CAST(ip.paymentDate AS LocalDate))
        ORDER BY COALESCE(CAST(s.startTime AS LocalDate), CAST(ip.paymentDate AS LocalDate))
        """)
    List<Object[]> sumByShiftBusinessDay(@Param("scid") Long scid,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
