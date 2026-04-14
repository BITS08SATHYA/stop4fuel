package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.StatementDTO;
import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.backend.service.StatementAutoGenerationService;
import com.stopforfuel.backend.service.StatementExcelService;
import com.stopforfuel.backend.service.StatementService;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
    private final StatementExcelService statementExcelService;
    private final StatementRepository statementRepository;

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
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false, defaultValue = "statementDate,desc") String sort) {
        String[] parts = sort.split(",");
        String sortField = parts[0];
        org.springframework.data.domain.Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Sort sorting = org.springframework.data.domain.Sort.by(dir, sortField)
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        return statementService.getStatements(customerId, status, categoryType, fromDate, toDate, search,
                PageRequest.of(page, Math.min(size, 100), sorting))
                .map(StatementDTO::from);
    }

    @GetMapping("/outstanding-search")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<StatementDTO> getOutstandingSearch(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) java.math.BigDecimal maxBalance) {
        // Sentinels avoid Postgres 'could not determine data type of parameter' for nulls.
        LocalDate fd = fromDate != null ? fromDate : LocalDate.of(2000, 1, 1);
        LocalDate td = toDate != null ? toDate : LocalDate.of(2099, 12, 31);
        java.math.BigDecimal mb = maxBalance != null ? maxBalance : new java.math.BigDecimal("999999999999");
        return statementRepository.findOutstanding(fd, td, search == null ? "" : search, mb,
                        SecurityUtils.getScid(),
                        PageRequest.of(page, Math.min(size, 100)))
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

    @GetMapping("/customer/{customerId}/recommended-limits")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Map<String, java.math.BigDecimal> getRecommendedLimits(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "15") int count) {
        return statementService.getRecommendedLimits(customerId, count);
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
    @PreAuthorize("hasPermission(null, 'PAYMENT_CREATE')")
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
     * Regenerate an existing statement with new parameters, keeping the same statement number.
     * PUT /api/statements/{id}/regenerate?fromDate=2025-06-01&toDate=2025-06-30
     */
    @PutMapping("/{id}/regenerate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public ResponseEntity<StatementDTO> regenerate(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) List<Long> billIds,
            @RequestParam(required = false) Long customerId) {
        Statement statement = statementService.regenerateStatement(
                id, fromDate, toDate, vehicleId, productId, billIds, customerId);
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
    @PreAuthorize("hasPermission(null, 'PAYMENT_DELETE')")
    public ResponseEntity<StatementDTO> removeBillFromStatement(
            @PathVariable Long statementId,
            @PathVariable Long billId) {
        Statement updated = statementService.removeBillFromStatement(statementId, billId);
        return ResponseEntity.ok(StatementDTO.from(updated));
    }

    @PostMapping("/{id}/generate-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
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
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public ResponseEntity<StatementDTO> approve(@PathVariable Long id) {
        Statement approved = statementService.approveStatement(id);
        return ResponseEntity.ok(StatementDTO.from(approved));
    }

    @PostMapping("/auto-generate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_CREATE')")
    public ResponseEntity<Map<String, Integer>> autoGenerate() {
        int count = statementAutoGenerationService.generateDraftsManually(SecurityUtils.getScid());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_DELETE')")
    public void delete(@PathVariable Long id) {
        statementService.deleteStatement(id);
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status) {
        Long scid = SecurityUtils.getScid();
        List<Statement> statements = (status != null && !status.isBlank())
                ? statementRepository.findByDateRangeAndStatusAndScid(fromDate, toDate, status, scid)
                : statementRepository.findByDateRangeAndScid(fromDate, toDate, scid);
        byte[] bytes = statementExcelService.generateExcel(statements);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statements_" + fromDate + "_" + toDate + ".xlsx");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping("/bulk-generate-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<Map<String, Object>> bulkGeneratePdfs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        Long scid = SecurityUtils.getScid();
        List<Statement> statements = statementRepository.findByDateRangeAndScid(fromDate, toDate, scid);
        int generated = 0;
        for (Statement s : statements) {
            if (s.getStatementPdfUrl() == null || s.getStatementPdfUrl().isBlank()) {
                statementService.generateAndStorePdf(s.getId());
                generated++;
            }
        }
        return ResponseEntity.ok(Map.of("generated", generated));
    }
}
