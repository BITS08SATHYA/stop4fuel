package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class LeaveService {

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    // Leave Types
    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType createLeaveType(LeaveType leaveType) {
        return leaveTypeRepository.save(leaveType);
    }

    public LeaveType updateLeaveType(Long id, LeaveType details) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leave type not found"));
        leaveType.setTypeName(details.getTypeName());
        leaveType.setMaxDaysPerYear(details.getMaxDaysPerYear());
        leaveType.setCarryForward(details.getCarryForward());
        leaveType.setMaxCarryForwardDays(details.getMaxCarryForwardDays());
        return leaveTypeRepository.save(leaveType);
    }

    public void deleteLeaveType(Long id) {
        leaveTypeRepository.deleteById(id);
    }

    // Leave Balances
    public List<LeaveBalance> getEmployeeLeaveBalances(Long employeeId, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year);
    }

    @Transactional
    public List<LeaveBalance> initializeLeaveBalances(Long employeeId, Integer year) {
        Employee employee = employeeRepository.findByIdAndScid(employeeId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<LeaveType> leaveTypes = leaveTypeRepository.findAll();
        for (LeaveType lt : leaveTypes) {
            leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, lt.getId(), year)
                    .orElseGet(() -> {
                        LeaveBalance balance = new LeaveBalance();
                        balance.setEmployee(employee);
                        balance.setLeaveType(lt);
                        balance.setYear(year);
                        balance.setTotalAllotted(lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear().doubleValue() : 0.0);
                        balance.setUsed(0.0);
                        balance.setRemaining(lt.getMaxDaysPerYear() != null ? lt.getMaxDaysPerYear().doubleValue() : 0.0);
                        return leaveBalanceRepository.save(balance);
                    });
        }
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year);
    }

    // Leave Requests
    public List<LeaveRequest> getEmployeeLeaveRequests(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<LeaveRequest> getLeaveRequestsByStatus(String status) {
        Long scid = SecurityUtils.getScid();
        if (status == null || status.isEmpty()) {
            return leaveRequestRepository.findAllByScidOrderByCreatedAtDesc(scid);
        }
        return leaveRequestRepository.findByScidAndStatusOrderByCreatedAtDesc(scid, status);
    }

    @Transactional
    public LeaveRequest createLeaveRequest(Long employeeId, LeaveRequest request) {
        Employee employee = employeeRepository.findByIdAndScid(employeeId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        request.setEmployee(employee);
        request.setStatus("PENDING");
        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveRequest approveLeaveRequest(Long requestId, String approvedBy, String remarks) {
        LeaveRequest request = leaveRequestRepository.findByIdAndScid(requestId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        request.setStatus("APPROVED");
        request.setApprovedBy(approvedBy);
        request.setRemarks(remarks);

        // Update leave balance
        int year = request.getFromDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(request.getEmployee().getId(), request.getLeaveType().getId(), year)
                .orElse(null);

        if (balance != null) {
            balance.setUsed(balance.getUsed() + request.getNumberOfDays());
            balance.setRemaining(balance.getTotalAllotted() - balance.getUsed());
            leaveBalanceRepository.save(balance);
        }

        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveRequest rejectLeaveRequest(Long requestId, String remarks) {
        LeaveRequest request = leaveRequestRepository.findByIdAndScid(requestId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        request.setStatus("REJECTED");
        request.setRemarks(remarks);
        return leaveRequestRepository.save(request);
    }
}
