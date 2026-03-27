package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.OperationalAdvance;
import org.springframework.stereotype.Repository;

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
}
