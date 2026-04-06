package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.PaytmReconSummaryDTO;
import com.stopforfuel.backend.dto.PaytmSyncResultDTO;
import com.stopforfuel.backend.dto.PaytmTransactionDTO;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.PaytmTransaction;
import com.stopforfuel.backend.enums.ReconStatus;
import com.stopforfuel.backend.service.PaytmService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paytm")
@RequiredArgsConstructor
public class PaytmController {

    private final PaytmService paytmService;

    // --- Sync ---

    @PostMapping("/sync")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ResponseEntity<PaytmSyncResultDTO> syncTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(paytmService.syncTransactions(date));
    }

    @PostMapping("/sync/range")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ResponseEntity<PaytmSyncResultDTO> syncRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(paytmService.syncTransactionRange(fromDate, toDate));
    }

    // --- Reconciliation ---

    @PostMapping("/reconcile")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ResponseEntity<Map<String, Object>> runReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        int matched = paytmService.runReconciliation(fromDate, toDate);
        return ResponseEntity.ok(Map.of("matched", matched));
    }

    @GetMapping("/recon-summary")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<PaytmReconSummaryDTO> getReconSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(paytmService.getReconSummary(fromDate, toDate));
    }

    // --- Transactions ---

    @GetMapping("/transactions")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<List<PaytmTransactionDTO>> getTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String reconStatus) {

        List<PaytmTransaction> txns;
        if (reconStatus != null && !reconStatus.isEmpty()) {
            txns = paytmService.getByReconStatus(ReconStatus.valueOf(reconStatus));
        } else {
            txns = paytmService.getByDateRange(fromDate, toDate);
        }
        return ResponseEntity.ok(txns.stream().map(PaytmTransactionDTO::from).toList());
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<PaytmTransactionDTO> getTransaction(@PathVariable Long id) {
        return paytmService.getById(id)
                .map(t -> ResponseEntity.ok(PaytmTransactionDTO.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transactions/{orderId}/status")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<Map<String, Object>> getTransactionStatus(@PathVariable String orderId) {
        return ResponseEntity.ok(paytmService.fetchTransactionStatus(orderId));
    }

    @GetMapping("/unmatched-invoices")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> getUnmatchedInvoices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<InvoiceBill> invoices = paytmService.getUnmatchedInvoices(fromDate, toDate);
        List<Map<String, Object>> result = invoices.stream().map(i -> Map.<String, Object>of(
                "id", i.getId(),
                "billNo", i.getBillNo() != null ? i.getBillNo() : "",
                "netAmount", i.getNetAmount(),
                "date", i.getDate().toString(),
                "customerName", i.getCustomer() != null ? i.getCustomer().getName() : "Walk-in"
        )).toList();
        return ResponseEntity.ok(result);
    }

    // --- Manual Reconciliation ---

    @PostMapping("/transactions/{id}/match")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ResponseEntity<PaytmTransactionDTO> manualMatch(
            @PathVariable Long id, @RequestParam Long invoiceBillId) {
        PaytmTransaction txn = paytmService.manualMatch(id, invoiceBillId);
        return ResponseEntity.ok(PaytmTransactionDTO.from(txn));
    }

    @PostMapping("/transactions/{id}/ignore")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ResponseEntity<PaytmTransactionDTO> ignoreTransaction(
            @PathVariable Long id, @RequestParam String reason) {
        PaytmTransaction txn = paytmService.ignoreTransaction(id, reason);
        return ResponseEntity.ok(PaytmTransactionDTO.from(txn));
    }

    @PostMapping("/transactions/{id}/dispute")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
    public ResponseEntity<PaytmTransactionDTO> disputeTransaction(
            @PathVariable Long id, @RequestParam String reason) {
        PaytmTransaction txn = paytmService.disputeTransaction(id, reason);
        return ResponseEntity.ok(PaytmTransactionDTO.from(txn));
    }

    // --- Settlements ---

    @PostMapping("/settlements/sync")
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ResponseEntity<Map<String, Object>> syncSettlements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int updated = paytmService.syncSettlements(date);
        return ResponseEntity.ok(Map.of("updated", updated, "date", date.toString()));
    }
}
