package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.StationExpenseDTO;
import com.stopforfuel.backend.entity.StationExpense;
import com.stopforfuel.backend.service.StationExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/station-expenses")
public class StationExpenseController {

    @Autowired
    private StationExpenseService stationExpenseService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_VIEW')")
    public List<StationExpenseDTO> getAllExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<StationExpense> result;
        if (from != null && to != null) {
            result = stationExpenseService.getExpensesBetween(from, to);
        } else {
            result = stationExpenseService.getAllExpenses();
        }
        return result.stream().map(StationExpenseDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public StationExpenseDTO createExpense(@Valid @RequestBody StationExpense expense) {
        return StationExpenseDTO.from(stationExpenseService.createExpense(expense));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public ResponseEntity<StationExpenseDTO> updateExpense(@PathVariable Long id, @Valid @RequestBody StationExpense expense) {
        try {
            return ResponseEntity.ok(StationExpenseDTO.from(stationExpenseService.updateExpense(id, expense)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        stationExpenseService.deleteExpense(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasPermission(null, 'FINANCE_VIEW')")
    public Map<String, Object> getExpenseSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stationExpenseService.getExpenseSummary(from, to);
    }
}
