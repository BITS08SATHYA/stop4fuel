package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.enums.LeaveStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends ScidRepository<LeaveRequest> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status);
    List<LeaveRequest> findByScidAndStatusOrderByCreatedAtDesc(Long scid, LeaveStatus status);
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
    List<LeaveRequest> findAllByScidOrderByCreatedAtDesc(Long scid);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.status = 'APPROVED' " +
           "AND lr.fromDate <= :monthEnd AND lr.toDate >= :monthStart")
    List<LeaveRequest> findApprovedLeavesOverlappingMonth(
            @Param("employeeId") Long employeeId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);
}
