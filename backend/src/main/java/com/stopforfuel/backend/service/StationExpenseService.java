package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.StationExpense;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.StationExpenseRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StationExpenseService {

    private final StationExpenseRepository stationExpenseRepository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<StationExpense> getAllExpenses() {
        return stationExpenseRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<StationExpense> getExpensesBetween(LocalDate from, LocalDate to) {
        return stationExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
    }

    @Transactional
    public StationExpense createExpense(StationExpense expense) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Open a shift before recording expenses.");
        }
        expense.setShiftId(activeShift.getId());
        return stationExpenseRepository.save(expense);
    }

    public StationExpense updateExpense(Long id, StationExpense details) {
        StationExpense expense = stationExpenseRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        expense.setExpenseType(details.getExpenseType());
        expense.setAmount(details.getAmount());
        expense.setExpenseDate(details.getExpenseDate());
        expense.setDescription(details.getDescription());
        expense.setPaidTo(details.getPaidTo());
        expense.setPaymentMode(details.getPaymentMode());
        expense.setRecurringType(details.getRecurringType());

        return stationExpenseRepository.save(expense);
    }

    public void deleteExpense(Long id) {
        stationExpenseRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<StationExpense> getByShift(Long shiftId) {
        return stationExpenseRepository.findByShiftIdOrderByIdDesc(shiftId);
    }

    @Transactional(readOnly = true)
    public Double sumByShift(Long shiftId) {
        return stationExpenseRepository.sumAmountByShift(shiftId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getExpenseSummary(LocalDate from, LocalDate to) {
        List<StationExpense> expenses = stationExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
        Double total = stationExpenseRepository.sumAmountBetween(from, to);

        Map<String, Double> byCategory = new HashMap<>();
        for (StationExpense e : expenses) {
            String category = e.getExpenseType() != null ? e.getExpenseType().getTypeName() : "Uncategorized";
            byCategory.merge(category, e.getAmount(), Double::sum);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAmount", total);
        summary.put("count", expenses.size());
        summary.put("byCategory", byCategory);
        summary.put("from", from.toString());
        summary.put("to", to.toString());

        return summary;
    }
}
