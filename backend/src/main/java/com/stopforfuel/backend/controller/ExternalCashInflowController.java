package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ExternalCashInflowDTO;
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
    public List<ExternalCashInflowDTO> getAll() {
        return service.getAll().stream().map(ExternalCashInflowDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExternalCashInflowDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(ExternalCashInflowDTO::from).toList();
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExternalCashInflowDTO> getByStatus(@PathVariable String status) {
        return service.getByStatus(status).stream().map(ExternalCashInflowDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_CREATE')")
    public ExternalCashInflowDTO create(@Valid @RequestBody ExternalCashInflow inflow) {
        return ExternalCashInflowDTO.from(service.create(inflow));
    }

    @PostMapping("/{id}/repay")
    @PreAuthorize("hasPermission(null, 'SHIFT_UPDATE')")
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
