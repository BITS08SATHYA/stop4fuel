package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.ExpenseType;
import com.stopforfuel.backend.entity.StationExpense;
import com.stopforfuel.backend.repository.StationExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationExpenseServiceTest {

    @Mock
    private StationExpenseRepository stationExpenseRepository;

    @InjectMocks
    private StationExpenseService stationExpenseService;

    private StationExpense testExpense;
    private ExpenseType testExpenseType;

    @BeforeEach
    void setUp() {
        testExpenseType = new ExpenseType();
        testExpenseType.setId(1L);
        testExpenseType.setTypeName("Maintenance");

        testExpense = new StationExpense();
        testExpense.setId(1L);
        testExpense.setExpenseType(testExpenseType);
        testExpense.setAmount(500.0);
        testExpense.setExpenseDate(LocalDate.now());
        testExpense.setDescription("AC repair");
        testExpense.setPaidTo("Technician");
        testExpense.setPaymentMode("CASH");
        testExpense.setRecurringType("ONE_TIME");
    }

    @Test
    void getAllExpenses_returnsList() {
        when(stationExpenseRepository.findAllByOrderByExpenseDateDesc()).thenReturn(List.of(testExpense));

        List<StationExpense> result = stationExpenseService.getAllExpenses();

        assertEquals(1, result.size());
        verify(stationExpenseRepository).findAllByOrderByExpenseDateDesc();
    }

    @Test
    void getExpensesBetween_returnsList() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        when(stationExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to))
                .thenReturn(List.of(testExpense));

        List<StationExpense> result = stationExpenseService.getExpensesBetween(from, to);

        assertEquals(1, result.size());
        verify(stationExpenseRepository).findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
    }

    @Test
    void createExpense_saves() {
        when(stationExpenseRepository.save(any(StationExpense.class))).thenAnswer(i -> i.getArgument(0));

        StationExpense result = stationExpenseService.createExpense(testExpense);

        assertEquals(500.0, result.getAmount());
        verify(stationExpenseRepository).save(testExpense);
    }

    @Test
    void updateExpense_updatesFields() {
        StationExpense details = new StationExpense();
        ExpenseType newType = new ExpenseType();
        newType.setId(2L);
        newType.setTypeName("Utilities");
        details.setExpenseType(newType);
        details.setAmount(800.0);
        details.setExpenseDate(LocalDate.now());
        details.setDescription("Electricity bill");
        details.setPaidTo("EB Office");
        details.setPaymentMode("UPI");
        details.setRecurringType("MONTHLY");

        when(stationExpenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(stationExpenseRepository.save(any(StationExpense.class))).thenAnswer(i -> i.getArgument(0));

        StationExpense result = stationExpenseService.updateExpense(1L, details);

        assertEquals(800.0, result.getAmount());
        assertEquals("Electricity bill", result.getDescription());
        assertEquals("UPI", result.getPaymentMode());
        assertEquals("MONTHLY", result.getRecurringType());
    }

    @Test
    void updateExpense_notFound_throws() {
        when(stationExpenseRepository.findById(99L)).thenReturn(Optional.empty());

        StationExpense details = new StationExpense();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stationExpenseService.updateExpense(99L, details));
        assertTrue(ex.getMessage().contains("Expense not found"));
    }

    @Test
    void deleteExpense_callsDeleteById() {
        stationExpenseService.deleteExpense(1L);

        verify(stationExpenseRepository).deleteById(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getExpenseSummary_calculatesCorrectly() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        ExpenseType type2 = new ExpenseType();
        type2.setId(2L);
        type2.setTypeName("Utilities");

        StationExpense expense2 = new StationExpense();
        expense2.setId(2L);
        expense2.setExpenseType(type2);
        expense2.setAmount(300.0);
        expense2.setExpenseDate(LocalDate.now());

        when(stationExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to))
                .thenReturn(List.of(testExpense, expense2));
        when(stationExpenseRepository.sumAmountBetween(from, to)).thenReturn(800.0);

        Map<String, Object> summary = stationExpenseService.getExpenseSummary(from, to);

        assertEquals(800.0, summary.get("totalAmount"));
        assertEquals(2, summary.get("count"));

        Map<String, Double> byCategory = (Map<String, Double>) summary.get("byCategory");
        assertEquals(500.0, byCategory.get("Maintenance"));
        assertEquals(300.0, byCategory.get("Utilities"));
    }
}
