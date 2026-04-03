package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.ExpenseType;
import com.stopforfuel.backend.repository.ExpenseTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expense-types")
@RequiredArgsConstructor
public class ExpenseTypeController {

    private final ExpenseTypeRepository repository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_VIEW')")
    public List<ExpenseType> getAll() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public ExpenseType create(@Valid @RequestBody ExpenseType expenseType) {
        return repository.save(expenseType);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public ResponseEntity<ExpenseType> update(@PathVariable Long id, @Valid @RequestBody ExpenseType updated) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setTypeName(updated.getTypeName());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
