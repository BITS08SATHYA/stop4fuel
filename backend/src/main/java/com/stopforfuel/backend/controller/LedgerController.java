package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.service.LedgerPdfGenerator;
import com.stopforfuel.backend.service.LedgerService;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerPdfGenerator ledgerPdfGenerator;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;

    /**
     * Get opening balance for a customer at a given date.
     * GET /api/ledger/opening-balance?customerId=1&asOfDate=2025-07-01
     */
    @GetMapping("/opening-balance")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<BigDecimal> getOpeningBalance(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(ledgerService.getOpeningBalance(customerId, asOfDate));
    }

    /**
     * Get full customer ledger between dates.
     * GET /api/ledger/customer/{customerId}?fromDate=2025-06-01&toDate=2025-06-30
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<LedgerService.CustomerLedger> getCustomerLedger(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ledgerService.getCustomerLedger(customerId, fromDate, toDate));
    }

    /**
     * Get outstanding (unpaid) credit bills for a customer.
     * GET /api/ledger/outstanding/{customerId}
     */
    @GetMapping("/outstanding/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<InvoiceBill> getOutstandingBills(@PathVariable Long customerId) {
        return ledgerService.getOutstandingBills(customerId);
    }

    /**
     * Download customer ledger as PDF.
     * GET /api/ledger/customer/{customerId}/pdf?fromDate=2025-04-01&toDate=2026-03-31
     */
    @GetMapping("/customer/{customerId}/pdf")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public ResponseEntity<byte[]> downloadLedgerPdf(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LedgerService.CustomerLedger ledger = ledgerService.getCustomerLedger(customerId, fromDate, toDate);
        Customer customer = customerRepository.findById(customerId).orElse(null);
        List<Company> companies = companyRepository.findByScid(SecurityUtils.getScid());
        Company company = !companies.isEmpty() ? companies.get(0) : null;

        byte[] pdfBytes = ledgerPdfGenerator.generate(ledger, customer, company);

        String customerName = customer != null ? customer.getName().replaceAll("[^a-zA-Z0-9]", "_") : "Customer";
        String filename = "Ledger_" + customerName + "_" + fromDate + "_to_" + toDate + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
