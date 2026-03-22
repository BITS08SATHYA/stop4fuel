package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<Payment> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String customerCategory) {
        return paymentService.getPayments(customerCategory, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Payment getById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<Payment> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getPaymentsByCustomer(customerId, PageRequest.of(page, size));
    }

    @GetMapping("/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Payment> getByStatement(@PathVariable Long statementId) {
        return paymentService.getPaymentsByStatement(statementId);
    }

    @GetMapping("/bill/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Payment> getByInvoiceBill(@PathVariable Long invoiceBillId) {
        return paymentService.getPaymentsByInvoiceBill(invoiceBillId);
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Payment> getByShift(@PathVariable Long shiftId) {
        return paymentService.getPaymentsByShift(shiftId);
    }

    /**
     * Record a payment against a statement (for statement customers).
     * POST /api/payments/statement/{statementId}
     */
    @PostMapping("/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Payment> recordStatementPayment(
            @PathVariable Long statementId,
            @Valid @RequestBody Payment payment) {
        Payment saved = paymentService.recordStatementPayment(statementId, payment);
        return ResponseEntity.ok(saved);
    }

    /**
     * Record a payment against an individual bill (for local credit customers).
     * POST /api/payments/bill/{invoiceBillId}
     */
    @PostMapping("/bill/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Payment> recordBillPayment(
            @PathVariable Long invoiceBillId,
            @Valid @RequestBody Payment payment) {
        Payment saved = paymentService.recordBillPayment(invoiceBillId, payment);
        return ResponseEntity.ok(saved);
    }

    /**
     * Get payment summary for a statement (total received, balance, payment history).
     */
    @GetMapping("/summary/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<PaymentService.StatementPaymentSummary> getStatementSummary(
            @PathVariable Long statementId) {
        return ResponseEntity.ok(paymentService.getStatementPaymentSummary(statementId));
    }

    /**
     * Get payment summary for an individual bill.
     */
    @GetMapping("/summary/bill/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<PaymentService.BillPaymentSummary> getBillSummary(
            @PathVariable Long invoiceBillId) {
        return ResponseEntity.ok(paymentService.getBillPaymentSummary(invoiceBillId));
    }

    /**
     * Upload a proof image for a payment.
     * POST /api/payments/{id}/upload-proof
     */
    @PostMapping("/{id}/upload-proof")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Payment> uploadProof(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        Payment updated = paymentService.uploadProofImage(id, file);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get a presigned URL for a payment's proof image.
     * GET /api/payments/{id}/proof-url
     */
    @GetMapping("/{id}/proof-url")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<?> getProofUrl(@PathVariable Long id) {
        String url = paymentService.getProofImageUrl(id);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("url", url));
    }
}
