package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.LeaveBalance;
import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.entity.LeaveType;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.LeaveBalanceRepository;
import com.stopforfuel.backend.repository.LeaveRequestRepository;
import com.stopforfuel.backend.repository.LeaveTypeRepository;
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
class LeaveServiceTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LeaveService leaveService;

    private Employee testEmployee;
    private LeaveType casualLeave;
    private LeaveType sickLeave;
    private LeaveBalance testBalance;
    private LeaveRequest testRequest;

    @BeforeEach
    void setUp() {
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setName("Test Employee");

        casualLeave = new LeaveType();
        casualLeave.setId(1L);
        casualLeave.setTypeName("Casual Leave");
        casualLeave.setMaxDaysPerYear(12);
        casualLeave.setCarryForward(false);
        casualLeave.setMaxCarryForwardDays(0);

        sickLeave = new LeaveType();
        sickLeave.setId(2L);
        sickLeave.setTypeName("Sick Leave");
        sickLeave.setMaxDaysPerYear(10);
        sickLeave.setCarryForward(true);
        sickLeave.setMaxCarryForwardDays(5);

        testBalance = new LeaveBalance();
        testBalance.setId(1L);
        testBalance.setEmployee(testEmployee);
        testBalance.setLeaveType(casualLeave);
        testBalance.setYear(2026);
        testBalance.setTotalAllotted(12.0);
        testBalance.setUsed(2.0);
        testBalance.setRemaining(10.0);

        testRequest = new LeaveRequest();
        testRequest.setId(1L);
        testRequest.setEmployee(testEmployee);
        testRequest.setLeaveType(casualLeave);
        testRequest.setFromDate(LocalDate.of(2026, 3, 20));
        testRequest.setToDate(LocalDate.of(2026, 3, 22));
        testRequest.setNumberOfDays(3.0);
        testRequest.setReason("Personal work");
        testRequest.setStatus("PENDING");
    }

    // --- Leave Types ---

    @Test
    void getAllLeaveTypes_returnsList() {
        when(leaveTypeRepository.findAll()).thenReturn(List.of(casualLeave, sickLeave));

        List<LeaveType> result = leaveService.getAllLeaveTypes();

        assertEquals(2, result.size());
        assertEquals("Casual Leave", result.get(0).getTypeName());
        verify(leaveTypeRepository).findAll();
    }

    @Test
    void createLeaveType_saves() {
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(casualLeave);

        LeaveType result = leaveService.createLeaveType(casualLeave);

        assertEquals("Casual Leave", result.getTypeName());
        assertEquals(12, result.getMaxDaysPerYear());
        verify(leaveTypeRepository).save(casualLeave);
    }

    @Test
    void updateLeaveType_updatesFields() {
        LeaveType updated = new LeaveType();
        updated.setTypeName("Updated Leave");
        updated.setMaxDaysPerYear(15);
        updated.setCarryForward(true);
        updated.setMaxCarryForwardDays(3);

        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(casualLeave));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        LeaveType result = leaveService.updateLeaveType(1L, updated);

        assertEquals("Updated Leave", result.getTypeName());
        assertEquals(15, result.getMaxDaysPerYear());
        assertTrue(result.getCarryForward());
        assertEquals(3, result.getMaxCarryForwardDays());
    }

    @Test
    void updateLeaveType_notFound_throws() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> leaveService.updateLeaveType(99L, new LeaveType()));
        assertTrue(ex.getMessage().contains("Leave type not found"));
    }

    @Test
    void deleteLeaveType_callsDeleteById() {
        doNothing().when(leaveTypeRepository).deleteById(1L);

        leaveService.deleteLeaveType(1L);

        verify(leaveTypeRepository).deleteById(1L);
    }

    // --- Leave Balances ---

    @Test
    void getEmployeeLeaveBalances_returnsList() {
        when(leaveBalanceRepository.findByEmployeeIdAndYear(1L, 2026))
                .thenReturn(List.of(testBalance));

        List<LeaveBalance> result = leaveService.getEmployeeLeaveBalances(1L, 2026);

        assertEquals(1, result.size());
        assertEquals(12.0, result.get(0).getTotalAllotted());
        verify(leaveBalanceRepository).findByEmployeeIdAndYear(1L, 2026);
    }

    @Test
    void initializeLeaveBalances_createsBalancesForAllTypes() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(leaveTypeRepository.findAll()).thenReturn(List.of(casualLeave, sickLeave));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(1L), anyLong(), eq(2026)))
                .thenReturn(Optional.empty());
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(i -> i.getArgument(0));
        when(leaveBalanceRepository.findByEmployeeIdAndYear(1L, 2026))
                .thenReturn(List.of(testBalance, new LeaveBalance()));

        List<LeaveBalance> result = leaveService.initializeLeaveBalances(1L, 2026);

        assertEquals(2, result.size());
        verify(leaveBalanceRepository, times(2)).save(any(LeaveBalance.class));
    }

    @Test
    void initializeLeaveBalances_employeeNotFound_throws() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> leaveService.initializeLeaveBalances(99L, 2026));
        assertTrue(ex.getMessage().contains("Employee not found"));
    }

    // --- Leave Requests ---

    @Test
    void getEmployeeLeaveRequests_returnsList() {
        when(leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(testRequest));

        List<LeaveRequest> result = leaveService.getEmployeeLeaveRequests(1L);

        assertEquals(1, result.size());
        assertEquals("Personal work", result.get(0).getReason());
        verify(leaveRequestRepository).findByEmployeeIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getLeaveRequestsByStatus_withStatus_filtersCorrectly() {
        when(leaveRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING"))
                .thenReturn(List.of(testRequest));

        List<LeaveRequest> result = leaveService.getLeaveRequestsByStatus("PENDING");

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(leaveRequestRepository).findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Test
    void getLeaveRequestsByStatus_nullStatus_returnsAll() {
        when(leaveRequestRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(testRequest));

        List<LeaveRequest> result = leaveService.getLeaveRequestsByStatus(null);

        assertEquals(1, result.size());
        verify(leaveRequestRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void createLeaveRequest_setsPendingStatus() {
        LeaveRequest newRequest = new LeaveRequest();
        newRequest.setLeaveType(casualLeave);
        newRequest.setFromDate(LocalDate.of(2026, 4, 1));
        newRequest.setToDate(LocalDate.of(2026, 4, 3));
        newRequest.setNumberOfDays(3.0);
        newRequest.setReason("Vacation");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.createLeaveRequest(1L, newRequest);

        assertEquals("PENDING", result.getStatus());
        assertEquals(testEmployee, result.getEmployee());
        verify(leaveRequestRepository).save(newRequest);
    }

    @Test
    void createLeaveRequest_employeeNotFound_throws() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> leaveService.createLeaveRequest(99L, new LeaveRequest()));
        assertTrue(ex.getMessage().contains("Employee not found"));
    }

    @Test
    void approveLeaveRequest_updatesStatusAndBalance() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 1L, 2026))
                .thenReturn(Optional.of(testBalance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(i -> i.getArgument(0));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.approveLeaveRequest(1L, "Admin", "Approved");

        assertEquals("APPROVED", result.getStatus());
        assertEquals("Admin", result.getApprovedBy());
        assertEquals("Approved", result.getRemarks());
        assertEquals(5.0, testBalance.getUsed()); // was 2.0 + 3.0 days
        assertEquals(7.0, testBalance.getRemaining()); // 12.0 - 5.0
        verify(leaveBalanceRepository).save(testBalance);
    }

    @Test
    void approveLeaveRequest_notFound_throws() {
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> leaveService.approveLeaveRequest(99L, "Admin", "Approved"));
        assertTrue(ex.getMessage().contains("Leave request not found"));
    }

    @Test
    void rejectLeaveRequest_setsRejectedStatus() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(testRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.rejectLeaveRequest(1L, "Insufficient balance");

        assertEquals("REJECTED", result.getStatus());
        assertEquals("Insufficient balance", result.getRemarks());
        verify(leaveRequestRepository).save(testRequest);
    }

    @Test
    void rejectLeaveRequest_notFound_throws() {
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> leaveService.rejectLeaveRequest(99L, "Rejected"));
        assertTrue(ex.getMessage().contains("Leave request not found"));
    }
}
