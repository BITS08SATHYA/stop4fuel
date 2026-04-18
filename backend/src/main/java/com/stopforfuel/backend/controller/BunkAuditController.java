package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.BunkAuditReport;
import com.stopforfuel.backend.dto.MonthlyAuditSummary;
import com.stopforfuel.backend.service.BunkAuditReportService;
import com.stopforfuel.backend.service.BunkAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class BunkAuditController {

    private final BunkAuditService auditService;
    private final BunkAuditReportService auditReportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<BunkAuditReport> compute(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "RANGE") BunkAuditReport.Granularity granularity) {
        return ResponseEntity.ok(auditService.compute(from, to, granularity));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<BunkAuditReport> forShift(@PathVariable Long shiftId) {
        return ResponseEntity.ok(auditService.computeForShift(shiftId));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<List<MonthlyAuditSummary>> monthly(@RequestParam int year) {
        return ResponseEntity.ok(auditService.monthlyForYear(year));
    }

    @GetMapping("/pdf")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<byte[]> pdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "RANGE") BunkAuditReport.Granularity granularity) {
        byte[] pdf = auditReportService.generatePdf(from, to, granularity);
        String filename = "BunkAudit_" + from + "_to_" + to + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
