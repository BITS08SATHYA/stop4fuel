package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.ShiftCashInvoiceAutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * One-shot admin endpoint to backfill synthetic cash invoices for historic
 * CLOSED/RECONCILED shifts. From today forward, the shift-close flow handles it.
 */
@RestController
@RequestMapping("/api/admin/cash-invoice")
@RequiredArgsConstructor
public class ShiftCashInvoiceAdminController {

    private final ShiftCashInvoiceAutoService autoService;

    @PostMapping("/auto-generate")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> autoGenerate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(autoService.backfillRange(from, to));
    }
}
