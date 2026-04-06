package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.EAdvance;
import com.stopforfuel.backend.enums.PaymentMode;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EAdvanceRepository extends ScidRepository<EAdvance> {
    List<EAdvance> findByShiftIdOrderByIdDesc(Long shiftId);
    List<EAdvance> findByAdvanceTypeOrderByTransactionDateDesc(PaymentMode advanceType);
    List<EAdvance> findByShiftIdAndAdvanceType(Long shiftId, PaymentMode advanceType);
    List<EAdvance> findAllByOrderByTransactionDateDesc();

    @Query("SELECT e FROM EAdvance e WHERE e.scid = :scid AND e.transactionDate BETWEEN :from AND :to ORDER BY e.transactionDate DESC")
    List<EAdvance> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e FROM EAdvance e WHERE e.scid = :scid AND e.transactionDate BETWEEN :from AND :to AND e.advanceType = :type ORDER BY e.transactionDate DESC")
    List<EAdvance> findByDateRangeAndType(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("type") PaymentMode type);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EAdvance e WHERE e.shiftId = :shiftId")
    BigDecimal sumAllByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EAdvance e WHERE e.shiftId = :shiftId AND e.advanceType = :type")
    BigDecimal sumByShiftAndType(@Param("shiftId") Long shiftId, @Param("type") PaymentMode type);

    Optional<EAdvance> findByPaymentId(Long paymentId);
}
