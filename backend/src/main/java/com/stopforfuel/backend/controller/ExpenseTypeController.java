package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.ExpenseType;
import com.stopforfuel.backend.repository.ExpenseTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expense-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExpenseTypeController {

    private final ExpenseTypeRepository repository;

    @GetMapping
    public List<ExpenseType> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ExpenseType create(@RequestBody ExpenseType expenseType) {
        return repository.save(expenseType);
    }
}
