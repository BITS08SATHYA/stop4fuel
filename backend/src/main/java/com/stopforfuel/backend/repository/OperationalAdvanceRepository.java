package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.enums.AdvanceStatus;
import com.stopforfuel.backend.enums.AdvanceType;
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
    List<OperationalAdvance> findByStatusAndScidOrderByAdvanceDateDesc(AdvanceStatus status, Long scid);
    // shiftId is already tenant-scoped via shift creation
    List<OperationalAdvance> findByShiftIdOrderByIdDesc(Long shiftId);
    List<OperationalAdvance> findAllByScidOrderByAdvanceDateDesc(Long scid);
    List<OperationalAdvance> findByAdvanceTypeAndScidOrderByAdvanceDateDesc(AdvanceType advanceType, Long scid);
    List<OperationalAdvance> findByEmployeeIdAndScidOrderByAdvanceDateDesc(Long employeeId, Long scid);
    List<OperationalAdvance> findByStatusInAndScidOrderByAdvanceDateDesc(List<AdvanceStatus> statuses, Long scid);
    List<OperationalAdvance> findByEmployeeIdAndStatusAndScid(Long employeeId, AdvanceStatus status, Long scid);

    @Query("SELECT oa FROM OperationalAdvance oa WHERE oa.scid = :scid AND oa.advanceDate BETWEEN :from AND :to ORDER BY oa.advanceDate DESC")
    List<OperationalAdvance> findByDateRange(@Param("scid") Long scid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
