package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.CashInflowRepaymentRepository;
import com.stopforfuel.backend.repository.ExternalCashInflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalCashInflowServiceTest {

    @Mock
    private ExternalCashInflowRepository inflowRepository;

    @Mock
    private CashInflowRepaymentRepository repaymentRepository;

    @Mock
    private ShiftService shiftService;

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExternalCashInflowService externalCashInflowService;

    private ExternalCashInflow testInflow;
    private Shift activeShift;

    @BeforeEach
    void setUp() {
        testInflow = new ExternalCashInflow();
        testInflow.setId(1L);
        testInflow.setAmount(new BigDecimal("1000"));
        testInflow.setInflowDate(LocalDateTime.now());
        testInflow.setSource("Owner");
        testInflow.setPurpose("Petty cash");
        testInflow.setStatus("ACTIVE");
        testInflow.setRepaidAmount(BigDecimal.ZERO);
        testInflow.setScid(1L);

        activeShift = new Shift();
        activeShift.setId(10L);
    }

    @Test
    void getAll_returnsList() {
        when(inflowRepository.findAllByOrderByInflowDateDesc()).thenReturn(List.of(testInflow));

        List<ExternalCashInflow> result = externalCashInflowService.getAll();

        assertEquals(1, result.size());
        verify(inflowRepository).findAllByOrderByInflowDateDesc();
    }

    @Test
    void getByShift_returnsList() {
        when(inflowRepository.findByShiftIdOrderByInflowDateDesc(10L)).thenReturn(List.of(testInflow));

        List<ExternalCashInflow> result = externalCashInflowService.getByShift(10L);

        assertEquals(1, result.size());
        verify(inflowRepository).findByShiftIdOrderByInflowDateDesc(10L);
    }

    @Test
    void getByStatus_returnsList() {
        when(inflowRepository.findByStatusOrderByInflowDateDesc("ACTIVE")).thenReturn(List.of(testInflow));

        List<ExternalCashInflow> result = externalCashInflowService.getByStatus("ACTIVE");

        assertEquals(1, result.size());
        verify(inflowRepository).findByStatusOrderByInflowDateDesc("ACTIVE");
    }

    @Test
    void create_withActiveShift_setsShiftIdAndCreatesCashTransaction() {
        when(shiftService.getActiveShift()).thenReturn(activeShift);
        when(inflowRepository.save(any(ExternalCashInflow.class))).thenAnswer(i -> {
            ExternalCashInflow saved = i.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ExternalCashInflow result = externalCashInflowService.create(testInflow);

        assertEquals(10L, result.getShiftId());
    }

    @Test
    void create_withoutActiveShift_savesWithoutTransaction() {
        when(shiftService.getActiveShift()).thenReturn(null);
        when(inflowRepository.save(any(ExternalCashInflow.class))).thenAnswer(i -> i.getArgument(0));

        ExternalCashInflow result = externalCashInflowService.create(testInflow);

        assertNotNull(result);
    }

    @Test
    void recordRepayment_partialRepayment_setsPartiallyRepaid() {
        testInflow.setAmount(new BigDecimal("1000"));
        testInflow.setRepaidAmount(BigDecimal.ZERO);

        CashInflowRepayment repayment = new CashInflowRepayment();
        repayment.setAmount(new BigDecimal("500"));

        when(inflowRepository.findById(1L)).thenReturn(Optional.of(testInflow));
        when(shiftService.getActiveShift()).thenReturn(null);
        when(repaymentRepository.save(any(CashInflowRepayment.class))).thenAnswer(i -> {
            CashInflowRepayment saved = i.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(inflowRepository.save(any(ExternalCashInflow.class))).thenAnswer(i -> i.getArgument(0));

        externalCashInflowService.recordRepayment(1L, repayment);

        assertEquals("PARTIALLY_REPAID", testInflow.getStatus());
        assertEquals(new BigDecimal("500"), testInflow.getRepaidAmount());
    }

    @Test
    void recordRepayment_fullRepayment_setsFullyRepaid() {
        testInflow.setAmount(new BigDecimal("1000"));
        testInflow.setRepaidAmount(new BigDecimal("500"));

        CashInflowRepayment repayment = new CashInflowRepayment();
        repayment.setAmount(new BigDecimal("500"));

        when(inflowRepository.findById(1L)).thenReturn(Optional.of(testInflow));
        when(shiftService.getActiveShift()).thenReturn(null);
        when(repaymentRepository.save(any(CashInflowRepayment.class))).thenAnswer(i -> {
            CashInflowRepayment saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(inflowRepository.save(any(ExternalCashInflow.class))).thenAnswer(i -> i.getArgument(0));

        externalCashInflowService.recordRepayment(1L, repayment);

        assertEquals("FULLY_REPAID", testInflow.getStatus());
        assertEquals(new BigDecimal("1000"), testInflow.getRepaidAmount());
    }

    @Test
    void recordRepayment_inflowNotFound_throws() {
        when(inflowRepository.findById(99L)).thenReturn(Optional.empty());

        CashInflowRepayment repayment = new CashInflowRepayment();
        repayment.setAmount(new BigDecimal("100"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> externalCashInflowService.recordRepayment(99L, repayment));
        assertTrue(ex.getMessage().contains("External cash inflow not found"));
    }

    @Test
    void getRepayments_returnsList() {
        CashInflowRepayment repayment = new CashInflowRepayment();
        repayment.setId(1L);
        repayment.setAmount(new BigDecimal("500"));

        when(repaymentRepository.findByCashInflowIdOrderByRepaymentDateDesc(1L)).thenReturn(List.of(repayment));

        List<CashInflowRepayment> result = externalCashInflowService.getRepayments(1L);

        assertEquals(1, result.size());
        verify(repaymentRepository).findByCashInflowIdOrderByRepaymentDateDesc(1L);
    }
}
