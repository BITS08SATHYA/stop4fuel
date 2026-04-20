package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ApprovalRequestDTO;
import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.service.ApprovalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approval-requests")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_CREATE')")
    public ApprovalRequest submit(@RequestBody SubmitRequest body) {
        return approvalRequestService.submit(body.requestType(), body.customerId(), body.payload(), body.note());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_VIEW')")
    public List<ApprovalRequestDTO> listPending() {
        return approvalRequestService.listPendingDtos();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_CREATE')")
    public List<ApprovalRequestDTO> listMine() {
        return approvalRequestService.listMineDtos();
    }

    @GetMapping("/pending/count")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_VIEW')")
    public Map<String, Long> pendingCount() {
        return Map.of("count", approvalRequestService.pendingCount());
    }

    @GetMapping("/pending/invoice/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_CREATE') or hasPermission(null, 'APPROVAL_REQUEST_VIEW')")
    public List<ApprovalRequestDTO> pendingForInvoice(@PathVariable Long invoiceBillId) {
        return approvalRequestService.listPendingInvoicePaymentDtos(invoiceBillId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_APPROVE')")
    public ApprovalRequest approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        return approvalRequestService.approve(id, body != null ? body.note() : null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission(null, 'APPROVAL_REQUEST_APPROVE')")
    public ApprovalRequest reject(@PathVariable Long id, @RequestBody ReviewRequest body) {
        return approvalRequestService.reject(id, body != null ? body.note() : null);
    }

    public record SubmitRequest(ApprovalRequestType requestType, Long customerId, Map<String, Object> payload, String note) {}

    public record ReviewRequest(String note) {}
}
