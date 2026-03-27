package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.EAdvance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EAdvanceRepository extends ScidRepository<EAdvance> {
    List<EAdvance> findByShiftIdOrderByTransactionDateDesc(Long shiftId);
    List<EAdvance> findByAdvanceTypeOrderByTransactionDateDesc(String advanceType);
    List<EAdvance> findByShiftIdAndAdvanceType(Long shiftId, String advanceType);
    List<EAdvance> findAllByOrderByTransactionDateDesc();

    @Query("SELECT e FROM EAdvance e WHERE e.scid = :scid AND e.transactionDate BETWEEN :from AND :to ORDER BY e.transactionDate DESC")
    List<EAdvance> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e FROM EAdvance e WHERE e.scid = :scid AND e.transactionDate BETWEEN :from AND :to AND UPPER(e.advanceType) = UPPER(:type) ORDER BY e.transactionDate DESC")
    List<EAdvance> findByDateRangeAndType(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("type") String type);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EAdvance e WHERE e.shiftId = :shiftId")
    BigDecimal sumAllByShift(@Param("shiftId") Long shiftId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EAdvance e WHERE e.shiftId = :shiftId AND e.advanceType = :type")
    BigDecimal sumByShiftAndType(@Param("shiftId") Long shiftId, @Param("type") String type);
}
