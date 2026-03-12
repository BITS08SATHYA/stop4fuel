package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public Page<Payment> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getPayments(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Payment getById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/customer/{customerId}")
    public Page<Payment> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getPaymentsByCustomer(customerId, PageRequest.of(page, size));
    }

    @GetMapping("/statement/{statementId}")
    public List<Payment> getByStatement(@PathVariable Long statementId) {
        return paymentService.getPaymentsByStatement(statementId);
    }

    @GetMapping("/bill/{invoiceBillId}")
    public List<Payment> getByInvoiceBill(@PathVariable Long invoiceBillId) {
        return paymentService.getPaymentsByInvoiceBill(invoiceBillId);
    }

    @GetMapping("/shift/{shiftId}")
    public List<Payment> getByShift(@PathVariable Long shiftId) {
        return paymentService.getPaymentsByShift(shiftId);
    }

    /**
     * Record a payment against a statement (for statement customers).
     * POST /api/payments/statement/{statementId}
     */
    @PostMapping("/statement/{statementId}")
    public ResponseEntity<Payment> recordStatementPayment(
            @PathVariable Long statementId,
            @RequestBody Payment payment) {
        Payment saved = paymentService.recordStatementPayment(statementId, payment);
        return ResponseEntity.ok(saved);
    }

    /**
     * Record a payment against an individual bill (for local credit customers).
     * POST /api/payments/bill/{invoiceBillId}
     */
    @PostMapping("/bill/{invoiceBillId}")
    public ResponseEntity<Payment> recordBillPayment(
            @PathVariable Long invoiceBillId,
            @RequestBody Payment payment) {
        Payment saved = paymentService.recordBillPayment(invoiceBillId, payment);
        return ResponseEntity.ok(saved);
    }

    /**
     * Get payment summary for a statement (total received, balance, payment history).
     */
    @GetMapping("/summary/statement/{statementId}")
    public ResponseEntity<PaymentService.StatementPaymentSummary> getStatementSummary(
            @PathVariable Long statementId) {
        return ResponseEntity.ok(paymentService.getStatementPaymentSummary(statementId));
    }

    /**
     * Get payment summary for an individual bill.
     */
    @GetMapping("/summary/bill/{invoiceBillId}")
    public ResponseEntity<PaymentService.BillPaymentSummary> getBillSummary(
            @PathVariable Long invoiceBillId) {
        return ResponseEntity.ok(paymentService.getBillPaymentSummary(invoiceBillId));
    }
}
