package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.service.ShiftTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-transactions")
@RequiredArgsConstructor
public class ShiftTransactionController {

    private final ShiftTransactionService service;

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ShiftTransaction> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/shift/{shiftId}/summary")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public Map<String, Object> getShiftSummary(@PathVariable Long shiftId) {
        return service.getShiftSummary(shiftId);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ShiftTransaction create(@Valid @RequestBody ShiftTransaction transaction) {
        return service.create(transaction);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
