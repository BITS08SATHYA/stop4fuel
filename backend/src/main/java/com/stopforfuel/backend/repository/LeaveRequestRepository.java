package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.LeaveRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends ScidRepository<LeaveRequest> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<LeaveRequest> findByScidAndStatusOrderByCreatedAtDesc(Long scid, String status);
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
    List<LeaveRequest> findAllByScidOrderByCreatedAtDesc(Long scid);
}
