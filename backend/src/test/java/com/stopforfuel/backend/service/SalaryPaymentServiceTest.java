package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.SalaryPayment;
import com.stopforfuel.backend.repository.OperationalAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.SalaryPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaryPaymentServiceTest {

    @Mock
    private SalaryPaymentRepository salaryPaymentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private OperationalAdvanceRepository operationalAdvanceRepository;

    @InjectMocks
    private SalaryPaymentService salaryPaymentService;

    private Employee emp1;
    private Employee emp2;
    private SalaryPayment testPayment;
    private OperationalAdvance pendingAdvance;

    @BeforeEach
    void setUp() {
        emp1 = new Employee();
        emp1.setId(1L);
        emp1.setName("Employee One");
        emp1.setSalary(20000.0);
        emp1.setStatus("Active");

        emp2 = new Employee();
        emp2.setId(2L);
        emp2.setName("Employee Two");
        emp2.setSalary(25000.0);
        emp2.setStatus("Active");

        testPayment = new SalaryPayment();
        testPayment.setId(1L);
        testPayment.setEmployee(emp1);
        testPayment.setMonth(3);
        testPayment.setYear(2026);
        testPayment.setBaseSalary(20000.0);
        testPayment.setAdvanceDeduction(500.0);
        testPayment.setIncentiveAmount(0.0);
        testPayment.setOtherDeductions(0.0);
        testPayment.setNetPayable(19500.0);
        testPayment.setStatus("DRAFT");

        pendingAdvance = new OperationalAdvance();
        pendingAdvance.setId(1L);
        pendingAdvance.setEmployee(emp1);
        pendingAdvance.setAmount(new BigDecimal("500"));
        pendingAdvance.setAdvanceDate(LocalDate.of(2026, 3, 10).atStartOfDay());
        pendingAdvance.setAdvanceType("SALARY");
        pendingAdvance.setStatus("PENDING");
    }

    @Test
    void getMonthlyPayments_returnsList() {
        when(salaryPaymentRepository.findByMonthAndYear(3, 2026))
                .thenReturn(List.of(testPayment));

        List<SalaryPayment> result = salaryPaymentService.getMonthlyPayments(3, 2026);

        assertEquals(1, result.size());
        assertEquals(20000.0, result.get(0).getBaseSalary());
        verify(salaryPaymentRepository).findByMonthAndYear(3, 2026);
    }

    @Test
    void getEmployeePayments_returnsList() {
        when(salaryPaymentRepository.findByEmployeeIdOrderByYearDescMonthDesc(1L))
                .thenReturn(List.of(testPayment));

        List<SalaryPayment> result = salaryPaymentService.getEmployeePayments(1L);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getMonth());
        verify(salaryPaymentRepository).findByEmployeeIdOrderByYearDescMonthDesc(1L);
    }

    @Test
    void processMonthlyPayroll_createsPaymentsForActiveEmployees() {
        when(employeeRepository.findByStatus("Active")).thenReturn(List.of(emp1, emp2));

        // emp1 not yet processed, has pending advance of 500.0
        when(salaryPaymentRepository.findByEmployeeIdAndMonthAndYear(1L, 3, 2026))
                .thenReturn(Optional.empty());
        when(operationalAdvanceRepository.findByEmployeeIdAndStatus(1L, "PENDING"))
                .thenReturn(List.of(pendingAdvance));

        // emp2 not yet processed, no pending advances
        when(salaryPaymentRepository.findByEmployeeIdAndMonthAndYear(2L, 3, 2026))
                .thenReturn(Optional.empty());
        when(operationalAdvanceRepository.findByEmployeeIdAndStatus(2L, "PENDING"))
                .thenReturn(List.of());

        when(salaryPaymentRepository.save(any(SalaryPayment.class))).thenAnswer(i -> i.getArgument(0));

        List<SalaryPayment> result = salaryPaymentService.processMonthlyPayroll(3, 2026);

        assertEquals(2, result.size());

        // emp1: salary 20000 - advance 500 = 19500
        SalaryPayment payment1 = result.get(0);
        assertEquals(20000.0, payment1.getBaseSalary());
        assertEquals(500.0, payment1.getAdvanceDeduction());
        assertEquals(19500.0, payment1.getNetPayable());
        assertEquals("DRAFT", payment1.getStatus());

        // emp2: salary 25000 - no advance = 25000
        SalaryPayment payment2 = result.get(1);
        assertEquals(25000.0, payment2.getBaseSalary());
        assertEquals(0.0, payment2.getAdvanceDeduction());
        assertEquals(25000.0, payment2.getNetPayable());
        assertEquals("DRAFT", payment2.getStatus());

        verify(salaryPaymentRepository, times(2)).save(any(SalaryPayment.class));
    }

    @Test
    void processMonthlyPayroll_skipsAlreadyProcessed() {
        when(employeeRepository.findByStatus("Active")).thenReturn(List.of(emp1));
        when(salaryPaymentRepository.findByEmployeeIdAndMonthAndYear(1L, 3, 2026))
                .thenReturn(Optional.of(testPayment));

        List<SalaryPayment> result = salaryPaymentService.processMonthlyPayroll(3, 2026);

        assertEquals(1, result.size());
        assertEquals(testPayment, result.get(0));
        verify(salaryPaymentRepository, never()).save(any(SalaryPayment.class));
    }

    @Test
    void markAsPaid_setsStatusAndDeductsAdvances() {
        when(salaryPaymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(operationalAdvanceRepository.findByEmployeeIdAndStatus(1L, "PENDING"))
                .thenReturn(List.of(pendingAdvance));
        when(operationalAdvanceRepository.save(any(OperationalAdvance.class))).thenAnswer(i -> i.getArgument(0));
        when(salaryPaymentRepository.save(any(SalaryPayment.class))).thenAnswer(i -> i.getArgument(0));

        SalaryPayment result = salaryPaymentService.markAsPaid(1L, "BANK_TRANSFER");

        assertEquals("PAID", result.getStatus());
        assertNotNull(result.getPaymentDate());
        assertEquals("BANK_TRANSFER", result.getPaymentMode());
        assertEquals("DEDUCTED", pendingAdvance.getStatus());
        verify(operationalAdvanceRepository).save(pendingAdvance);
        verify(salaryPaymentRepository).save(testPayment);
    }

    @Test
    void markAsPaid_notFound_throws() {
        when(salaryPaymentRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> salaryPaymentService.markAsPaid(99L, "CASH"));
        assertTrue(ex.getMessage().contains("Payment not found"));
    }

    @Test
    void updatePayment_recalculatesNetPayable() {
        SalaryPayment details = new SalaryPayment();
        details.setBaseSalary(22000.0);
        details.setAdvanceDeduction(1000.0);
        details.setIncentiveAmount(500.0);
        details.setOtherDeductions(200.0);
        details.setRemarks("Updated salary");

        when(salaryPaymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(salaryPaymentRepository.save(any(SalaryPayment.class))).thenAnswer(i -> i.getArgument(0));

        SalaryPayment result = salaryPaymentService.updatePayment(1L, details);

        assertEquals(22000.0, result.getBaseSalary());
        assertEquals(1000.0, result.getAdvanceDeduction());
        assertEquals(500.0, result.getIncentiveAmount());
        assertEquals(200.0, result.getOtherDeductions());
        // net = 22000 - 1000 + 500 - 200 = 21300
        assertEquals(21300.0, result.getNetPayable());
        assertEquals("Updated salary", result.getRemarks());
    }

    @Test
    void updatePayment_notFound_throws() {
        when(salaryPaymentRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> salaryPaymentService.updatePayment(99L, new SalaryPayment()));
        assertTrue(ex.getMessage().contains("Payment not found"));
    }
}
