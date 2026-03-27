package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.OperationalAdvance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OperationalAdvanceRepository extends ScidRepository<OperationalAdvance> {
    List<OperationalAdvance> findByStatusOrderByAdvanceDateDesc(String status);
    List<OperationalAdvance> findByShiftIdOrderByAdvanceDateDesc(Long shiftId);
    List<OperationalAdvance> findAllByOrderByAdvanceDateDesc();
    List<OperationalAdvance> findByAdvanceTypeOrderByAdvanceDateDesc(String advanceType);
    List<OperationalAdvance> findByEmployeeIdOrderByAdvanceDateDesc(Long employeeId);
    List<OperationalAdvance> findByStatusInOrderByAdvanceDateDesc(List<String> statuses);
    List<OperationalAdvance> findByEmployeeIdAndStatus(Long employeeId, String status);

    @Query("SELECT oa FROM OperationalAdvance oa WHERE oa.scid = :scid AND oa.advanceDate BETWEEN :from AND :to ORDER BY oa.advanceDate DESC")
    List<OperationalAdvance> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
