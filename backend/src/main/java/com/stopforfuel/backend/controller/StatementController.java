package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public StatementStats getStats() {
        return statementService.getStats();
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<Statement> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryType) {
        return statementService.getStatements(customerId, status, categoryType, fromDate, toDate, search,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by("statementDate").descending()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Statement getById(@PathVariable Long id) {
        return statementService.getStatementById(id);
    }

    @GetMapping("/by-no/{statementNo}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Statement getByStatementNo(@PathVariable String statementNo) {
        return statementService.getStatementByNo(statementNo);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Statement> getByCustomer(@PathVariable Long customerId) {
        return statementService.getStatementsByCustomer(customerId);
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Statement> getOutstanding() {
        return statementService.getOutstandingStatements();
    }

    @GetMapping("/outstanding/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<Statement> getOutstandingByCustomer(@PathVariable Long customerId) {
        return statementService.getOutstandingByCustomer(customerId);
    }

    /**
     * Preview unlinked credit bills matching filters (before generating a statement).
     * GET /api/statements/preview?customerId=1&fromDate=2025-06-01&toDate=2025-06-30&vehicleId=5&productId=2
     */
    @GetMapping("/preview")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<InvoiceBill> previewBills(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId) {
        return statementService.previewBills(customerId, fromDate, toDate, vehicleId, productId);
    }

    /**
     * Generate a statement for a customer with optional filters.
     * POST /api/statements/generate?customerId=1&fromDate=2025-06-01&toDate=2025-06-30
     * Optional filters: vehicleId, productId, billIds (comma-separated)
     */
    @PostMapping("/generate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Statement> generate(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) List<Long> billIds) {
        Statement statement = statementService.generateStatement(
                customerId, fromDate, toDate, vehicleId, productId, billIds);
        return ResponseEntity.ok(statement);
    }

    /**
     * Get all bills linked to a statement (for detail/print view).
     */
    @GetMapping("/{id}/bills")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<InvoiceBill> getStatementBills(@PathVariable Long id) {
        return statementService.getStatementBills(id);
    }

    /**
     * Remove a disputed bill from a statement and recalculate totals.
     */
    @DeleteMapping("/{statementId}/bills/{billId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Statement> removeBillFromStatement(
            @PathVariable Long statementId,
            @PathVariable Long billId) {
        Statement updated = statementService.removeBillFromStatement(statementId, billId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/generate-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Statement> generatePdf(@PathVariable Long id) {
        Statement updated = statementService.generateAndStorePdf(id);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/pdf-url")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<Map<String, String>> getPdfUrl(@PathVariable Long id) {
        String url = statementService.getStatementPdfUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public void delete(@PathVariable Long id) {
        statementService.deleteStatement(id);
    }
}
