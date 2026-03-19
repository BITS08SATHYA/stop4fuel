package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.service.ExternalCashInflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-inflows")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExternalCashInflowController {

    private final ExternalCashInflowService service;

    @GetMapping
    public List<ExternalCashInflow> getAll() {
        return service.getAll();
    }

    @GetMapping("/shift/{shiftId}")
    public List<ExternalCashInflow> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/status/{status}")
    public List<ExternalCashInflow> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    @PostMapping
    public ExternalCashInflow create(@Valid @RequestBody ExternalCashInflow inflow) {
        return service.create(inflow);
    }

    @PostMapping("/{id}/repay")
    public CashInflowRepayment recordRepayment(@PathVariable Long id,
                                                @Valid @RequestBody CashInflowRepayment repayment) {
        return service.recordRepayment(id, repayment);
    }

    @GetMapping("/{id}/repayments")
    public List<CashInflowRepayment> getRepayments(@PathVariable Long id) {
        return service.getRepayments(id);
    }
}
