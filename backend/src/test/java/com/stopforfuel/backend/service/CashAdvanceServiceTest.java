package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.transaction.ShiftTransaction;
import com.stopforfuel.backend.repository.CashAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashAdvanceServiceTest {

    @Mock
    private CashAdvanceRepository repository;

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ShiftService shiftService;

    @Mock
    private ShiftTransactionService shiftTransactionService;

    @InjectMocks
    private CashAdvanceService cashAdvanceService;

    private CashAdvance testAdvance;
    private Shift activeShift;

    @BeforeEach
    void setUp() {
        activeShift = new Shift();
        activeShift.setId(1L);

        testAdvance = new CashAdvance();
        testAdvance.setId(1L);
        testAdvance.setAmount(new BigDecimal("5000"));
        testAdvance.setAdvanceType("REGULAR_ADVANCE");
        testAdvance.setRecipientName("Raj");
        testAdvance.setStatus("GIVEN");
        testAdvance.setScid(1L);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findAllByOrderByAdvanceDateDesc()).thenReturn(List.of(testAdvance));
        assertEquals(1, cashAdvanceService.getAll().size());
    }

    @Test
    void getByStatus_returnsList() {
        when(repository.findByStatusOrderByAdvanceDateDesc("GIVEN"))
                .thenReturn(List.of(testAdvance));
        assertEquals(1, cashAdvanceService.getByStatus("GIVEN").size());
    }

    @Test
    void getByShift_returnsList() {
        when(repository.findByShiftIdOrderByAdvanceDateDesc(1L))
                .thenReturn(List.of(testAdvance));
        assertEquals(1, cashAdvanceService.getByShift(1L).size());
    }

    @Test
    void create_withActiveShift_setsShiftAndCreatesExpenseTransaction() {
        when(shiftService.getActiveShift()).thenReturn(activeShift);
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> {
            CashAdvance a = i.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(shiftTransactionService.create(any(ShiftTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        CashAdvance result = cashAdvanceService.create(testAdvance);

        assertEquals(1L, result.getShiftId());
        verify(shiftTransactionService).create(any(ShiftTransaction.class));
    }

    @Test
    void create_withoutActiveShift_doesNotCreateTransaction() {
        when(shiftService.getActiveShift()).thenReturn(null);
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> i.getArgument(0));

        cashAdvanceService.create(testAdvance);

        verify(shiftTransactionService, never()).create(any());
    }

    @Test
    void recordReturn_fullReturn_setsStatusReturned() {
        when(repository.findById(1L)).thenReturn(Optional.of(testAdvance));
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftService.getActiveShift()).thenReturn(activeShift);
        when(shiftTransactionService.create(any(ShiftTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        CashAdvance result = cashAdvanceService.recordReturn(
                1L, new BigDecimal("5000"), "Full return");

        assertEquals("RETURNED", result.getStatus());
        assertEquals(new BigDecimal("5000"), result.getReturnedAmount());
        assertNotNull(result.getReturnDate());
        verify(shiftTransactionService).create(any(ShiftTransaction.class));
    }

    @Test
    void recordReturn_partialReturn_setsStatusPartiallyReturned() {
        when(repository.findById(1L)).thenReturn(Optional.of(testAdvance));
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftService.getActiveShift()).thenReturn(activeShift);
        when(shiftTransactionService.create(any(ShiftTransaction.class)))
                .thenAnswer(i -> i.getArgument(0));

        CashAdvance result = cashAdvanceService.recordReturn(
                1L, new BigDecimal("3000"), "Partial return");

        assertEquals("PARTIALLY_RETURNED", result.getStatus());
    }

    @Test
    void recordReturn_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> cashAdvanceService.recordReturn(99L, BigDecimal.ZERO, ""));
    }

    @Test
    void recordReturn_zeroAmount_noTransaction() {
        when(repository.findById(1L)).thenReturn(Optional.of(testAdvance));
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftService.getActiveShift()).thenReturn(activeShift);

        cashAdvanceService.recordReturn(1L, BigDecimal.ZERO, "No return");

        verify(shiftTransactionService, never()).create(any());
    }

    @Test
    void cancel_setsStatusCancelled() {
        when(repository.findById(1L)).thenReturn(Optional.of(testAdvance));
        when(repository.save(any(CashAdvance.class))).thenAnswer(i -> i.getArgument(0));

        CashAdvance result = cashAdvanceService.cancel(1L);
        assertEquals("CANCELLED", result.getStatus());
    }

    @Test
    void cancel_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> cashAdvanceService.cancel(99L));
    }

    @Test
    void delete_callsRepository() {
        when(invoiceBillRepository.findByCashAdvanceId(1L)).thenReturn(Collections.emptyList());
        cashAdvanceService.delete(1L);
        verify(repository).deleteById(1L);
    }
}
