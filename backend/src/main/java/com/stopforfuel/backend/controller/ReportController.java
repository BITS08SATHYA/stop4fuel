package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.AllPartyUnpaidReportService;
import com.stopforfuel.backend.service.CustomerBalanceReportService;
import com.stopforfuel.backend.service.DailySalesReportService;
import com.stopforfuel.backend.service.OpeningBalanceReportService;
import com.stopforfuel.backend.service.TankInventorySummaryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final DailySalesReportService dailySalesReportService;
    private final TankInventorySummaryReportService tankInventoryReportService;
    private final CustomerBalanceReportService customerBalanceReportService;
    private final AllPartyUnpaidReportService allPartyUnpaidReportService;
    private final OpeningBalanceReportService openingBalanceReportService;

    // ======================== Daily Sales ========================

    @GetMapping("/daily-sales/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> dailySalesPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = dailySalesReportService.generatePdf(fromDate, toDate);
        String filename = "DailySales_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/daily-sales/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> dailySalesExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = dailySalesReportService.generateExcel(fromDate, toDate);
        String filename = "DailySales_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== Tank Inventory ========================

    @GetMapping("/tank-inventory/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> tankInventoryPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = tankInventoryReportService.generatePdf(fromDate, toDate);
        String filename = "TankInventory_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/tank-inventory/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> tankInventoryExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = tankInventoryReportService.generateExcel(fromDate, toDate);
        String filename = "TankInventory_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== Customer Balance ========================

    @GetMapping("/customer-balance/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> customerBalancePdf() {
        byte[] pdf = customerBalanceReportService.generatePdf();
        String filename = "CustomerBalance_" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/customer-balance/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> customerBalanceExcel() {
        byte[] excel = customerBalanceReportService.generateExcel();
        String filename = "CustomerBalance_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== All Party Statement ========================

    @GetMapping("/all-party-statement/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> allPartyStatementPdf() {
        byte[] pdf = allPartyUnpaidReportService.generateStatementPdf();
        String filename = "AllPartyStatement_" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/all-party-statement/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> allPartyStatementExcel() {
        byte[] excel = allPartyUnpaidReportService.generateStatementExcel();
        String filename = "AllPartyStatement_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== All Party Local ========================

    @GetMapping("/all-party-local/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> allPartyLocalPdf() {
        byte[] pdf = allPartyUnpaidReportService.generateLocalPdf();
        String filename = "AllPartyLocal_" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/all-party-local/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> allPartyLocalExcel() {
        byte[] excel = allPartyUnpaidReportService.generateLocalExcel();
        String filename = "AllPartyLocal_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== Opening Balance — Local ========================

    @GetMapping("/opening-balance-local/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> openingBalanceLocalPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = openingBalanceReportService.generateLocalPdf(fromDate, toDate);
        String filename = "LocalOpeningBalance_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/opening-balance-local/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> openingBalanceLocalExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = openingBalanceReportService.generateLocalExcel(fromDate, toDate);
        String filename = "LocalOpeningBalance_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== Opening Balance — Statement ========================

    @GetMapping("/opening-balance-statement/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> openingBalanceStatementPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = openingBalanceReportService.generateStatementPdf(fromDate, toDate);
        String filename = "StatementOpeningBalance_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/opening-balance-statement/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> openingBalanceStatementExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = openingBalanceReportService.generateStatementExcel(fromDate, toDate);
        String filename = "StatementOpeningBalance_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
