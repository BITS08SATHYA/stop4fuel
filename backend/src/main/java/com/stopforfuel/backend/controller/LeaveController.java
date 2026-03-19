package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.LeaveBalance;
import com.stopforfuel.backend.entity.LeaveRequest;
import com.stopforfuel.backend.entity.LeaveType;
import com.stopforfuel.backend.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public List<LeaveType> getAllLeaveTypes() {
        return leaveService.getAllLeaveTypes();
    }

    @PostMapping("/leave-types")
    public LeaveType createLeaveType(@Valid @RequestBody LeaveType leaveType) {
        return leaveService.createLeaveType(leaveType);
    }

    @PutMapping("/leave-types/{id}")
    public ResponseEntity<LeaveType> updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveType leaveType) {
        try {
            return ResponseEntity.ok(leaveService.updateLeaveType(id, leaveType));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/leave-types/{id}")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        leaveService.deleteLeaveType(id);
        return ResponseEntity.ok().build();
    }

    // Leave Balances
    @GetMapping("/employees/{employeeId}/leave-balances")
    public List<LeaveBalance> getLeaveBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int yr = (year != null) ? year : LocalDate.now().getYear();
        return leaveService.getEmployeeLeaveBalances(employeeId, yr);
    }

    @PostMapping("/employees/{employeeId}/leave-balances/initialize")
    public List<LeaveBalance> initializeLeaveBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int yr = (year != null) ? year : LocalDate.now().getYear();
        return leaveService.initializeLeaveBalances(employeeId, yr);
    }

    // Leave Requests
    @PostMapping("/employees/{employeeId}/leave-requests")
    public LeaveRequest createLeaveRequest(@PathVariable Long employeeId, @Valid @RequestBody LeaveRequest request) {
        return leaveService.createLeaveRequest(employeeId, request);
    }

    @GetMapping("/employees/{employeeId}/leave-requests")
    public List<LeaveRequest> getEmployeeLeaveRequests(@PathVariable Long employeeId) {
        return leaveService.getEmployeeLeaveRequests(employeeId);
    }

    @GetMapping("/leave-requests")
    public List<LeaveRequest> getLeaveRequests(@RequestParam(required = false) String status) {
        return leaveService.getLeaveRequestsByStatus(status);
    }

    @PatchMapping("/leave-requests/{id}/approve")
    public ResponseEntity<LeaveRequest> approveLeaveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String approvedBy = body != null ? body.get("approvedBy") : null;
            String remarks = body != null ? body.get("remarks") : null;
            return ResponseEntity.ok(leaveService.approveLeaveRequest(id, approvedBy, remarks));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/leave-requests/{id}/reject")
    public ResponseEntity<LeaveRequest> rejectLeaveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String remarks = body != null ? body.get("remarks") : null;
            return ResponseEntity.ok(leaveService.rejectLeaveRequest(id, remarks));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
