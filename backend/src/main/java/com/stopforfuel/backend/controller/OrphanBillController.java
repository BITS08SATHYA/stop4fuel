package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.AutoFixResultDTO;
import com.stopforfuel.backend.dto.BulkAutoFixResultDTO;
import com.stopforfuel.backend.dto.OrphanBillDTO;
import com.stopforfuel.backend.service.OrphanBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin recovery endpoints for the orphan-bill data anomaly. See OrphanBillService for the
 * business logic; root cause + scope captured in memory `project_orphan_bills_bug`.
 */
@RestController
@RequestMapping("/api/admin/orphan-bills")
@RequiredArgsConstructor
public class OrphanBillController {

    private final OrphanBillService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public List<OrphanBillDTO> list(
            @RequestParam(name = "includeHistorical", defaultValue = "false") boolean includeHistorical) {
        return service.listOrphans(includeHistorical);
    }

    /**
     * Lightweight sidebar-badge endpoint: just the count, no row payload.
     */
    @GetMapping("/count")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public Map<String, Long> count(
            @RequestParam(name = "includeHistorical", defaultValue = "false") boolean includeHistorical) {
        return Map.of("count", service.countOrphans(includeHistorical));
    }

    @PostMapping("/{billId}/auto-fix")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public AutoFixResultDTO autoFix(@PathVariable Long billId) {
        return service.autoFix(billId);
    }

    @PostMapping("/auto-fix-all")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public BulkAutoFixResultDTO autoFixAll(
            @RequestParam(name = "includeHistorical", defaultValue = "false") boolean includeHistorical) {
        return service.autoFixAll(includeHistorical);
    }
}
