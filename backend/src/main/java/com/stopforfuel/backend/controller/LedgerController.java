package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
}
