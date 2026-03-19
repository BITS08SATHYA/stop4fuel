package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.UtilityBill;
import com.stopforfuel.backend.repository.UtilityBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilityBillServiceTest {

    @Mock
    private UtilityBillRepository utilityBillRepository;

    @InjectMocks
    private UtilityBillService utilityBillService;

    private UtilityBill testBill;

    @BeforeEach
    void setUp() {
        testBill = new UtilityBill();
        testBill.setId(1L);
        testBill.setBillType("ELECTRICITY");
        testBill.setProvider("TNEB");
        testBill.setConsumerNumber("1234567890");
        testBill.setBillDate(LocalDate.now());
        testBill.setDueDate(LocalDate.now().plusDays(15));
        testBill.setBillAmount(5000.0);
        testBill.setPaidAmount(0.0);
        testBill.setStatus("PENDING");
        testBill.setUnitsConsumed(350.0);
        testBill.setBillPeriod("Mar 2026");
        testBill.setRemarks("Monthly bill");
    }

    @Test
    void getAllBills_returnsList() {
        when(utilityBillRepository.findAllByOrderByBillDateDesc()).thenReturn(List.of(testBill));

        List<UtilityBill> result = utilityBillService.getAllBills();

        assertEquals(1, result.size());
        assertEquals("ELECTRICITY", result.get(0).getBillType());
        verify(utilityBillRepository).findAllByOrderByBillDateDesc();
    }

    @Test
    void getBillsByType_returnsList() {
        when(utilityBillRepository.findByBillTypeOrderByBillDateDesc("ELECTRICITY"))
                .thenReturn(List.of(testBill));

        List<UtilityBill> result = utilityBillService.getBillsByType("ELECTRICITY");

        assertEquals(1, result.size());
        verify(utilityBillRepository).findByBillTypeOrderByBillDateDesc("ELECTRICITY");
    }

    @Test
    void getPendingBills_returnsPendingOnly() {
        when(utilityBillRepository.findByStatusOrderByDueDateAsc("PENDING"))
                .thenReturn(List.of(testBill));

        List<UtilityBill> result = utilityBillService.getPendingBills();

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(utilityBillRepository).findByStatusOrderByDueDateAsc("PENDING");
    }

    @Test
    void createBill_saves() {
        when(utilityBillRepository.save(any(UtilityBill.class))).thenAnswer(i -> i.getArgument(0));

        UtilityBill result = utilityBillService.createBill(testBill);

        assertEquals("TNEB", result.getProvider());
        verify(utilityBillRepository).save(testBill);
    }

    @Test
    void updateBill_updatesFields() {
        UtilityBill details = new UtilityBill();
        details.setBillType("WATER");
        details.setProvider("Metro Water");
        details.setConsumerNumber("9876543210");
        details.setBillDate(LocalDate.now());
        details.setDueDate(LocalDate.now().plusDays(30));
        details.setBillAmount(1500.0);
        details.setPaidAmount(1500.0);
        details.setStatus("PAID");
        details.setUnitsConsumed(100.0);
        details.setBillPeriod("Feb 2026");
        details.setRemarks("Paid in full");

        when(utilityBillRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(utilityBillRepository.save(any(UtilityBill.class))).thenAnswer(i -> i.getArgument(0));

        UtilityBill result = utilityBillService.updateBill(1L, details);

        assertEquals("WATER", result.getBillType());
        assertEquals("Metro Water", result.getProvider());
        assertEquals("PAID", result.getStatus());
        assertEquals(1500.0, result.getPaidAmount());
    }

    @Test
    void updateBill_notFound_throws() {
        when(utilityBillRepository.findById(99L)).thenReturn(Optional.empty());

        UtilityBill details = new UtilityBill();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> utilityBillService.updateBill(99L, details));
        assertTrue(ex.getMessage().contains("Bill not found"));
    }

    @Test
    void deleteBill_callsDeleteById() {
        utilityBillService.deleteBill(1L);

        verify(utilityBillRepository).deleteById(1L);
    }
}
