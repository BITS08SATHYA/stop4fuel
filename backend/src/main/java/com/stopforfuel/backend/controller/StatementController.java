package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.DayWiseStatementPreview;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.StatementDTO;
import com.stopforfuel.backend.dto.StatementStats;
import com.stopforfuel.backend.dto.VehicleWiseStatementPreview;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.enums.BillType;
import com.stopforfuel.backend.enums.ReportLayout;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.backend.service.BillSequenceService;
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
    private final BillSequenceService billSequenceService;
    private final CompanyRepository companyRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final PaymentRepository paymentRepository;

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
        Page<StatementDTO> result = statementService.getStatements(customerId, status, categoryType, fromDate, toDate, search,
                PageRequest.of(page, Math.min(size, 100), sorting))
                .map(StatementDTO::from);
        statementService.attachVehicleNumbers(result.getContent());
        return result;
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
        List<StatementDTO> dtos = statementService.getStatementsByCustomer(customerId)
                .stream().map(StatementDTO::from).toList();
        statementService.attachVehicleNumbers(dtos);
        return dtos;
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
            @RequestParam(required = false) List<Long> billIds,
            @RequestParam(required = false) ReportLayout reportLayout) {
        Statement statement = statementService.generateStatement(
                customerId, fromDate, toDate, vehicleId, productId, billIds,
                reportLayout != null ? reportLayout : ReportLayout.VEHICLE_WISE);
        return ResponseEntity.ok(StatementDTO.from(statement));
    }

    /**
     * Preview bills for the customer in the period grouped by calendar day, with running
     * totals and suggested split boundaries that keep each statement under maxAmount.
     * GET /api/statements/preview-day-wise?customerId=1&fromDate=...&toDate=...&maxAmount=369860
     */
    @GetMapping("/preview-day-wise")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public DayWiseStatementPreview previewDayWise(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) java.math.BigDecimal maxAmount) {
        return statementService.previewDayWise(customerId, fromDate, toDate, maxAmount);
    }

    /**
     * Preview bills grouped by vehicle, each vehicle split into statement-sized groups by liter
     * ceiling. literCeiling is optional — when omitted, each vehicle uses its effective ceiling
     * (vehicle override → customer default). When supplied, it overrides all vehicles for this preview.
     * GET /api/statements/preview-vehicle-wise?customerId=1&fromDate=...&toDate=...&literCeiling=250
     */
    @GetMapping("/preview-vehicle-wise")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public VehicleWiseStatementPreview previewVehicleWise(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) java.math.BigDecimal literCeiling) {
        return statementService.previewVehicleWise(customerId, fromDate, toDate, literCeiling);
    }

    public record GenerateBatchRequest(Long customerId, ReportLayout reportLayout, List<BatchEntry> statements) {
        public record BatchEntry(List<Long> billIds) {}
    }

    /**
     * Create N statements at once. Each entry carries the explicit billIds the UI assigned
     * to that statement (after the user's optional split-boundary adjustments).
     * POST /api/statements/generate-batch
     */
    @PostMapping("/generate-batch")
    @PreAuthorize("hasPermission(null, 'PAYMENT_CREATE')")
    public ResponseEntity<List<StatementDTO>> generateBatch(@RequestBody GenerateBatchRequest req) {
        if (req == null || req.customerId() == null || req.statements() == null || req.statements().isEmpty()) {
            throw new IllegalArgumentException("customerId and at least one statement group are required");
        }
        List<List<Long>> groups = req.statements().stream().map(GenerateBatchRequest.BatchEntry::billIds).toList();
        ReportLayout layout = req.reportLayout() != null ? req.reportLayout() : ReportLayout.VEHICLE_WISE;
        List<Statement> created = statementService.generateBatch(req.customerId(), layout, groups);
        // PDF generation post-commit — failures here don't roll back statements.
        for (Statement s : created) {
            try {
                statementService.generateAndStorePdf(s.getId());
            } catch (Exception ignored) {
                // PDF failures are non-fatal; the statement row is valid and the PDF
                // can be regenerated later via /{id}/generate-pdf.
            }
        }
        return ResponseEntity.ok(created.stream().map(StatementDTO::from).toList());
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
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ReportLayout reportLayout) {
        Statement statement = statementService.regenerateStatement(
                id, fromDate, toDate, vehicleId, productId, billIds, customerId, reportLayout);
        return ResponseEntity.ok(StatementDTO.from(statement));
    }

    /**
     * Rename the human-readable statement number (S-XXXXX). Allowed on DRAFT and
     * NOT_PAID statements only — PAID is frozen because the customer already has the
     * document under the original number.
     */
    @PatchMapping("/{id}/statement-no")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public StatementDTO renameStatement(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        String newNo = body != null ? body.get("statementNo") : null;
        return StatementDTO.from(statementService.updateStatementNo(id, newNo));
    }

    /**
     * Peek the next statement number that Auto-Generate Drafts / Generate Statement
     * would issue. Read-only — does not consume the sequence.
     */
    @GetMapping("/sequence/peek")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public BillSequenceService.NextBillNoView peekStatementSequence() {
        return billSequenceService.peekNextBillNo(BillType.STMT);
    }

    /**
     * Set the next statement number — admin-controlled sequence reset / fast-forward.
     * Forward-only by design: existing statement numbers are not renumbered.
     */
    @PutMapping("/sequence/next")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public BillSequenceService.NextBillNoView setStatementSequence(@RequestBody Map<String, Long> body) {
        Long next = body != null ? body.get("nextNumber") : null;
        if (next == null) {
            throw new IllegalArgumentException("nextNumber is required");
        }
        return billSequenceService.setNextBillNo(BillType.STMT, next);
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

    /**
     * Preview the statement PDF without persisting a Statement row, linking bills,
     * uploading to S3, or consuming a statement number. Used by the
     * "Download PDF (Preview)" button so cashiers can verify the layout before committing.
     */
    @GetMapping("/preview-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> previewPdf(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) List<Long> billIds) {
        byte[] pdf = statementService.previewStatementPdf(customerId, fromDate, toDate, vehicleId, productId, billIds);
        String filename = "Statement_PREVIEW_" + customerId + "_" + fromDate + "_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }

    @GetMapping("/{id}/pdf-url")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<Map<String, String>> getPdfUrl(@PathVariable Long id) {
        String url = statementService.getStatementPdfUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Consolidated customer-wise PDF for the period — useful when the customer is configured
     * VEHICLE_WISE but the owner wants a single combined document. Pure read-only: no DB write,
     * no S3 upload, no statement number consumed. Pulls all credit bills regardless of grouping.
     */
    @GetMapping("/customer/{customerId}/consolidated-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> consolidatedCustomerPdf(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = statementService.consolidatedCustomerPdf(customerId, fromDate, toDate);
        String filename = "Consolidated_Statement_" + customerId + "_" + fromDate + "_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public ResponseEntity<StatementDTO> approve(@PathVariable Long id) {
        Statement approved = statementService.approveStatement(id);
        return ResponseEntity.ok(StatementDTO.from(approved));
    }

    @PostMapping("/auto-generate")
    @PreAuthorize("hasPermission(null, 'PAYMENT_CREATE')")
    public ResponseEntity<Map<String, Integer>> autoGenerate(@RequestBody(required = false) AutoGenRequest req) {
        Long scid = SecurityUtils.getScid();
        int count = (req == null || req.isEmpty())
                ? statementAutoGenerationService.generateDraftsManually(scid)
                : statementAutoGenerationService.generateDraftsManually(scid, req.fromDate(), req.toDate(), req.frequency());
        return ResponseEntity.ok(Map.of("count", count));
    }

    public record AutoGenRequest(
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate,
            String frequency
    ) {
        public boolean isEmpty() {
            return fromDate == null && toDate == null && (frequency == null || frequency.isBlank());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_DELETE')")
    public void delete(@PathVariable Long id) {
        statementService.deleteStatement(id);
    }

    /**
     * Per-statement Excel detail export. Mirrors the PDF layout (header, bills grouped
     * by date or vehicle per the statement's reportLayout, product/vehicle summaries,
     * payments, balance) but as a single-sheet .xlsx for accounting use.
     */
    @GetMapping("/{id}/export-excel")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> exportStatementDetailExcel(@PathVariable Long id) {
        Statement statement = statementService.getStatementById(id);
        List<com.stopforfuel.backend.entity.InvoiceBill> bills = invoiceBillRepository.findByStatementId(id);
        List<com.stopforfuel.backend.entity.Payment> payments = paymentRepository.findByStatementId(id);
        Long scid = statement.getScid() != null ? statement.getScid() : SecurityUtils.getScid();
        Company company = companyRepository.findByScid(scid).stream().findFirst().orElse(null);
        byte[] bytes = statementExcelService.generateStatementDetailExcel(statement, bills, company, payments);
        String safeNo = statement.getStatementNo() != null ? statement.getStatementNo().replace("/", "-") : String.valueOf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Statement_" + safeNo + ".xlsx");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(headers).body(bytes);
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
        String companyName = companyRepository.findByScid(scid).stream()
                .findFirst().map(Company::getName).orElse("");
        byte[] bytes = statementExcelService.generateExcel(statements, companyName, fromDate, toDate);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statements_" + fromDate + "_" + toDate + ".xlsx");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping("/bulk-generate-pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public ResponseEntity<Map<String, Object>> bulkGeneratePdfs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean force) {
        Long scid = SecurityUtils.getScid();
        List<Statement> statements = statementRepository.findByDateRangeAndScid(fromDate, toDate, scid);
        int generated = 0;
        for (Statement s : statements) {
            if (force || s.getStatementPdfUrl() == null || s.getStatementPdfUrl().isBlank()) {
                statementService.generateAndStorePdf(s.getId());
                generated++;
            }
        }
        return ResponseEntity.ok(Map.of("generated", generated));
    }
}
