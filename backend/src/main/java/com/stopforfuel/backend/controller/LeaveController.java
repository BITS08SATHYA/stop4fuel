package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.LeaveRequestDTO;
import com.stopforfuel.backend.entity.LeaveBalance;
import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.entity.LeaveType;
import com.stopforfuel.backend.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    // Leave Types
    @GetMapping("/leave-types")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<LeaveType> getAllLeaveTypes() {
        return leaveService.getAllLeaveTypes();
    }

    @PostMapping("/leave-types")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public LeaveType createLeaveType(@Valid @RequestBody LeaveType leaveType) {
        return leaveService.createLeaveType(leaveType);
    }

    @PutMapping("/leave-types/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<LeaveType> updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveType leaveType) {
        try {
            return ResponseEntity.ok(leaveService.updateLeaveType(id, leaveType));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/leave-types/{id}")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        leaveService.deleteLeaveType(id);
        return ResponseEntity.ok().build();
    }

    // Leave Balances
    @GetMapping("/employees/{employeeId}/leave-balances")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<LeaveBalance> getLeaveBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int yr = (year != null) ? year : LocalDate.now().getYear();
        return leaveService.getEmployeeLeaveBalances(employeeId, yr);
    }

    @PostMapping("/employees/{employeeId}/leave-balances/initialize")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public List<LeaveBalance> initializeLeaveBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int yr = (year != null) ? year : LocalDate.now().getYear();
        return leaveService.initializeLeaveBalances(employeeId, yr);
    }

    // Leave Requests
    @PostMapping("/employees/{employeeId}/leave-requests")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public LeaveRequestDTO createLeaveRequest(@PathVariable Long employeeId, @Valid @RequestBody LeaveRequest request) {
        return LeaveRequestDTO.from(leaveService.createLeaveRequest(employeeId, request));
    }

    @GetMapping("/employees/{employeeId}/leave-requests")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<LeaveRequestDTO> getEmployeeLeaveRequests(@PathVariable Long employeeId) {
        return leaveService.getEmployeeLeaveRequests(employeeId).stream().map(LeaveRequestDTO::from).toList();
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_VIEW')")
    public List<LeaveRequestDTO> getLeaveRequests(@RequestParam(required = false) String status) {
        return leaveService.getLeaveRequestsByStatus(status).stream().map(LeaveRequestDTO::from).toList();
    }

    @PatchMapping("/leave-requests/{id}/approve")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<LeaveRequestDTO> approveLeaveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String approvedBy = body != null ? body.get("approvedBy") : null;
            String remarks = body != null ? body.get("remarks") : null;
            return ResponseEntity.ok(LeaveRequestDTO.from(leaveService.approveLeaveRequest(id, approvedBy, remarks)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasPermission(null, 'EMPLOYEE_MANAGE')")
    public ResponseEntity<LeaveRequestDTO> rejectLeaveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String remarks = body != null ? body.get("remarks") : null;
            return ResponseEntity.ok(LeaveRequestDTO.from(leaveService.rejectLeaveRequest(id, remarks)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
