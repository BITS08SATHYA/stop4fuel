package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalRequestStatus status);

    List<ApprovalRequest> findByRequestedByOrderByCreatedAtDesc(Long requestedBy);

    long countByStatus(ApprovalRequestStatus status);
}
