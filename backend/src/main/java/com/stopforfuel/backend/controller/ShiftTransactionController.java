package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.service.ShiftTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ShiftTransactionController {

    private final ShiftTransactionService service;

    @GetMapping("/shift/{shiftId}")
    public List<ShiftTransaction> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId);
    }

    @GetMapping("/shift/{shiftId}/summary")
    public Map<String, Object> getShiftSummary(@PathVariable Long shiftId) {
        return service.getShiftSummary(shiftId);
    }

    @PostMapping
    public ShiftTransaction create(@RequestBody ShiftTransaction transaction) {
        return service.create(transaction);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
