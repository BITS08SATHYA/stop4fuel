package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.StatementDTO;
import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.service.StatementAutoGenerationService;
import com.stopforfuel.backend.service.StatementService;
import com.stopforfuel.config.SecurityUtils;
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
    private final StatementAutoGenerationService statementAutoGenerationService;

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public StatementStats getStats() {
        return statementService.getStats();
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<StatementDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryType) {
        return statementService.getStatements(customerId, status, categoryType, fromDate, toDate, search,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by("statementDate").descending()))
                .map(StatementDTO::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public StatementDTO getById(@PathVariable Long id) {
        return StatementDTO.from(statementService.getStatementById(id));
    }

    @GetMapping("/by-no/{statementNo}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public StatementDTO getByStatementNo(@PathVariable String statementNo) {
        return StatementDTO.from(statementService.getStatementByNo(statementNo));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<StatementDTO> getByCustomer(@PathVariable Long customerId) {
        return statementService.getStatementsByCustomer(customerId).stream().map(StatementDTO::from).toList();
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<StatementDTO> getOutstanding() {
        return statementService.getOutstandingStatements().stream().map(StatementDTO::from).toList();
    }

    @GetMapping("/outstanding/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<StatementDTO> getOutstandingByCustomer(@PathVariable Long customerId) {
        return statementService.getOutstandingByCustomer(customerId).stream().map(StatementDTO::from).toList();
    }

    /**
     * Preview unlinked credit bills matching filters (before generating a statement).
     * GET /api/statements/preview?customerId=1&fromDate=2025-06-01&toDate=2025-06-30&vehicleId=5&productId=2
     */
    @GetMapping("/preview")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<InvoiceBillDTO> previewBills(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId) {
        return statementService.previewBills(customerId, fromDate, toDate, vehicleId, productId)
                .stream().map(InvoiceBillDTO::from).toList();
    }

    /**
     * Generate a statement for a customer with optional filters.
     * POST /api/statements/generate?customerId=1&fromDate=2025-06-01&toDate=2025-06-30
     * Optional filters: vehicleId, productId, billIds (comma-separated)
     */
    @PostMapping("/generate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<StatementDTO> generate(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) List<Long> billIds) {
        Statement statement = statementService.generateStatement(
                customerId, fromDate, toDate, vehicleId, productId, billIds);
        return ResponseEntity.ok(StatementDTO.from(statement));
    }

    /**
     * Get all bills linked to a statement (for detail/print view).
     */
    @GetMapping("/{id}/bills")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<InvoiceBillDTO> getStatementBills(@PathVariable Long id) {
        return statementService.getStatementBills(id).stream().map(InvoiceBillDTO::from).toList();
    }

    /**
     * Remove a disputed bill from a statement and recalculate totals.
     */
    @DeleteMapping("/{statementId}/bills/{billId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<StatementDTO> removeBillFromStatement(
            @PathVariable Long statementId,
            @PathVariable Long billId) {
        Statement updated = statementService.removeBillFromStatement(statementId, billId);
        return ResponseEntity.ok(StatementDTO.from(updated));
    }

    @PostMapping("/{id}/generate-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<StatementDTO> generatePdf(@PathVariable Long id) {
        Statement updated = statementService.generateAndStorePdf(id);
        return ResponseEntity.ok(StatementDTO.from(updated));
    }

    @GetMapping("/{id}/pdf-url")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<Map<String, String>> getPdfUrl(@PathVariable Long id) {
        String url = statementService.getStatementPdfUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<StatementDTO> approve(@PathVariable Long id) {
        Statement approved = statementService.approveStatement(id);
        return ResponseEntity.ok(StatementDTO.from(approved));
    }

    @PostMapping("/auto-generate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public ResponseEntity<Map<String, Integer>> autoGenerate() {
        int count = statementAutoGenerationService.generateDraftsManually(SecurityUtils.getScid());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_MANAGE')")
    public void delete(@PathVariable Long id) {
        statementService.deleteStatement(id);
    }
}
