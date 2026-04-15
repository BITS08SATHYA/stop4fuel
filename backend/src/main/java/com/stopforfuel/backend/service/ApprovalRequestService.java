package com.stopforfuel.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.entity.VehicleType;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.ApprovalRequestRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.VehicleTypeRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final VehicleService vehicleService;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApprovalRequest submit(ApprovalRequestType type, Long customerId, Map<String, Object> payload, String note) {
        if (type == null) {
            throw new BusinessException("requestType is required");
        }
        boolean customerRequired = type == ApprovalRequestType.ADD_VEHICLE
                || type == ApprovalRequestType.UNBLOCK_CUSTOMER
                || type == ApprovalRequestType.RAISE_CREDIT_LIMIT;
        if (customerRequired && customerId == null) {
            throw new BusinessException("customerId is required for " + type);
        }
        if (customerId != null) {
            customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        }
        validatePayload(type, payload);

        ApprovalRequest req = new ApprovalRequest();
        req.setRequestType(type);
        req.setStatus(ApprovalRequestStatus.PENDING);
        req.setCustomerId(customerId);
        req.setRequestedBy(SecurityUtils.getCurrentUserId());
        req.setRequestNote(note);
        req.setPayload(writeJson(payload));
        ApprovalRequest saved = approvalRequestRepository.save(req);

        // Fire-and-forget push notification to approvers
        try {
            pushNotificationService.notifyApprovalRequestCreated(saved, null, null);
        } catch (Exception e) {
            // Never fail submission because of push delivery issues
        }
        return saved;
    }

    @Transactional
    public ApprovalRequest approve(Long id, String reviewNote) {
        ApprovalRequest req = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));
        if (req.getStatus() != ApprovalRequestStatus.PENDING) {
            throw new BusinessException("Only PENDING requests can be approved. Current: " + req.getStatus());
        }

        Map<String, Object> payload = readJson(req.getPayload());

        switch (req.getRequestType()) {
            case ADD_VEHICLE -> applyAddVehicle(req.getCustomerId(), payload);
            case UNBLOCK_CUSTOMER -> customerService.unblockCustomer(req.getCustomerId(),
                    (String) payload.getOrDefault("reason", null));
            case RAISE_CREDIT_LIMIT -> customerService.updateCreditLimits(req.getCustomerId(), payload);
            case RECORD_STATEMENT_PAYMENT -> paymentService.recordStatementPayment(
                    Long.valueOf(payload.get("statementId").toString()), buildPayment(payload));
            case RECORD_INVOICE_PAYMENT -> paymentService.recordBillPayment(
                    Long.valueOf(payload.get("invoiceBillId").toString()), buildPayment(payload));
        }

        req.setStatus(ApprovalRequestStatus.APPROVED);
        req.setReviewedBy(SecurityUtils.getCurrentUserId());
        req.setReviewNote(reviewNote);
        req.setReviewedAt(LocalDateTime.now());
        return approvalRequestRepository.save(req);
    }

    @Transactional
    public ApprovalRequest reject(Long id, String reviewNote) {
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new BusinessException("A review note is required when rejecting a request");
        }
        ApprovalRequest req = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));
        if (req.getStatus() != ApprovalRequestStatus.PENDING) {
            throw new BusinessException("Only PENDING requests can be rejected. Current: " + req.getStatus());
        }
        req.setStatus(ApprovalRequestStatus.REJECTED);
        req.setReviewedBy(SecurityUtils.getCurrentUserId());
        req.setReviewNote(reviewNote);
        req.setReviewedAt(LocalDateTime.now());
        return approvalRequestRepository.save(req);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listPending() {
        return approvalRequestRepository.findByStatusOrderByCreatedAtDesc(ApprovalRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listMine() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return List.of();
        return approvalRequestRepository.findByRequestedByOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return approvalRequestRepository.countByStatus(ApprovalRequestStatus.PENDING);
    }

    private void validatePayload(ApprovalRequestType type, Map<String, Object> payload) {
        Map<String, Object> p = payload != null ? payload : new HashMap<>();
        switch (type) {
            case ADD_VEHICLE -> {
                if (!p.containsKey("vehicleNumber") || p.get("vehicleNumber") == null
                        || p.get("vehicleNumber").toString().isBlank()) {
                    throw new BusinessException("vehicleNumber is required for ADD_VEHICLE");
                }
            }
            case RAISE_CREDIT_LIMIT -> {
                if (!p.containsKey("creditLimitAmount") && !p.containsKey("creditLimitLiters")) {
                    throw new BusinessException("creditLimitAmount or creditLimitLiters is required for RAISE_CREDIT_LIMIT");
                }
            }
            case UNBLOCK_CUSTOMER -> {
                // reason is optional
            }
            case RECORD_STATEMENT_PAYMENT -> {
                if (p.get("statementId") == null) {
                    throw new BusinessException("statementId is required for RECORD_STATEMENT_PAYMENT");
                }
                requirePaymentFields(p);
            }
            case RECORD_INVOICE_PAYMENT -> {
                if (p.get("invoiceBillId") == null) {
                    throw new BusinessException("invoiceBillId is required for RECORD_INVOICE_PAYMENT");
                }
                requirePaymentFields(p);
            }
        }
    }

    private void requirePaymentFields(Map<String, Object> p) {
        if (p.get("amount") == null) {
            throw new BusinessException("amount is required");
        }
        if (new BigDecimal(p.get("amount").toString()).signum() <= 0) {
            throw new BusinessException("amount must be positive");
        }
        if (p.get("paymentMode") == null || p.get("paymentMode").toString().isBlank()) {
            throw new BusinessException("paymentMode is required");
        }
    }

    private Payment buildPayment(Map<String, Object> p) {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal(p.get("amount").toString()));
        payment.setPaymentMode(PaymentMode.valueOf(p.get("paymentMode").toString()));
        if (p.get("referenceNo") != null) {
            payment.setReferenceNo(p.get("referenceNo").toString());
        }
        if (p.get("remarks") != null) {
            payment.setRemarks(p.get("remarks").toString());
        }
        if (p.get("paymentDate") != null) {
            payment.setPaymentDate(LocalDateTime.parse(p.get("paymentDate").toString()));
        }
        if (p.get("proofImageKey") != null) {
            payment.setProofImageKey(p.get("proofImageKey").toString());
        }
        return payment;
    }

    private void applyAddVehicle(Long customerId, Map<String, Object> p) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        Vehicle v = new Vehicle();
        v.setCustomer(customer);
        v.setVehicleNumber(p.get("vehicleNumber").toString().trim());
        if (p.get("vehicleTypeId") != null) {
            Long vtId = Long.valueOf(p.get("vehicleTypeId").toString());
            VehicleType vt = vehicleTypeRepository.findById(vtId)
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleType not found: " + vtId));
            v.setVehicleType(vt);
        }
        if (p.get("preferredProductId") != null) {
            Long pid = Long.valueOf(p.get("preferredProductId").toString());
            Product prod = productRepository.findById(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + pid));
            v.setPreferredProduct(prod);
        }
        if (p.get("maxCapacity") != null) {
            v.setMaxCapacity(new BigDecimal(p.get("maxCapacity").toString()));
        }
        if (p.get("maxLitersPerMonth") != null) {
            v.setMaxLitersPerMonth(new BigDecimal(p.get("maxLitersPerMonth").toString()));
        }
        vehicleService.createVehicle(v);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : new HashMap<>());
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize payload: " + e.getMessage());
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("Failed to parse payload: " + e.getMessage());
        }
    }
}
