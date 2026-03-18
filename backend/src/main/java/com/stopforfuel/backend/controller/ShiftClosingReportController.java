package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.ReportAuditLog;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import com.stopforfuel.backend.service.ShiftClosingReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ShiftClosingReportController {

    private final ShiftClosingReportService reportService;

    @PostMapping("/{shiftId}/generate")
    public ShiftClosingReport generateReport(@PathVariable Long shiftId) {
        return reportService.generateReport(shiftId);
    }

    @GetMapping("/{shiftId}")
    public ShiftClosingReport getReport(@PathVariable Long shiftId) {
        return reportService.getReport(shiftId);
    }

    @GetMapping
    public List<ShiftClosingReport> getAllReports(@RequestParam(required = false) String status) {
        return reportService.getAllReports(status);
    }

    @PatchMapping("/{reportId}/line-items/{lineItemId}")
    public ShiftClosingReport editLineItem(@PathVariable Long reportId,
                                            @PathVariable Long lineItemId,
                                            @RequestBody Map<String, Object> body) {
        BigDecimal newAmount = new BigDecimal(body.get("amount").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return reportService.editLineItem(reportId, lineItemId, newAmount, reason);
    }

    @PostMapping("/{reportId}/transfer")
    public ShiftClosingReport transferEntry(@PathVariable Long reportId,
                                             @RequestBody Map<String, Object> body) {
        Long lineItemId = Long.parseLong(body.get("lineItemId").toString());
        Long targetReportId = Long.parseLong(body.get("targetReportId").toString());
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return reportService.transferEntry(reportId, lineItemId, targetReportId, reason);
    }

    @PostMapping("/{reportId}/finalize")
    public ShiftClosingReport finalizeReport(@PathVariable Long reportId,
                                              @RequestBody(required = false) Map<String, String> body) {
        String finalizedBy = body != null ? body.get("finalizedBy") : null;
        return reportService.finalizeReport(reportId, finalizedBy);
    }

    @PostMapping("/{reportId}/recompute")
    public ShiftClosingReport recomputeReport(@PathVariable Long reportId) {
        return reportService.recomputeReport(reportId);
    }

    @GetMapping("/{reportId}/audit-log")
    public List<ReportAuditLog> getAuditLog(@PathVariable Long reportId) {
        return reportService.getAuditLog(reportId);
    }
}
