package com.stopforfuel.backend.entity;

import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests", indexes = {
        @Index(name = "idx_approval_req_status", columnList = "status"),
        @Index(name = "idx_approval_req_requested_by", columnList = "requested_by"),
        @Index(name = "idx_approval_req_customer_id", columnList = "customer_id")
})
@Getter
@Setter
public class ApprovalRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private ApprovalRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApprovalRequestStatus status = ApprovalRequestStatus.PENDING;

    @Column(name = "customer_id")
    private Long customerId;

    /**
     * Type-specific data as a JSON string.
     * ADD_VEHICLE: { vehicleNumber, vehicleTypeId, preferredProductId, maxCapacity, maxLitersPerMonth }
     * UNBLOCK_CUSTOMER: { reason }
     * RAISE_CREDIT_LIMIT: { creditLimitAmount, creditLimitLiters }
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "request_note", columnDefinition = "TEXT")
    private String requestNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
