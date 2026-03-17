package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatementController {

    private final StatementService statementService;

    @GetMapping
    public Page<Statement> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status) {
        return statementService.getStatements(customerId, status, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Statement getById(@PathVariable Long id) {
        return statementService.getStatementById(id);
    }

    @GetMapping("/by-no/{statementNo}")
    public Statement getByStatementNo(@PathVariable String statementNo) {
        return statementService.getStatementByNo(statementNo);
    }

    @GetMapping("/customer/{customerId}")
    public List<Statement> getByCustomer(@PathVariable Long customerId) {
        return statementService.getStatementsByCustomer(customerId);
    }

    @GetMapping("/outstanding")
    public List<Statement> getOutstanding() {
        return statementService.getOutstandingStatements();
    }

    @GetMapping("/outstanding/customer/{customerId}")
    public List<Statement> getOutstandingByCustomer(@PathVariable Long customerId) {
        return statementService.getOutstandingByCustomer(customerId);
    }

    /**
     * Preview unlinked credit bills matching filters (before generating a statement).
     * GET /api/statements/preview?customerId=1&fromDate=2025-06-01&toDate=2025-06-30&vehicleId=5&productId=2
     */
    @GetMapping("/preview")
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
    public List<InvoiceBill> getStatementBills(@PathVariable Long id) {
        return statementService.getStatementBills(id);
    }

    /**
     * Remove a disputed bill from a statement and recalculate totals.
     */
    @DeleteMapping("/{statementId}/bills/{billId}")
    public ResponseEntity<Statement> removeBillFromStatement(
            @PathVariable Long statementId,
            @PathVariable Long billId) {
        Statement updated = statementService.removeBillFromStatement(statementId, billId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        statementService.deleteStatement(id);
    }
}
