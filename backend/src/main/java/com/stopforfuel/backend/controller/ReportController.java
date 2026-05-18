package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.AllPartyUnpaidReportService;
import com.stopforfuel.backend.service.CustomerBalanceReportService;
import com.stopforfuel.backend.service.DailySalesRegisterService;
import com.stopforfuel.backend.service.DailySalesReportService;
import com.stopforfuel.backend.service.IncentivePaymentReportService;
import com.stopforfuel.backend.service.OpeningBalanceReportService;
import com.stopforfuel.backend.service.TankInventorySummaryReportService;
import com.stopforfuel.backend.service.VatReportService;
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
    private final IncentivePaymentReportService incentivePaymentReportService;
    private final VatReportService vatReportService;
    private final DailySalesRegisterService dailySalesRegisterService;

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

    // ======================== Incentive Payment ========================

    @GetMapping("/incentive-payment/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> incentivePaymentPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = incentivePaymentReportService.generatePdf(fromDate, toDate);
        String filename = "IncentivePayment_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/incentive-payment/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> incentivePaymentExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = incentivePaymentReportService.generateExcel(fromDate, toDate);
        String filename = "IncentivePayment_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== VAT Report ========================

    @GetMapping("/vat/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> vatPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] pdf = vatReportService.generatePdf(fromDate, toDate);
        String filename = "VAT_" + fromDate + "_to_" + toDate + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/vat/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> vatExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] excel = vatReportService.generateExcel(fromDate, toDate);
        String filename = "VAT_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ======================== Daily Sales Register (auditor) ========================

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private ResponseEntity<byte[]> excelResponse(byte[] excel, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/daily-register-diesel/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> dieselRegisterPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return pdfResponse(dailySalesRegisterService.generateFuelPdf(DailySalesRegisterService.DIESEL, fromDate, toDate),
                "DieselRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-diesel/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> dieselRegisterExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return excelResponse(dailySalesRegisterService.generateFuelExcel(DailySalesRegisterService.DIESEL, fromDate, toDate),
                "DieselRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-petrol/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> petrolRegisterPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return pdfResponse(dailySalesRegisterService.generateFuelPdf(DailySalesRegisterService.PETROL, fromDate, toDate),
                "PetrolRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-petrol/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> petrolRegisterExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return excelResponse(dailySalesRegisterService.generateFuelExcel(DailySalesRegisterService.PETROL, fromDate, toDate),
                "PetrolRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-xtra-premium/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> xtraPremiumRegisterPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return pdfResponse(dailySalesRegisterService.generateFuelPdf(DailySalesRegisterService.XTRA_PREMIUM, fromDate, toDate),
                "XtraPremiumRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-xtra-premium/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> xtraPremiumRegisterExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return excelResponse(dailySalesRegisterService.generateFuelExcel(DailySalesRegisterService.XTRA_PREMIUM, fromDate, toDate),
                "XtraPremiumRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-lubricants/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> lubricantRegisterPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return pdfResponse(dailySalesRegisterService.generateLubricantPdf(fromDate, toDate),
                "LubricantRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-lubricants/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> lubricantRegisterExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return excelResponse(dailySalesRegisterService.generateLubricantExcel(fromDate, toDate),
                "LubricantRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-purchase/pdf")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> purchaseRegisterPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return pdfResponse(dailySalesRegisterService.generatePurchasePdf(fromDate, toDate),
                "PurchaseRegister_" + fromDate + "_to_" + toDate);
    }

    @GetMapping("/daily-register-purchase/excel")
    @PreAuthorize("hasPermission(null, 'REPORT_VIEW')")
    public ResponseEntity<byte[]> purchaseRegisterExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return excelResponse(dailySalesRegisterService.generatePurchaseExcel(fromDate, toDate),
                "PurchaseRegister_" + fromDate + "_to_" + toDate);
    }
}
