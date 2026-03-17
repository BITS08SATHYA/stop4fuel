package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.transaction.CashTransaction;
import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.repository.ShiftTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftTransactionServiceTest {

    @Mock
    private ShiftTransactionRepository repository;

    @InjectMocks
    private ShiftTransactionService shiftTransactionService;

    @Test
    void getByShift_returnsList() {
        CashTransaction txn = new CashTransaction();
        txn.setReceivedAmount(new BigDecimal("500"));
        when(repository.findByShiftId(1L)).thenReturn(List.of(txn));

        List<ShiftTransaction> result = shiftTransactionService.getByShift(1L);
        assertEquals(1, result.size());
    }

    @Test
    void create_setsDefaultScid() {
        CashTransaction txn = new CashTransaction();
        txn.setReceivedAmount(new BigDecimal("1000"));
        when(repository.save(any(ShiftTransaction.class))).thenAnswer(i -> i.getArgument(0));

        ShiftTransaction result = shiftTransactionService.create(txn);
        assertEquals(1L, result.getScid());
    }

    @Test
    void create_preservesExistingScid() {
        CashTransaction txn = new CashTransaction();
        txn.setScid(2L);
        when(repository.save(any(ShiftTransaction.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(2L, shiftTransactionService.create(txn).getScid());
    }

    @Test
    void delete_callsRepository() {
        shiftTransactionService.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void getShiftSummary_calculatesCorrectly() {
        when(repository.sumCashByShift(1L)).thenReturn(new BigDecimal("5000"));
        when(repository.sumUpiByShift(1L)).thenReturn(new BigDecimal("3000"));
        when(repository.sumCardByShift(1L)).thenReturn(new BigDecimal("2000"));
        when(repository.sumExpenseByShift(1L)).thenReturn(new BigDecimal("1000"));
        when(repository.sumAllByShift(1L)).thenReturn(new BigDecimal("10000"));

        Map<String, Object> summary = shiftTransactionService.getShiftSummary(1L);

        assertEquals(new BigDecimal("5000"), summary.get("cash"));
        assertEquals(new BigDecimal("3000"), summary.get("upi"));
        assertEquals(new BigDecimal("2000"), summary.get("card"));
        assertEquals(new BigDecimal("1000"), summary.get("expense"));
        assertEquals(new BigDecimal("10000"), summary.get("total"));
        assertEquals(new BigDecimal("9000"), summary.get("net"));
    }
}
