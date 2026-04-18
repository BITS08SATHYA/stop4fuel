package com.stopforfuel.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stopforfuel.backend.dto.ApprovalRequestDTO;
import com.stopforfuel.backend.entity.ApprovalRequest;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.entity.VehicleType;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.enums.ApprovalRequestStatus;
import com.stopforfuel.backend.enums.ApprovalRequestType;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.ApprovalRequestRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.backend.repository.VehicleTypeRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final VehicleService vehicleService;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ProductRepository productRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final PaymentService paymentService;
    private final PushNotificationService pushNotificationService;
    private final NotificationBroadcaster notificationBroadcaster;
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
        // Real-time SSE fan-out to connected approvers
        try {
            notificationBroadcaster.broadcastToPermission("APPROVAL_REQUEST_APPROVE", "approval", Map.of(
                    "type", "APPROVAL_REQUEST_CREATED",
                    "requestId", saved.getId(),
                    "requestType", saved.getRequestType().name(),
                    "customerId", saved.getCustomerId() != null ? saved.getCustomerId() : "",
                    "requestedBy", saved.getRequestedBy() != null ? saved.getRequestedBy() : "",
                    "createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : ""
            ));
        } catch (Exception ignored) {}
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
            case UNBLOCK_CUSTOMER -> {
                // Idempotent: if already active, skip the unblock call and just mark approved
                Customer c = customerRepository.findById(req.getCustomerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.getCustomerId()));
                if (c.getStatus() == EntityStatus.BLOCKED) {
                    customerService.unblockCustomer(req.getCustomerId(),
                            (String) payload.getOrDefault("reason", null));
                }
                // else: already unblocked elsewhere — approval is still valid, just a no-op
            }
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
        ApprovalRequest savedApproved = approvalRequestRepository.save(req);
        fanoutReviewed(savedApproved, "APPROVAL_REQUEST_APPROVED");
        return savedApproved;
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
        ApprovalRequest savedRejected = approvalRequestRepository.save(req);
        fanoutReviewed(savedRejected, "APPROVAL_REQUEST_REJECTED");
        return savedRejected;
    }

    /** Notify the cashier who submitted, AND refresh approvers so the request disappears from their inbox. */
    private void fanoutReviewed(ApprovalRequest req, String eventType) {
        Map<String, Object> payload = Map.of(
                "type", eventType,
                "requestId", req.getId(),
                "requestType", req.getRequestType().name(),
                "reviewNote", req.getReviewNote() != null ? req.getReviewNote() : "",
                "reviewedAt", req.getReviewedAt() != null ? req.getReviewedAt().toString() : ""
        );
        try {
            if (req.getRequestedBy() != null) {
                notificationBroadcaster.sendToUser(req.getRequestedBy(), "approval", payload);
            }
            notificationBroadcaster.broadcastToPermission("APPROVAL_REQUEST_APPROVE", "approval", payload);
        } catch (Exception ignored) {}
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

    @Transactional(readOnly = true)
    public List<ApprovalRequestDTO> listPendingDtos() {
        return hydrate(listPending());
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDTO> listMineDtos() {
        return hydrate(listMine());
    }

    /**
     * Batch-hydrate a list of ApprovalRequest entities into DTOs, resolving
     * referenced customer/bill/statement IDs into friendly identifiers in one pass.
     */
    private List<ApprovalRequestDTO> hydrate(List<ApprovalRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();

        // One pass to collect all referenced ids per type
        Set<Long> customerIds = new HashSet<>();
        Set<Long> invoiceBillIds = new HashSet<>();
        Set<Long> statementIds = new HashSet<>();
        List<Map<String, Object>> parsedPayloads = new ArrayList<>(requests.size());

        for (ApprovalRequest r : requests) {
            if (r.getCustomerId() != null) customerIds.add(r.getCustomerId());
            Map<String, Object> p = readJsonSafe(r.getPayload());
            parsedPayloads.add(p);
            if (r.getRequestType() == ApprovalRequestType.RECORD_INVOICE_PAYMENT
                    && p.get("invoiceBillId") != null) {
                invoiceBillIds.add(toLong(p.get("invoiceBillId")));
            } else if (r.getRequestType() == ApprovalRequestType.RECORD_STATEMENT_PAYMENT
                    && p.get("statementId") != null) {
                statementIds.add(toLong(p.get("statementId")));
            }
        }

        Map<Long, Customer> customers = customerIds.isEmpty() ? Map.of()
                : customerRepository.findAllById(customerIds).stream()
                    .collect(Collectors.toMap(Customer::getId, c -> c));
        Map<Long, InvoiceBill> bills = invoiceBillIds.isEmpty() ? Map.of()
                : invoiceBillRepository.findAllById(invoiceBillIds).stream()
                    .collect(Collectors.toMap(InvoiceBill::getId, b -> b));
        Map<Long, Statement> statements = statementIds.isEmpty() ? Map.of()
                : statementRepository.findAllById(statementIds).stream()
                    .collect(Collectors.toMap(Statement::getId, s -> s));

        List<ApprovalRequestDTO> out = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            out.add(toDto(requests.get(i), parsedPayloads.get(i), customers, bills, statements));
        }
        return out;
    }

    private ApprovalRequestDTO toDto(ApprovalRequest r,
                                      Map<String, Object> payload,
                                      Map<Long, Customer> customers,
                                      Map<Long, InvoiceBill> bills,
                                      Map<Long, Statement> statements) {
        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setId(r.getId());
        dto.setRequestType(r.getRequestType());
        dto.setStatus(r.getStatus());
        dto.setCustomerId(r.getCustomerId());
        dto.setRequestedBy(r.getRequestedBy());
        dto.setRequestNote(r.getRequestNote());
        dto.setReviewedBy(r.getReviewedBy());
        dto.setReviewNote(r.getReviewNote());
        dto.setReviewedAt(r.getReviewedAt());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        dto.setPayload(payload);

        Customer customer = r.getCustomerId() != null ? customers.get(r.getCustomerId()) : null;
        if (customer != null) {
            dto.setCustomerName(customer.getName());
        }

        switch (r.getRequestType()) {
            case RECORD_INVOICE_PAYMENT -> {
                if (payload.get("invoiceBillId") != null) {
                    InvoiceBill bill = bills.get(toLong(payload.get("invoiceBillId")));
                    if (bill != null) {
                        dto.setBillNo(bill.getBillNo());
                        if (customer == null && bill.getCustomer() != null) {
                            dto.setCustomerName(bill.getCustomer().getName());
                        }
                    }
                }
                dto.setAmount(toBigDecimal(payload.get("amount")));
                dto.setPaymentMode(toStr(payload.get("paymentMode")));
            }
            case RECORD_STATEMENT_PAYMENT -> {
                if (payload.get("statementId") != null) {
                    Statement stmt = statements.get(toLong(payload.get("statementId")));
                    if (stmt != null) {
                        dto.setStatementNo(stmt.getStatementNo());
                        if (customer == null && stmt.getCustomer() != null) {
                            dto.setCustomerName(stmt.getCustomer().getName());
                        }
                    }
                }
                dto.setAmount(toBigDecimal(payload.get("amount")));
                dto.setPaymentMode(toStr(payload.get("paymentMode")));
            }
            case ADD_VEHICLE -> dto.setVehicleNumber(toStr(payload.get("vehicleNumber")));
            case RAISE_CREDIT_LIMIT -> {
                if (customer != null) {
                    dto.setCurrentCreditLimitAmount(customer.getCreditLimitAmount());
                    dto.setCurrentCreditLimitLiters(customer.getCreditLimitLiters());
                }
                dto.setRequestedCreditLimitAmount(toBigDecimal(payload.get("creditLimitAmount")));
                dto.setRequestedCreditLimitLiters(toBigDecimal(payload.get("creditLimitLiters")));
            }
            case UNBLOCK_CUSTOMER -> {
                // nothing extra — reason already in payload
            }
        }
        return dto;
    }

    private Map<String, Object> readJsonSafe(String json) {
        try {
            return readJson(json);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(v.toString());
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

    private String toStr(Object v) {
        return v != null ? v.toString() : null;
    }

    private void validatePayload(ApprovalRequestType type, Map<String, Object> payload) {
        Map<String, Object> p = payload != null ? payload : new HashMap<>();
        switch (type) {
            case ADD_VEHICLE -> {
                if (!p.containsKey("vehicleNumber") || p.get("vehicleNumber") == null
                        || p.get("vehicleNumber").toString().isBlank()) {
                    throw new BusinessException("vehicleNumber is required for ADD_VEHICLE");
                }
                if (p.get("vehicleTypeId") == null) {
                    throw new BusinessException("vehicleTypeId is required for ADD_VEHICLE");
                }
                if (p.get("preferredProductId") == null) {
                    throw new BusinessException("preferredProductId (fuel type) is required for ADD_VEHICLE");
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
