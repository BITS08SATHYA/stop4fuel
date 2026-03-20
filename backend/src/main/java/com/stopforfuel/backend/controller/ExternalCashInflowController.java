package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.service.ExternalCashInflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-inflows")
@RequiredArgsConstructor
public class ExternalCashInflowController {

    private final ExternalCashInflowService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExternalCashInflow> getAll() {
        return service.getAll();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExternalCashInflow> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExternalCashInflow> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ExternalCashInflow create(@Valid @RequestBody ExternalCashInflow inflow) {
        return service.create(inflow);
    }

    @PostMapping("/{id}/repay")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public CashInflowRepayment recordRepayment(@PathVariable Long id,
                                                @Valid @RequestBody CashInflowRepayment repayment) {
        return service.recordRepayment(id, repayment);
    }

    @GetMapping("/{id}/repayments")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<CashInflowRepayment> getRepayments(@PathVariable Long id) {
        return service.getRepayments(id);
    }
}
