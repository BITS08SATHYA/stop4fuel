package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Expense;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.ExpenseRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository repository;
    private final ShiftService shiftService;

    public List<Expense> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public Expense getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
    }

    public List<Expense> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByExpenseDateDesc(shiftId);
    }

    @Transactional
    public Expense create(Expense expense) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            expense.setShiftId(activeShift.getId());
        }
        return repository.save(expense);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public BigDecimal sumByShift(Long shiftId) {
        return repository.sumByShift(shiftId);
    }

    public List<Expense> getByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        return repository.findByDateRange(SecurityUtils.getScid(), from, to);
    }
}
