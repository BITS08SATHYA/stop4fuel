package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.service.CreditMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit/monitoring")
@RequiredArgsConstructor
public class CreditMonitoringController {

    private final CreditMonitoringService creditMonitoringService;

    @GetMapping("/watchlist")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<CreditMonitoringService.CreditHealth> getWatchlist() {
        return creditMonitoringService.getWatchlist();
    }

    @GetMapping("/health/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public CreditMonitoringService.CreditHealth getCreditHealth(@PathVariable Long customerId) {
        return creditMonitoringService.computeCreditHealth(customerId);
    }

    @GetMapping("/reconciliation/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public CreditMonitoringService.ReconciliationSummary getReconciliation(@PathVariable Long customerId) {
        return creditMonitoringService.getReconciliationSummary(customerId);
    }

    @PostMapping("/scan")
    @PreAuthorize("hasPermission(null, 'CUSTOMER_UPDATE')")
    public ResponseEntity<Map<String, Object>> triggerManualScan() {
        int blocked = creditMonitoringService.runManualScan();
        return ResponseEntity.ok(Map.of("blockedCount", blocked));
    }

    @GetMapping("/block-history/{customerId}")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public List<CreditMonitoringService.BlockEventDTO> getBlockHistory(@PathVariable Long customerId) {
        return creditMonitoringService.getBlockHistory(customerId);
    }
}
