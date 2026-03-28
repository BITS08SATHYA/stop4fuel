package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.OperationalAdvance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperationalAdvanceRepository extends ScidRepository<OperationalAdvance> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oa FROM OperationalAdvance oa WHERE oa.id = :id")
    Optional<OperationalAdvance> findByIdForUpdate(@Param("id") Long id);
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
