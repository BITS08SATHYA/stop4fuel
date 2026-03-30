package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.SalaryHistory;
import com.stopforfuel.backend.repository.DesignationRepository;
import com.stopforfuel.backend.repository.OperationalAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.SalaryHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryHistoryRepository salaryHistoryRepository;

    @Mock
    private OperationalAdvanceRepository operationalAdvanceRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private DesignationRepository designationRepository;

    @Mock
    private RolesRepository rolesRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setName("Ravi Kumar");
        testEmployee.setPhone("9876543210");
        testEmployee.setEmployeeCode("EMP001");
        testEmployee.setSalary(25000.0);
        testEmployee.setStatus("Active");
    }

    // --- getAllEmployees ---

    @Test
    void getAllEmployees_returnsList() {
        Employee emp2 = new Employee();
        emp2.setId(2L);
        emp2.setName("Suresh");
        when(employeeRepository.findAll()).thenReturn(List.of(testEmployee, emp2));

        List<Employee> result = employeeService.getAllEmployees();

        assertEquals(2, result.size());
        verify(employeeRepository).findAll();
    }

    // --- getEmployeeById ---

    @Test
    void getEmployeeById_exists_returnsOptional() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        Optional<Employee> result = employeeService.getEmployeeById(1L);

        assertTrue(result.isPresent());
        assertEquals("Ravi Kumar", result.get().getName());
    }

    @Test
    void getEmployeeById_notFound_returnsEmpty() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Employee> result = employeeService.getEmployeeById(99L);

        assertTrue(result.isEmpty());
    }

    // --- getActiveEmployees ---

    @Test
    void getActiveEmployees_returnsList() {
        when(employeeRepository.findByStatus("Active")).thenReturn(List.of(testEmployee));

        List<Employee> result = employeeService.getActiveEmployees();

        assertEquals(1, result.size());
        assertEquals("Active", result.get(0).getStatus());
        verify(employeeRepository).findByStatus("Active");
    }

    // --- createEmployee ---

    @Test
    void createEmployee_saves() {
        Roles empRole = new Roles();
        empRole.setId(1L);
        empRole.setRoleType("EMPLOYEE");
        when(rolesRepository.findByRoleType("EMPLOYEE")).thenReturn(Optional.of(empRole));
        when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);

        Employee result = employeeService.createEmployee(testEmployee);

        assertNotNull(result);
        assertEquals("Ravi Kumar", result.getName());
        verify(employeeRepository).save(testEmployee);
    }

    // --- updateEmployee ---

    @Test
    void updateEmployee_exists_updatesFields() {
        Employee details = new Employee();
        details.setName("Ravi Kumar Updated");
        details.setPhone("9999999999");
        details.setEmployeeCode("EMP002");
        details.setDesignation("Manager");
        details.setEmail("ravi@test.com");
        details.setSalary(30000.0);
        details.setStatus("Active");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(designationRepository.findByName("Manager")).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        Employee result = employeeService.updateEmployee(1L, details);

        assertEquals("Ravi Kumar Updated", result.getName());
        assertEquals("9999999999", result.getPhone());
        assertEquals("EMP002", result.getEmployeeCode());
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void updateEmployee_notFound_throwsException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> employeeService.updateEmployee(99L, new Employee()));
        assertTrue(ex.getMessage().contains("Employee not found"));
    }

    // --- deleteEmployee ---

    @Test
    void deleteEmployee_callsDeleteById() {
        employeeService.deleteEmployee(1L);

        verify(employeeRepository).deleteById(1L);
    }

    // --- getSalaryHistory ---

    @Test
    void getSalaryHistory_returnsList() {
        SalaryHistory h1 = new SalaryHistory();
        h1.setOldSalary(20000.0);
        h1.setNewSalary(25000.0);
        SalaryHistory h2 = new SalaryHistory();
        h2.setOldSalary(25000.0);
        h2.setNewSalary(30000.0);

        when(salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(1L))
                .thenReturn(List.of(h2, h1));

        List<SalaryHistory> result = employeeService.getSalaryHistory(1L);

        assertEquals(2, result.size());
        verify(salaryHistoryRepository).findByEmployeeIdOrderByEffectiveDateDesc(1L);
    }

    // --- addSalaryRevision ---

    @Test
    void addSalaryRevision_updatesEmployeeSalary() {
        SalaryHistory history = new SalaryHistory();
        history.setEmployee(testEmployee);
        history.setNewSalary(35000.0);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(salaryHistoryRepository.save(any(SalaryHistory.class))).thenAnswer(i -> i.getArgument(0));

        SalaryHistory result = employeeService.addSalaryRevision(history);

        assertEquals(25000.0, result.getOldSalary());
        assertEquals(35000.0, result.getNewSalary());
        assertEquals(35000.0, testEmployee.getSalary());
        verify(employeeRepository).save(testEmployee);
        verify(salaryHistoryRepository).save(history);
    }

    @Test
    void addSalaryRevision_employeeNotFound_throws() {
        Employee ref = new Employee();
        ref.setId(99L);
        SalaryHistory history = new SalaryHistory();
        history.setEmployee(ref);

        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> employeeService.addSalaryRevision(history));
        assertTrue(ex.getMessage().contains("Employee not found"));
    }

    // --- getAdvances ---

    @Test
    void getAdvances_returnsList() {
        OperationalAdvance a1 = new OperationalAdvance();
        a1.setAmount(new BigDecimal("5000"));
        OperationalAdvance a2 = new OperationalAdvance();
        a2.setAmount(new BigDecimal("3000"));

        when(operationalAdvanceRepository.findByEmployeeIdOrderByAdvanceDateDesc(1L))
                .thenReturn(List.of(a1, a2));

        List<OperationalAdvance> result = employeeService.getAdvances(1L);

        assertEquals(2, result.size());
        verify(operationalAdvanceRepository).findByEmployeeIdOrderByAdvanceDateDesc(1L);
    }

    // --- getPendingAdvances ---

    @Test
    void getPendingAdvances_returnsPendingOnly() {
        OperationalAdvance pending = new OperationalAdvance();
        pending.setStatus("PENDING");
        pending.setAmount(new BigDecimal("5000"));

        when(operationalAdvanceRepository.findByEmployeeIdAndStatus(1L, "PENDING"))
                .thenReturn(List.of(pending));

        List<OperationalAdvance> result = employeeService.getPendingAdvances(1L);

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(operationalAdvanceRepository).findByEmployeeIdAndStatus(1L, "PENDING");
    }

    // --- addAdvance ---

    @Test
    void addAdvance_saves() {
        OperationalAdvance advance = new OperationalAdvance();
        advance.setAmount(new BigDecimal("5000"));
        advance.setStatus("PENDING");

        when(operationalAdvanceRepository.save(any(OperationalAdvance.class))).thenReturn(advance);

        OperationalAdvance result = employeeService.addAdvance(advance);

        assertNotNull(result);
        assertEquals(new BigDecimal("5000"), result.getAmount());
        verify(operationalAdvanceRepository).save(advance);
    }

    // --- updateAdvanceStatus ---

    @Test
    void updateAdvanceStatus_updatesStatus() {
        OperationalAdvance advance = new OperationalAdvance();
        advance.setId(1L);
        advance.setStatus("PENDING");

        when(operationalAdvanceRepository.findById(1L)).thenReturn(Optional.of(advance));
        when(operationalAdvanceRepository.save(any(OperationalAdvance.class))).thenAnswer(i -> i.getArgument(0));

        OperationalAdvance result = employeeService.updateAdvanceStatus(1L, "DEDUCTED");

        assertEquals("DEDUCTED", result.getStatus());
        verify(operationalAdvanceRepository).save(advance);
    }

    @Test
    void updateAdvanceStatus_notFound_throws() {
        when(operationalAdvanceRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> employeeService.updateAdvanceStatus(99L, "DEDUCTED"));
        assertTrue(ex.getMessage().contains("Advance not found"));
    }
}
