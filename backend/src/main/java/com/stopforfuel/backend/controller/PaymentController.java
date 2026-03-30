package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.PaymentDTO;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.service.PaymentService;
import com.stopforfuel.backend.service.PaymentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentReportService paymentReportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<PaymentDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) String paidAgainst,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        return paymentService.getPayments(categoryType, paidAgainst, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate")))
                .map(PaymentDTO::from);
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) String paidAgainst,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        List<Payment> payments = paymentService.getPaymentsForExport(categoryType, paidAgainst, from, to);
        byte[] pdf = paymentReportService.generatePdf(payments, fromDate, toDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) String paidAgainst,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        List<Payment> payments = paymentService.getPaymentsForExport(categoryType, paidAgainst, from, to);
        byte[] excel = paymentReportService.generateExcel(payments, fromDate, toDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/{id}/receipt/pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        byte[] pdf = paymentReportService.generateReceipt(payment);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_receipt_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public PaymentDTO getById(@PathVariable Long id) {
        return PaymentDTO.from(paymentService.getPaymentById(id));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<PaymentDTO> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.getPaymentsByCustomer(customerId, PageRequest.of(page, size))
                .map(PaymentDTO::from);
    }

    @GetMapping("/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<PaymentDTO> getByStatement(@PathVariable Long statementId) {
        return paymentService.getPaymentsByStatement(statementId).stream().map(PaymentDTO::from).toList();
    }

    @GetMapping("/bill/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<PaymentDTO> getByInvoiceBill(@PathVariable Long invoiceBillId) {
        return paymentService.getPaymentsByInvoiceBill(invoiceBillId).stream().map(PaymentDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<PaymentDTO> getByShift(@PathVariable Long shiftId) {
        return paymentService.getPaymentsByShift(shiftId).stream().map(PaymentDTO::from).toList();
    }

    /**
     * Record a payment against a statement (for statement customers).
     * POST /api/payments/statement/{statementId}
     */
    @PostMapping("/statement/{statementId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<PaymentDTO> recordStatementPayment(
            @PathVariable Long statementId,
            @Valid @RequestBody Payment payment) {
        Payment saved = paymentService.recordStatementPayment(statementId, payment);
        return ResponseEntity.ok(PaymentDTO.from(saved));
    }

    /**
     * Record a payment against an individual bill (for local credit customers).
     * POST /api/payments/bill/{invoiceBillId}
     */
    @PostMapping("/bill/{invoiceBillId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<PaymentDTO> recordBillPayment(
            @PathVariable Long invoiceBillId,
            @Valid @RequestBody Payment payment) {
        Payment saved = paymentService.recordBillPayment(invoiceBillId, payment);
        return ResponseEntity.ok(PaymentDTO.from(saved));
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
    public ResponseEntity<PaymentDTO> uploadProof(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        Payment updated = paymentService.uploadProofImage(id, file);
        return ResponseEntity.ok(PaymentDTO.from(updated));
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
