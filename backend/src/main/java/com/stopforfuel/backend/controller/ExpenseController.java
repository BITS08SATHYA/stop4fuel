package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ExpenseDTO;
import com.stopforfuel.backend.entity.Expense;
import com.stopforfuel.backend.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExpenseDTO> getAll() {
        return service.getAll().stream().map(ExpenseDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public ExpenseDTO getById(@PathVariable Long id) {
        return ExpenseDTO.from(service.getById(id));
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExpenseDTO> getByShift(@PathVariable Long shiftId) {
        return service.getByShift(shiftId).stream().map(ExpenseDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}/total")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public BigDecimal getShiftTotal(@PathVariable Long shiftId) {
        return service.sumByShift(shiftId);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'SHIFT_VIEW')")
    public List<ExpenseDTO> searchByDateRange(
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate) {
        return service.getByDateRange(fromDate, toDate).stream().map(ExpenseDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ExpenseDTO create(@Valid @RequestBody Expense expense) {
        return ExpenseDTO.from(service.create(expense));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
