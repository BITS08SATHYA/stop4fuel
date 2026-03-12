package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.repository.ShiftTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShiftTransactionService {

    private final ShiftTransactionRepository repository;

    public List<ShiftTransaction> getByShift(Long shiftId) {
        return repository.findByShiftId(shiftId);
    }

    @Transactional
    public ShiftTransaction create(ShiftTransaction transaction) {
        if (transaction.getScid() == null) {
            transaction.setScid(1L);
        }
        return repository.save(transaction);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Map<String, Object> getShiftSummary(Long shiftId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("cash", repository.sumCashByShift(shiftId));
        summary.put("upi", repository.sumUpiByShift(shiftId));
        summary.put("card", repository.sumCardByShift(shiftId));
        summary.put("expense", repository.sumExpenseByShift(shiftId));
        summary.put("total", repository.sumAllByShift(shiftId));

        BigDecimal total = repository.sumAllByShift(shiftId);
        BigDecimal expense = repository.sumExpenseByShift(shiftId);
        summary.put("net", total.subtract(expense));

        return summary;
    }
}
