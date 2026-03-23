package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.LeaveBalance;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends ScidRepository<LeaveBalance> {
    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);
}
