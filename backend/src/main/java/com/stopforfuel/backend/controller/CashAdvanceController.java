package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.service.CashAdvanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advances")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CashAdvanceController {

    private final CashAdvanceService cashAdvanceService;

    @GetMapping
    public List<CashAdvance> getAll() {
        return cashAdvanceService.getAll();
    }

    @GetMapping("/status/{status}")
    public List<CashAdvance> getByStatus(@PathVariable String status) {
        return cashAdvanceService.getByStatus(status);
    }

    @GetMapping("/shift/{shiftId}")
    public List<CashAdvance> getByShift(@PathVariable Long shiftId) {
        return cashAdvanceService.getByShift(shiftId);
    }

    @PostMapping
    public CashAdvance create(@RequestBody CashAdvance advance) {
        return cashAdvanceService.create(advance);
    }

    @PostMapping("/{id}/return")
    public CashAdvance recordReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal returnedAmount = new BigDecimal(body.get("returnedAmount").toString());
        String returnRemarks = body.get("returnRemarks") != null ? body.get("returnRemarks").toString() : null;
        return cashAdvanceService.recordReturn(id, returnedAmount, returnRemarks);
    }

    @PatchMapping("/{id}/cancel")
    public CashAdvance cancel(@PathVariable Long id) {
        return cashAdvanceService.cancel(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cashAdvanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
