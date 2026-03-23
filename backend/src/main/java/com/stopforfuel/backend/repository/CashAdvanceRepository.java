package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CashAdvance;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CashAdvanceRepository extends ScidRepository<CashAdvance> {
    List<CashAdvance> findByStatusOrderByAdvanceDateDesc(String status);
    List<CashAdvance> findByShiftIdOrderByAdvanceDateDesc(Long shiftId);
    List<CashAdvance> findAllByOrderByAdvanceDateDesc();
    List<CashAdvance> findByAdvanceTypeOrderByAdvanceDateDesc(String advanceType);
    List<CashAdvance> findByEmployeeIdOrderByAdvanceDateDesc(Long employeeId);
    List<CashAdvance> findByStatusInOrderByAdvanceDateDesc(List<String> statuses);
}
